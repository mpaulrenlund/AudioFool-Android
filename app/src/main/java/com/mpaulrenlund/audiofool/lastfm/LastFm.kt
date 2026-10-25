package com.mpaulrenlund.audiofool.lastfm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmApi
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmException
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmScrobbler
import com.mpaulrenlund.audiofool.core.scrobbling.LastFmSession
import com.mpaulrenlund.audiofool.core.scrobbling.ScrobbleQueue
import com.mpaulrenlund.audiofool.core.scrobbling.ScrobblerState
import com.mpaulrenlund.audiofool.playback.PlayWatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Last.fm (spec 7) as Settings shows it, and the scrobbler fed from playback.
 * Ported from the desktop's `LastFmViewModel.cs` (the connect flow, the queue
 * line) around the Core's [LastFmScrobbler].
 *
 * Connecting is Last.fm's desktop flow: the user types the API key and secret
 * once (the desktop's), a token is fetched, last.fm opens in the browser to
 * approve it, and this polls auth.getSession until the approval lands, so there
 * is no "I've approved it" button to forget, and the password is only ever typed
 * into last.fm itself.
 *
 * Main thread only.
 */
@Stable
class LastFm(private val context: Context, folder: File) : PlayWatcher {
    private val scope: CoroutineScope = MainScope()
    private val store = LastFmStore(context)
    private lateinit var scrobbler: LastFmScrobbler

    /** What Settings draws. */
    var status: Status by mutableStateOf(Status())
        private set

    class Status(
        val state: ScrobblerState = ScrobblerState.Disconnected,
        val userName: String? = null,
        /** Connect was tapped: the key and secret fields are showing. */
        val enteringKeys: Boolean = false,
        /** The browser is open on last.fm's approval page. */
        val waitingForApproval: Boolean = false,
        val error: String? = null,
        /** The queue and the last send, in a line ("2 scrobbles waiting to send."). */
        val queue: String = "",
        /** The saved key and secret, to fill the fields when connecting again. */
        val savedKey: String = "",
        val savedSecret: String = "",
    )

    private var connectJob: Job? = null
    private var authorizeUrl: String? = null

    init {
        scope.launch {
            val (queue, saved) = withContext(Dispatchers.IO) {
                ScrobbleQueue.load(File(folder, "scrobbles.json").path, System.currentTimeMillis()) to store.read()
            }
            scrobbler = LastFmScrobbler(queue, scope)
            scrobbler.onStatusChanged = ::refresh
            scrobbler.onAuthFailed = { message -> status = copy(error = message) }
            if (saved?.sessionKey != null) scrobbler.connect(saved.apiKey, saved.secret, LastFmSession(saved.userName ?: "", saved.sessionKey))
            refresh()
        }
    }

    private val ready: Boolean get() = ::scrobbler.isInitialized

    // ---- Settings ----

    /** Connect: show the key and secret fields, filled with the saved ones. */
    fun startConnecting() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { store.read() }
            status = copy(enteringKeys = true, error = null, savedKey = saved?.apiKey ?: "", savedSecret = saved?.secret ?: "")
        }
    }

    fun cancel() {
        connectJob?.cancel()
        connectJob = null
        authorizeUrl = null
        status = copy(enteringKeys = false, waitingForApproval = false, error = null)
    }

    fun connect(apiKey: String, secret: String) {
        val key = apiKey.trim()
        val sharedSecret = secret.trim()
        if (key.isEmpty() || sharedSecret.isEmpty() || !ready) return
        val api = LastFmApi(key, sharedSecret)

        connectJob?.cancel()
        status = copy(waitingForApproval = true, error = null)
        connectJob = scope.launch {
            try {
                withTimeout(POLL_LIMIT_MS) {
                    val token = api.getToken()

                    // The key and secret evidently work, so keep them even if the
                    // approval is never given.
                    withContext(Dispatchers.IO) { store.saveKeys(key, sharedSecret) }

                    authorizeUrl = LastFmApi.authorizeUrl(key, token)
                    openApprovalPage()

                    while (true) {
                        delay(POLL_INTERVAL_MS)
                        try {
                            val session = api.getSession(token)
                            withContext(Dispatchers.IO) { store.saveSession(session.userName, session.key) }
                            scrobbler.connect(key, sharedSecret, session)
                            status = copy(enteringKeys = false, error = null)
                            break
                        } catch (e: LastFmException) {
                            // Still waiting for the user, or a blip: ask again.
                            if (!e.isTokenNotYetAuthorised && !e.isTransient) throw e
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                status = copy(error = "Last.fm wasn't approved within five minutes. Tap Connect to try again.")
            } catch (e: LastFmException) {
                status = copy(
                    error = when (e.code) {
                        10 -> "Last.fm doesn't recognise that API key."
                        13 -> "The shared secret doesn't match that API key."
                        15 -> "The approval expired. Tap Connect to try again."
                        else -> e.message
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                authorizeUrl = null
                status = copy(waitingForApproval = false)
            }
        }
    }

    /** The approval page again, if the browser was closed before approving. */
    fun openApprovalPage() {
        authorizeUrl?.let(::openInBrowser)
    }

    fun openCreateApiAccount() = openInBrowser(LastFmApi.CREATE_API_ACCOUNT_URL)

    fun disconnect() {
        if (!ready) return
        scope.launch(Dispatchers.IO) { store.forgetSession() }
        scrobbler.disconnect()
        status = copy(error = null)
    }

    // ---- playback (PlayWatcher) ----

    override fun started(track: Track) {
        if (ready) scrobbler.trackStarted(track, track.durationMs)
    }

    override fun position(track: Track, positionMs: Long) {
        if (ready) scrobbler.advance(positionMs, track)
    }

    override fun stopped() {
        if (ready) scrobbler.stopped()
    }

    // ---- inside ----

    private fun refresh() {
        status = copy(state = scrobbler.state, userName = scrobbler.userName, queue = queueLine())
    }

    /** The desktop's QueueStatus: what is waiting, and how the last send went. */
    private fun queueLine(): String {
        val pending = when (val n = scrobbler.pending) {
            0 -> "Nothing waiting to send."
            1 -> "1 scrobble waiting to send."
            else -> "%,d scrobbles waiting to send.".format(n)
        }
        scrobbler.lastError?.let { return "$pending Last send failed: $it It will be retried." }
        val sent = scrobbler.lastSentAtMs ?: return pending
        return "$pending Last sent ${android.text.format.DateFormat.format("HH:mm", sent)}."
    }

    private fun copy(
        state: ScrobblerState = status.state,
        userName: String? = status.userName,
        enteringKeys: Boolean = status.enteringKeys,
        waitingForApproval: Boolean = status.waitingForApproval,
        error: String? = status.error,
        queue: String = status.queue,
        savedKey: String = status.savedKey,
        savedSecret: String = status.savedSecret,
    ) = Status(state, userName, enteringKeys, waitingForApproval, error, queue, savedKey, savedSecret)

    private fun openInBrowser(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // No browser; "Open again" stays there.
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
        const val POLL_LIMIT_MS = 5 * 60_000L
    }
}

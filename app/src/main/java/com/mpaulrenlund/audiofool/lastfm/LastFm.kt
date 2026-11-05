package com.mpaulrenlund.audiofool.lastfm

import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Last.fm (spec 7) as Settings shows it, and the scrobbler fed from playback.
 * Ported from the desktop's `LastFmViewModel.cs` (the queue line, the states)
 * around the Core's [LastFmScrobbler].
 *
 * Connecting is the phone's own (the user's call, after 0.8.0): the user types
 * their Last.fm username and password once, and Last.fm's sign-in for phone apps
 * (auth.getMobileSession) trades them for a session key, signed with AudioFool's
 * own API key and secret, [appKey] and [appSecret], built in from outside the repo.
 * The password is sent that once and kept nowhere; the session is stored encrypted
 * ([LastFmStore]). The desktop connects through the browser instead.
 *
 * A session made earlier with another key (the desktop's, typed in before) keeps
 * that key: [LastFmStore] holds each session with the key it was made with.
 *
 * Main thread only.
 */
@Stable
class LastFm(context: Context, folder: File, private val appKey: String, private val appSecret: String) : PlayWatcher {
    private val scope: CoroutineScope = MainScope()
    private val store = LastFmStore(context)
    private lateinit var scrobbler: LastFmScrobbler

    /** What Settings draws. */
    var status: Status by mutableStateOf(Status())
        private set

    class Status(
        val state: ScrobblerState = ScrobblerState.Disconnected,
        val userName: String? = null,
        /** Connect was tapped: the username and password fields are showing. */
        val signingIn: Boolean = false,
        /** The name and password are on their way to Last.fm. */
        val sending: Boolean = false,
        val error: String? = null,
        /** The queue and the last send, in a line ("2 scrobbles waiting to send."). */
        val queue: String = "",
    )

    private var connectJob: Job? = null

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

    /** Connect: show the username and password fields. */
    fun startConnecting() {
        status = copy(signingIn = true, error = null)
    }

    fun cancel() {
        connectJob?.cancel()
        connectJob = null
        status = copy(signingIn = false, sending = false, error = null)
    }

    /** Signs in with the user's Last.fm [userName] (or email) and [password]. */
    fun connect(userName: String, password: String) {
        val name = userName.trim()
        if (name.isEmpty() || password.isEmpty() || !ready) return
        if (appKey.isBlank() || appSecret.isBlank()) {
            status = copy(error = "This build of AudioFool has no Last.fm key, so it can't sign in.")
            return
        }
        val api = LastFmApi(appKey, appSecret)

        connectJob?.cancel()
        status = copy(sending = true, error = null)
        connectJob = scope.launch {
            try {
                val session = api.getMobileSession(name, password)
                withContext(Dispatchers.IO) {
                    store.saveKeys(appKey, appSecret)
                    store.saveSession(session.userName, session.key)
                }
                scrobbler.connect(appKey, appSecret, session)
                status = copy(signingIn = false, error = null)
            } catch (e: LastFmException) {
                status = copy(error = signInError(e))
            } finally {
                status = copy(sending = false)
            }
        }
    }

    /**
     * What Listening History reads with (spec 4.7): the saved API key and the connected
     * user, or null when not connected. Reads need no secret or session, so this works
     * while Last.fm asks to connect again too, as the desktop's does.
     */
    suspend fun historyReader(): HistoryReader? {
        val saved = withContext(Dispatchers.IO) { store.read() } ?: return null
        val user = saved.userName?.takeIf { it.isNotBlank() && saved.sessionKey != null } ?: return null
        return HistoryReader(LastFmApi(saved.apiKey, saved.secret), user)
    }

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
        signingIn: Boolean = status.signingIn,
        sending: Boolean = status.sending,
        error: String? = status.error,
        queue: String = status.queue,
    ) = Status(state, userName, signingIn, sending, error, queue)

    companion object {
        /** What the form says when Last.fm turns the sign-in down. */
        fun signInError(e: LastFmException): String = when (e.code) {
            4 -> "Last.fm didn't accept that username and password."
            10, 26 -> "Last.fm didn't accept AudioFool's own key (${e.message?.trimEnd('.')}). A new build with a new key is needed."
            0 -> e.message ?: "Couldn't reach Last.fm."
            else -> e.message ?: "Last.fm error ${e.code}."
        }
    }
}

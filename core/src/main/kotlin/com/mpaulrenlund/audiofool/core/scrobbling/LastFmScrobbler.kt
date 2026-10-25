package com.mpaulrenlund.audiofool.core.scrobbling

import com.mpaulrenlund.audiofool.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class ScrobblerState {
    Disconnected,
    Scrobbling,

    /** Connected, with scrobbling switched off. */
    Off,

    /** The last send failed and plays are waiting in the queue. */
    Failing,

    /** Last.fm rejected the session or key. */
    NeedsReconnect,
}

/**
 * Turns playback into Last.fm scrobbles: feeds [PlayTracker], sends "now
 * playing" when a play starts, queues each scrobble on disk the moment it is
 * earned, and sends the queue in batches. Ported from the desktop's
 * `AudioFool.Core/Scrobbling/LastFmScrobbler.cs`.
 *
 * Scrobbling at the threshold rather than at the end of the track means a play
 * survives the app being closed halfway through the second half.
 *
 * A failed send leaves the queue alone and backs off, doubling from one minute
 * to half an hour; the next position report after that retries. A rejected
 * session stops sending altogether until the user reconnects; the queue is kept
 * for when they do.
 *
 * Called from one thread (the main one). Sends run in [scope] and never throw
 * back into playback; in the app [scope] runs on the main thread too, so the
 * callbacks arrive there.
 */
class LastFmScrobbler(
    private val queue: ScrobbleQueue,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val apiFactory: (String, String) -> LastFmApi = { key, secret -> LastFmApi(key, secret) },
) {
    private val tracker = PlayTracker()

    @Volatile private var api: LastFmApi? = null
    @Volatile private var session: LastFmSession? = null
    private val flushing = AtomicBoolean(false)
    @Volatile private var retryAtMs = 0L
    @Volatile private var backoffMs = FIRST_BACKOFF_MS

    /** Called whenever [state] may have changed. */
    var onStatusChanged: (() -> Unit)? = null

    /**
     * Called when Last.fm rejects the session or key, with a message to show.
     * Once per failure, not once per retry.
     */
    var onAuthFailed: ((String) -> Unit)? = null

    @Volatile var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            statusChanged()
        }

    /**
     * One word for the screen. A scrobble queued for the moment before it is sent
     * is not [ScrobblerState.Failing]: only a failed send is, so the dot does not
     * flicker after every track.
     */
    val state: ScrobblerState
        get() = when {
            !isConnected -> ScrobblerState.Disconnected
            needsReconnect -> ScrobblerState.NeedsReconnect
            !enabled -> ScrobblerState.Off
            lastError != null && pending > 0 -> ScrobblerState.Failing
            else -> ScrobblerState.Scrobbling
        }

    val isConnected: Boolean get() = session != null

    val userName: String? get() = session?.userName

    /** True after Last.fm rejected the credentials, until [connect]. */
    @Volatile var needsReconnect: Boolean = false
        private set

    val pending: Int get() = queue.count

    /** The last send's error, or null when it went through. */
    @Volatile var lastError: String? = null
        private set

    /** When the queue last went through, epoch milliseconds. */
    @Volatile var lastSentAtMs: Long? = null
        private set

    fun connect(apiKey: String, secret: String, session: LastFmSession) {
        api = apiFactory(apiKey, secret)
        this.session = session
        needsReconnect = false
        lastError = null
        retryAtMs = 0
        backoffMs = FIRST_BACKOFF_MS
        statusChanged()
        flush()
    }

    fun disconnect() {
        api = null
        session = null
        needsReconnect = false
        lastError = null
        statusChanged()
    }

    /** A track began playing from the start. */
    fun trackStarted(track: Track, durationMs: Long) {
        if (tracker.start(track, durationMs, clock())) sendNowPlaying()
    }

    fun stopped() = tracker.stop()

    /**
     * The playing track's tags changed. The play carries on and scrobbles under
     * the new tags; "now playing" is sent again only if what it shows changed.
     */
    fun trackRetagged(track: Track) {
        val before = tracker.nowPlaying()
        if (tracker.retag(track) && tracker.nowPlaying() != before) sendNowPlaying()
    }

    /**
     * The playing position, reported a few times a second while playing, with the
     * track the player says is heard now. Across a gapless join the position can
     * be the new track's for a tick while the tracker still holds the old one;
     * those ticks are skipped rather than read as the old track jumping back to
     * its start.
     */
    fun advance(positionMs: Long, playing: Track? = null) {
        val current = tracker.current
        if (playing != null && current != null && !playing.filePath.equals(current.filePath, ignoreCase = true))
            return

        val now = clock()
        val result = tracker.advance(positionMs, now)

        if (result.restarted) sendNowPlaying()

        // Earned while switched off or disconnected is still not queued: the
        // switch means "don't record my plays", not "record them for later".
        val due = result.due
        if (due != null && enabled && isConnected) {
            queue.add(due)
            statusChanged()
            flush()
        } else if (queue.count > 0 && now >= retryAtMs) {
            flush()
        }
    }

    private fun sendNowPlaying() {
        if (!enabled || needsReconnect) return
        val api = api ?: return
        val session = session ?: return
        val entry = tracker.nowPlaying() ?: return

        scope.launch {
            try {
                api.updateNowPlaying(session.key, entry)
            } catch (e: LastFmException) {
                // "Now playing" is a courtesy; it is not retried or queued.
                if (e.isAuthFailure) rejectSession(e)
            }
        }
    }

    /**
     * Sends the queue, oldest first, a batch at a time, until it is empty or a
     * batch fails. Only one flush runs at once.
     */
    fun flush(): Job = scope.launch { flushNow() }

    private suspend fun flushNow() {
        if (!enabled || needsReconnect) return
        val api = api ?: return
        val session = session ?: return
        // Nothing to send is no news: the desktop's flush of an empty queue
        // finishes before Connect returns, so it raises nothing anyone sees.
        if (queue.count == 0) return
        if (!flushing.compareAndSet(false, true)) return

        try {
            while (true) {
                val batch = queue.peek(LastFmApi.MAX_BATCH)
                if (batch.isEmpty()) break

                val outcomes = try {
                    api.scrobble(session.key, batch)
                } catch (e: LastFmException) {
                    if (e.isAuthFailure) {
                        rejectSession(e)
                    } else {
                        lastError = e.message
                        retryAtMs = clock() + backoffMs
                        backoffMs = minOf(backoffMs * 2, MAX_BACKOFF_MS)
                    }
                    return
                }

                // Deferred (the daily limit) stays queued; everything else is done.
                val done = batch.filterIndexed { i, _ -> outcomes[i] != ScrobbleOutcome.Deferred }
                queue.remove(done)

                lastError = null
                lastSentAtMs = clock()
                backoffMs = FIRST_BACKOFF_MS

                if (done.size < batch.size) {
                    retryAtMs = clock() + MAX_BACKOFF_MS
                    return
                }
            }
        } finally {
            flushing.set(false)
            statusChanged()
        }
    }

    private fun rejectSession(e: LastFmException) {
        if (needsReconnect) return

        needsReconnect = true
        lastError = e.message
        statusChanged()
        onAuthFailed?.invoke("Last.fm stopped accepting scrobbles (${e.message}). Reconnect in Settings.")
    }

    private fun statusChanged() {
        onStatusChanged?.invoke()
    }

    companion object {
        private const val FIRST_BACKOFF_MS = 60_000L
        private const val MAX_BACKOFF_MS = 30 * 60_000L
    }
}

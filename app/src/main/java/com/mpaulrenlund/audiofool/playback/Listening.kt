package com.mpaulrenlund.audiofool.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mpaulrenlund.audiofool.core.library.SafeFile
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.core.playback.ListeningCounter
import com.mpaulrenlund.audiofool.core.playback.ListeningTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The listening time Statistics shows (spec 4.6), counted from the heard position
 * ([ListeningCounter]) and saved to [path] after every [SAVE_EVERY_MS] of
 * listening, and when the queue runs out. The first run starts the count.
 *
 * Main thread only; saves go to [scope] on the IO dispatcher.
 */
@Stable
class Listening(private val path: String, private val scope: CoroutineScope) : PlayWatcher {
    var time: ListeningTime by mutableStateOf(load())
        private set

    private val counter = ListeningCounter()
    private var unsavedMs = 0L

    override fun started(track: Track) {}

    override fun position(track: Track, positionMs: Long) {
        val added = counter.heard(track, positionMs)
        if (added == 0L) return
        time = time.copy(totalMs = time.totalMs + added)
        unsavedMs += added
        if (unsavedMs >= SAVE_EVERY_MS) save()
    }

    override fun stopped() {
        counter.reset()
        save()
    }

    private fun load(): ListeningTime {
        val (saved, _) = SafeFile.read(path, System.currentTimeMillis(), ListeningTime::decode)
        return saved ?: ListeningTime(sinceUtc = System.currentTimeMillis()).also { write(it) }
    }

    private fun save() {
        unsavedMs = 0
        write(time)
    }

    private fun write(value: ListeningTime) {
        val bytes = value.encode()
        scope.launch(Dispatchers.IO) {
            synchronized(this@Listening) {
                try {
                    SafeFile.write(path) { it.write(bytes) }
                } catch (e: IOException) {
                    // The next save tries again.
                }
            }
        }
    }

    private companion object {
        const val SAVE_EVERY_MS = 10_000L
    }
}

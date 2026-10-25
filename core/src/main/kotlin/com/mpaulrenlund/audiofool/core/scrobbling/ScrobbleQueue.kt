package com.mpaulrenlund.audiofool.core.scrobbling

import com.mpaulrenlund.audiofool.core.library.SafeFile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Scrobbles waiting to be sent, persisted so none are lost to the network being
 * down, Last.fm being offline, or the app closing first. Every change is saved
 * straight away with [SafeFile], the save before kept as `scrobbles.json.bak`,
 * which is read if the file is missing or damaged. Ported from the desktop's
 * `AudioFool.Core/Scrobbling/ScrobbleQueue.cs`.
 *
 * Entries older than [MAX_AGE_MS] are dropped on load: Last.fm ignores scrobbles
 * more than two weeks old, so sending them would only waste requests.
 */
class ScrobbleQueue private constructor(private val path: String) {
    private val entries = ArrayList<ScrobbleEntry>()
    private val gate = Any()

    val count: Int get() = synchronized(gate) { entries.size }

    fun add(entry: ScrobbleEntry) {
        synchronized(gate) {
            entries.add(entry)
            save()
        }
    }

    /** The oldest entries, up to [max], without removing them. */
    fun peek(max: Int): List<ScrobbleEntry> = synchronized(gate) { entries.take(max) }

    /** Removes these exact entries (by identity), leaving anything added since. */
    fun remove(sent: Collection<ScrobbleEntry>) {
        synchronized(gate) {
            if (entries.removeAll { e -> sent.any { it === e } }) save()
        }
    }

    private fun save() {
        try {
            SafeFile.write(path) { it.write(json.encodeToString(listSerializer, entries).toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            // Still held in memory, and sent from there if the network comes back.
        } catch (e: SecurityException) {
        }
    }

    companion object {
        const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000

        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(ScrobbleEntry.serializer())

        /**
         * Never throws. The file, else the save before it; with neither, an empty
         * queue: a lost queue costs a few plays, refusing to start would cost more.
         */
        fun load(path: String, nowMs: Long): ScrobbleQueue {
            val queue = ScrobbleQueue(path)
            val (saved, how) = SafeFile.read(path, nowMs) { json.decodeFromString(listSerializer, it.toString(Charsets.UTF_8)) }
            if (saved != null) {
                val oldest = Math.floorDiv(nowMs - MAX_AGE_MS, 1000L)
                queue.entries.addAll(saved.filter { it.timestamp >= oldest })

                // From the backup: a file of its own again, so the next start reads it.
                if (queue.entries.size != saved.size || how == SafeFile.Load.FromBackup)
                    queue.save()
            }
            return queue
        }
    }
}

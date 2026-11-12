package com.mpaulrenlund.audiofool.core.lyrics

import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder

/** Why lyrics couldn't be looked up, in words the Lyrics screen shows. */
class LyricsException(message: String) : Exception(message)

/** One GET; swapped out in tests. Throws [IOException] when LRCLIB can't be reached. */
fun interface LrcLibTransport {
    fun get(url: String): HttpAnswer
}

/**
 * One of LRCLIB's songs: its names, its length in seconds, and its lyrics, timed
 * ([synced], LRC) and plain. [instrumental] when LRCLIB lists it as having none.
 */
data class LrcLibRecord(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double,
    val instrumental: Boolean,
    val plain: String?,
    val synced: String?,
)

/**
 * LRCLIB (lrclib.net): a free, open database of lyrics, most of them timed, with
 * no key or account. It is told the song's artist, title, album and length, as
 * the Genius screen tells Genius. 2026-11-11, the user's call: a song's own files
 * first, then LRCLIB. Every request runs on the IO dispatcher.
 */
class LrcLibClient(private val transport: LrcLibTransport = HttpTransport) {
    /**
     * The song LRCLIB holds under exactly these names, within 2 s of [durationSeconds]
     * (LRCLIB's own rule), or null when it holds none.
     */
    suspend fun get(artist: String, title: String, album: String, durationSeconds: Long): LrcLibRecord? {
        val query = "track_name=${escape(title)}&artist_name=${escape(artist)}&album_name=${escape(album)}&duration=$durationSeconds"
        return request("get?$query", notFoundIsNull = true) { LrcLibJson.record(it) }
    }

    /** LRCLIB's search by title and artist, best first. */
    suspend fun search(artist: String, title: String): List<LrcLibRecord> =
        request("search?track_name=${escape(title)}&artist_name=${escape(artist)}", notFoundIsNull = false) { LrcLibJson.records(it) } ?: emptyList()

    private suspend fun <T> request(path: String, notFoundIsNull: Boolean, read: (JsonElement) -> T?): T? = withContext(Dispatchers.IO) {
        val answer = try {
            transport.get(BASE_URL + path)
        } catch (e: SocketTimeoutException) {
            throw LyricsException("LRCLIB didn't answer in time.")
        } catch (e: IOException) {
            throw LyricsException("Couldn't reach LRCLIB: ${e.message ?: e.javaClass.simpleName}")
        }
        if (answer.status == 404 && notFoundIsNull) return@withContext null

        val doc = try {
            json.parseToJsonElement(answer.body).takeIf { it is JsonObject || it is JsonArray }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        if (answer.status !in 200..299) {
            val message = ((doc as? JsonObject)?.get("message") as? JsonPrimitive)?.takeIf { it.isString }?.content
            throw LyricsException(
                if (!message.isNullOrEmpty()) "LRCLIB answered HTTP ${answer.status}: ${message.trimEnd('.')}"
                else "LRCLIB answered HTTP ${answer.status}.",
            )
        }
        if (doc == null) throw LyricsException("LRCLIB answered in a way AudioFool doesn't recognise.")
        read(doc) ?: throw LyricsException("LRCLIB answered in a way AudioFool doesn't recognise.")
    }

    /** The real one: java.net, 20 seconds to connect and to answer. */
    object HttpTransport : LrcLibTransport {
        override fun get(url: String): HttpAnswer {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                // LRCLIB asks every app to name itself.
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")

                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                return HttpAnswer(status, body)
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net/api/"

        private const val USER_AGENT = "AudioFool/1.0 (personal music player; Android)"

        private val json = Json { ignoreUnknownKeys = true }

        /** Everything but the unreserved characters, spaces as %20. */
        private fun escape(text: String): String =
            URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
    }
}

/** LRCLIB's answers, read loosely: a field missing or of another type is left empty, not a failure. */
object LrcLibJson {
    /** One song, as `get` answers; null when it isn't one. */
    fun record(e: JsonElement): LrcLibRecord? {
        val o = e as? JsonObject ?: return null
        fun text(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return null
        return LrcLibRecord(
            id = id,
            title = text("trackName") ?: "",
            artist = text("artistName") ?: "",
            album = text("albumName") ?: "",
            durationSeconds = (o["duration"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            instrumental = (o["instrumental"] as? JsonPrimitive)?.booleanOrNull ?: false,
            plain = text("plainLyrics")?.takeIf { it.isNotBlank() },
            synced = text("syncedLyrics")?.takeIf { it.isNotBlank() },
        )
    }

    /** The songs `search` answers with; null when the answer isn't a list. */
    fun records(e: JsonElement): List<LrcLibRecord>? = (e as? JsonArray)?.mapNotNull(::record)
}

package com.mpaulrenlund.audiofool.core.genius

import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder

/** One GET; swapped out in tests. Throws [IOException] when Genius can't be reached. */
fun interface GeniusTransport {
    fun get(url: String): HttpAnswer
}

/**
 * Reads song pages from genius.com's own web API: the one its pages are built
 * from, at `genius.com/api/`. It needs no key and, unlike the documented
 * developer API (`api.genius.com`), it has the comments and the questions and
 * answers. It is undocumented, so a change on Genius's side shows up here as a
 * [GeniusException] and nowhere else. Genius never sends lyrics through either.
 *
 * Ported from the desktop's `AudioFool.Core/Genius/GeniusClient.cs` (session 75).
 * Every request runs on the IO dispatcher, its answer read there too.
 */
class GeniusClient(private val transport: GeniusTransport = HttpTransport) {
    /** Genius's search for songs, best first. */
    suspend fun search(query: String, count: Int): List<GeniusHit> =
        get("search/song?per_page=$count&q=${escapeData(query)}", GeniusJson::searchHits)

    suspend fun getSong(id: Long): GeniusSong = get("songs/$id?text_format=plain", GeniusJson::song)

    suspend fun getAnnotations(id: Long): List<GeniusAnnotation> =
        get("referents?song_id=$id&per_page=50&text_format=plain", GeniusJson::annotations)

    suspend fun getAnswers(id: Long): List<GeniusAnswer> =
        get("questions?song_id=$id&per_page=20&text_format=plain", GeniusJson::answers)

    /** One page of comments, most-voted first; pages count from 1. */
    suspend fun getComments(id: Long, page: Int): GeniusCommentPage =
        get("songs/$id/comments?per_page=$COMMENTS_PER_PAGE&page=$page&text_format=plain", GeniusJson::comments)

    /**
     * One GET. Genius wraps every answer as `{"meta": {"status": ...}, "response": ...}`;
     * anything else (a block page, a timeout, no network) becomes a
     * [GeniusException] saying which.
     */
    private suspend fun <T> get(path: String, read: (JsonElement) -> T): T = withContext(Dispatchers.IO) {
        val answer = try {
            transport.get(BASE_URL + path)
        } catch (e: SocketTimeoutException) {
            throw GeniusException("Genius didn't answer in time.")
        } catch (e: IOException) {
            throw GeniusException("Couldn't reach Genius: ${e.message ?: e.javaClass.simpleName}")
        }

        // Every answer of Genius's is an object. kotlinx reads a bare word such as
        // "<html>blocked</html>" as a literal rather than failing, as .NET does, so
        // anything else counts as unreadable here.
        val doc = try {
            json.parseToJsonElement(answer.body) as? JsonObject
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw GeniusException("Genius answered HTTP ${answer.status} without a page AudioFool can read.")

        if (answer.status !in 200..299) {
            val message = (doc["meta"] as? JsonObject)?.get("message")
                ?.let { it as? JsonPrimitive }?.takeIf { it.isString }?.content
            throw GeniusException(
                if (!message.isNullOrEmpty()) "Genius answered HTTP ${answer.status}: ${message.trimEnd('.')}"
                else "Genius answered HTTP ${answer.status}.",
            )
        }

        val response = doc["response"] as? JsonObject
            ?: throw GeniusException("Genius answered in a way AudioFool doesn't recognise.")
        read(response)
    }

    /** The real one: java.net, 20 seconds to connect and to answer. */
    object HttpTransport : GeniusTransport {
        override fun get(url: String): HttpAnswer {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
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
        const val BASE_URL = "https://genius.com/api/"

        /** Most comments Genius sends in one page. */
        const val COMMENTS_PER_PAGE = 50

        private const val USER_AGENT = "AudioFool/1.0 (personal music player; Android)"

        private val json = Json { ignoreUnknownKeys = true }

        /** As .NET's Uri.EscapeDataString: everything but the unreserved characters, spaces as %20. */
        private fun escapeData(text: String): String =
            URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
    }
}

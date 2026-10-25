package com.mpaulrenlund.audiofool.core.scrobbling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

/** An authorised Last.fm session: the key every write call is signed with. */
data class LastFmSession(val userName: String, val key: String)

/**
 * A Last.fm API error. [code] is Last.fm's own error number, or 0 for a network
 * fault or an answer that was not Last.fm's JSON.
 */
class LastFmException(val code: Int, message: String) : Exception(message) {
    /**
     * Worth sending again later: the network, a server error, Last.fm saying it
     * is offline or busy, or rate limiting.
     */
    val isTransient: Boolean get() = code in setOf(0, 11, 16, 29)

    /**
     * The credentials themselves are wrong (the session was revoked, or the key
     * or secret is bad), so retrying cannot help until the user reconnects.
     */
    val isAuthFailure: Boolean get() = code in setOf(4, 9, 10, 13, 26)

    /** auth.getSession before the user has approved the token in the browser. */
    val isTokenNotYetAuthorised: Boolean get() = code == 14
}

/** What Last.fm did with one scrobble in a batch. */
enum class ScrobbleOutcome {
    Accepted,

    /** Refused for good (a filtered artist, a timestamp too old): never resend. */
    Ignored,

    /** The daily scrobble limit: keep it and try again tomorrow. */
    Deferred,
}

/** One HTTP POST, swapped out in tests. Throws [IOException] when Last.fm can't be reached. */
fun interface LastFmTransport {
    fun post(url: String, form: String): HttpAnswer
}

class HttpAnswer(val status: Int, val body: String)

/**
 * The Last.fm 2.0 web API calls a scrobbler needs, signed as the API requires.
 * Ported from the desktop's `AudioFool.Core/Scrobbling/LastFmApi.cs`.
 *
 * Authentication is the desktop flow: [getToken], then the user approves the
 * token on last.fm in their browser ([authorizeUrl]), then [getSession] trades it
 * for a session key that does not expire. The user's password never passes
 * through the app.
 */
class LastFmApi(apiKey: String, secret: String, private val transport: LastFmTransport = HttpTransport) {
    private val apiKey = apiKey.trim()
    private val secret = secret.trim()

    suspend fun getToken(): String {
        val root = call("auth.getToken", emptyList(), sessionKey = null)
        return root.string("token")?.takeIf { it.isNotEmpty() }
            ?: throw LastFmException(0, "Last.fm returned no token.")
    }

    suspend fun getSession(token: String): LastFmSession {
        val root = call("auth.getSession", listOf("token" to token), sessionKey = null)
        val session = root["session"] as? JsonObject
        val key = session?.string("key")
        if (session != null && !key.isNullOrEmpty())
            return LastFmSession(session.string("name") ?: "", key)
        throw LastFmException(0, "Last.fm returned no session.")
    }

    suspend fun updateNowPlaying(sessionKey: String, entry: ScrobbleEntry) {
        val p = mutableListOf("artist" to entry.artist, "track" to entry.track)
        addOptional(p, "album", entry.album)
        addOptional(p, "albumArtist", entry.albumArtist)
        addOptional(p, "trackNumber", entry.trackNumber?.toString())
        addOptional(p, "duration", entry.durationSeconds?.toString())

        call("track.updateNowPlaying", p, sessionKey)
    }

    /**
     * Sends up to [MAX_BATCH] scrobbles in one request and returns what happened
     * to each, in the order given.
     */
    suspend fun scrobble(sessionKey: String, entries: List<ScrobbleEntry>): List<ScrobbleOutcome> {
        require(entries.size in 1..MAX_BATCH) { "Between 1 and $MAX_BATCH scrobbles, not ${entries.size}." }

        val p = mutableListOf<Pair<String, String>>()
        entries.forEachIndexed { i, e ->
            p.add("artist[$i]" to e.artist)
            p.add("track[$i]" to e.track)
            p.add("timestamp[$i]" to e.timestamp.toString())
            addOptional(p, "album[$i]", e.album)
            addOptional(p, "albumArtist[$i]", e.albumArtist)
            addOptional(p, "trackNumber[$i]", e.trackNumber?.toString())
            addOptional(p, "duration[$i]", e.durationSeconds?.toString())
        }

        return parseScrobbleOutcomes(call("track.scrobble", p, sessionKey), entries.size)
    }

    /**
     * One signed POST. Every call is signed, including auth.getToken, which does
     * not need it but accepts it. Last.fm reports errors as JSON with an `error`
     * number, usually under a 4xx or 5xx status; anything that is not JSON at all
     * is treated as a transient fault.
     */
    private suspend fun call(method: String, parameters: List<Pair<String, String>>, sessionKey: String?): JsonObject {
        val p = parameters.toMutableList()
        p.add("method" to method)
        p.add("api_key" to apiKey)
        if (sessionKey != null) p.add("sk" to sessionKey)
        p.add("api_sig" to sign(p, secret))
        p.add("format" to "json")

        val form = p.joinToString("&") { (name, value) -> formEncode(name) + "=" + formEncode(value) }
        val answer = try {
            withContext(Dispatchers.IO) { transport.post(ENDPOINT, form) }
        } catch (e: SocketTimeoutException) {
            throw LastFmException(0, "Last.fm didn't answer in time.")
        } catch (e: IOException) {
            throw LastFmException(0, "Couldn't reach Last.fm: ${e.message}")
        }

        val root = try {
            json.parseToJsonElement(answer.body)
        } catch (e: RuntimeException) {
            throw LastFmException(0, "Last.fm answered HTTP ${answer.status} without JSON.")
        }

        val error = (root as? JsonObject)?.get("error") as? JsonPrimitive
        if (error != null && !error.isString && error.intOrNull != null) {
            val code = error.intOrNull!!
            throw LastFmException(code, root.string("message") ?: "Last.fm error $code.")
        }

        if (answer.status !in 200..299)
            throw LastFmException(0, "Last.fm answered HTTP ${answer.status}.")

        return root as? JsonObject ?: JsonObject(emptyMap())
    }

    companion object {
        const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

        /** Where to make an API key and secret. Last.fm issues one per application. */
        const val CREATE_API_ACCOUNT_URL = "https://www.last.fm/api/account/create"

        /** Where a user can revoke the app's access. The API has no call for it. */
        const val APPLICATIONS_SETTINGS_URL = "https://www.last.fm/settings/applications"

        /** Last.fm takes at most this many scrobbles per request. */
        const val MAX_BATCH = 50

        private const val USER_AGENT = "AudioFool/1.0 (personal music player; Android)"

        private val json = Json { ignoreUnknownKeys = true }

        fun authorizeUrl(apiKey: String, token: String): String =
            "https://www.last.fm/api/auth/?api_key=${escapeData(apiKey.trim())}&token=${escapeData(token)}"

        /**
         * The api_sig: every parameter except `format` and `callback`, sorted by
         * name, each written as name then value with nothing between, the secret
         * appended, and the UTF-8 bytes MD5-hashed to lowercase hex. Names are
         * sorted ordinally (Kotlin's String order), so `album[0]` comes before
         * `artist[0]`.
         */
        fun sign(parameters: List<Pair<String, String>>, secret: String): String {
            val text = StringBuilder()
            for ((name, value) in parameters.filter { it.first != "format" && it.first != "callback" }.sortedBy { it.first })
                text.append(name).append(value)
            text.append(secret)

            return MessageDigest.getInstance("MD5").digest(text.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        /**
         * Reads the per-scrobble `ignoredMessage` codes. Last.fm answers a batch of
         * one with an object rather than an array; both are handled. A response too
         * short to cover every entry counts the rest as accepted: the request
         * itself succeeded, and resending would duplicate them.
         */
        fun parseScrobbleOutcomes(root: JsonElement, count: Int): List<ScrobbleOutcome> {
            val outcomes = MutableList(count) { ScrobbleOutcome.Accepted }
            val list = ((root as? JsonObject)?.get("scrobbles") as? JsonObject)?.get("scrobble") ?: return outcomes

            val items = if (list is JsonArray) list.toList() else listOf(list)
            for (i in 0 until minOf(count, items.size)) {
                val code = ((items[i] as? JsonObject)?.get("ignoredMessage") as? JsonObject)?.get("code") as? JsonPrimitive
                    ?: continue
                outcomes[i] = when (code.contentOrNull?.toIntOrNull() ?: 0) {
                    0 -> ScrobbleOutcome.Accepted
                    5 -> ScrobbleOutcome.Deferred
                    else -> ScrobbleOutcome.Ignored
                }
            }
            return outcomes
        }

        fun parse(text: String): JsonElement = json.parseToJsonElement(text)

        private fun addOptional(p: MutableList<Pair<String, String>>, name: String, value: String?) {
            if (!value.isNullOrBlank()) p.add(name to value)
        }

        private fun formEncode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

        /** As .NET's Uri.EscapeDataString: everything but the unreserved characters, spaces as %20. */
        private fun escapeData(text: String): String =
            URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

        private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    }

    /** The real one: java.net, 20 seconds to connect and to answer. */
    object HttpTransport : LastFmTransport {
        override fun post(url: String, form: String): HttpAnswer {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.doOutput = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                return HttpAnswer(status, body)
            } finally {
                connection.disconnect()
            }
        }
    }
}

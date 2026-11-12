package com.mpaulrenlund.audiofool.core.updates

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
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI

/** Why an update couldn't be looked up or downloaded, in words the pop-up shows. */
class UpdateException(message: String) : Exception(message)

/**
 * A release on GitHub that carries an APK: its version (from the tag), and the APK's
 * address, size and SHA-256 (null when GitHub doesn't give one).
 */
data class AppRelease(val version: AppVersion, val apkUrl: String, val apkSize: Long, val apkSha256: String?)

/** One GET; swapped out in tests. Throws [IOException] when GitHub can't be reached. */
fun interface ReleasesTransport {
    fun get(url: String): HttpAnswer
}

/**
 * The repo's releases, as GitHub lists them to anyone (the repo is public since
 * 2026-11-12, so no token). Every build goes out as a pre-release, so the whole list
 * is read, not GitHub's "latest" (which skips pre-releases). Runs on the IO dispatcher.
 */
class GitHubReleases(private val url: String = RELEASES_API, private val transport: ReleasesTransport = HttpTransport) {
    suspend fun list(): List<AppRelease> = withContext(Dispatchers.IO) {
        val answer = try {
            transport.get(url)
        } catch (e: SocketTimeoutException) {
            throw UpdateException("GitHub didn't answer in time.")
        } catch (e: IOException) {
            throw UpdateException("Couldn't reach GitHub: ${e.message ?: e.javaClass.simpleName}")
        }
        if (answer.status !in 200..299) throw UpdateException("GitHub answered HTTP ${answer.status}.")
        val doc = try {
            json.parseToJsonElement(answer.body)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        doc?.let(ReleasesJson::releases) ?: throw UpdateException("GitHub answered in a way AudioFool doesn't recognise.")
    }

    /** The real one: java.net, 20 seconds to connect and to answer. */
    object HttpTransport : ReleasesTransport {
        override fun get(url: String): HttpAnswer {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                // GitHub turns away a request that doesn't name its app.
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

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
        /** The newest 20 releases, newest first: far more than are ever out between two looks. */
        const val RELEASES_API = "https://api.github.com/repos/mpaulrenlund/AudioFool-Android/releases?per_page=20"

        const val USER_AGENT = "AudioFool-Android"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** GitHub's release list, read loosely: a release that isn't usable (a draft, no APK, a tag that isn't a version) is left out. */
object ReleasesJson {
    /** Null when the answer isn't a list. */
    fun releases(e: JsonElement): List<AppRelease>? = (e as? JsonArray)?.mapNotNull(::release)

    fun release(e: JsonElement): AppRelease? {
        val o = e as? JsonObject ?: return null
        if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return null
        val version = text(o, "tag_name")?.let(AppVersion::parse) ?: return null
        val apk = (o["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { asset ->
            text(asset, "name")?.endsWith(".apk", ignoreCase = true) == true && text(asset, "state").let { it == null || it == "uploaded" }
        } ?: return null
        val url = text(apk, "browser_download_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val size = (apk["size"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        val sha256 = text(apk, "digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
            ?.takeIf { d -> d.length == 64 && d.all { it in '0'..'9' || it in 'a'..'f' } }
        return AppRelease(version, url, size, sha256)
    }

    private fun text(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

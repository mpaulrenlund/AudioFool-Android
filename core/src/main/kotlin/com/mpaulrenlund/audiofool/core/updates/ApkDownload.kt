package com.mpaulrenlund.audiofool.core.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest

/** An open download: its bytes, and what to close when done. */
class Download(val stream: InputStream, private val onClose: () -> Unit = {}) : Closeable {
    override fun close() {
        try {
            stream.close()
        } finally {
            onClose()
        }
    }
}

/** Opens a download; swapped out in tests. Throws [IOException] when it can't be reached. */
fun interface DownloadTransport {
    fun open(url: String): Download
}

/**
 * Downloads a release's APK (spec 9) and checks it is the file GitHub lists: its size,
 * and its SHA-256 when GitHub gives one. Runs on the IO dispatcher.
 */
class ApkDownload(private val transport: DownloadTransport = HttpTransport) {
    /**
     * Writes [release]'s APK to [file], telling [progress] the bytes so far and the total
     * (on the IO thread, after every block). A download that fails, or isn't the file
     * GitHub lists, is deleted and the reason thrown as an [UpdateException]; cancelling
     * the coroutine stops it and deletes the file too.
     */
    suspend fun fetch(release: AppRelease, file: File, progress: (done: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            transport.open(release.apkUrl).use { download ->
                file.outputStream().use { out ->
                    val block = ByteArray(BLOCK)
                    while (true) {
                        ensureActive()
                        val n = download.stream.read(block)
                        if (n < 0) break
                        out.write(block, 0, n)
                        digest.update(block, 0, n)
                        done += n
                        if (done > release.apkSize) throw UpdateException("The download was bigger than the file on GitHub.")
                        progress(done, release.apkSize)
                    }
                }
            }
            if (done != release.apkSize) throw UpdateException("The download stopped early (${UpdateRules.progress(done, release.apkSize)}).")
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.apkSha256 != null && sha256 != release.apkSha256) throw UpdateException("The download didn't match the file on GitHub.")
        } catch (e: Throwable) {
            file.delete()
            throw when (e) {
                is SocketTimeoutException -> UpdateException("GitHub didn't answer in time.")
                is IOException -> UpdateException("Couldn't download the update: ${e.message ?: e.javaClass.simpleName}")
                else -> e
            }
        }
    }

    /** The real one: java.net, following GitHub's redirect to where the file is kept. */
    object HttpTransport : DownloadTransport {
        override fun open(url: String): Download {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", GitHubReleases.USER_AGENT)
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                connection.disconnect()
                throw e
            }
            if (status !in 200..299) {
                connection.disconnect()
                throw UpdateException("GitHub answered HTTP $status to the download.")
            }
            return Download(connection.inputStream, connection::disconnect)
        }
    }

    private companion object {
        const val BLOCK = 64 * 1024
    }
}

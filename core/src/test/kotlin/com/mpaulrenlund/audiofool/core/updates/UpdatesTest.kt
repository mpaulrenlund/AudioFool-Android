package com.mpaulrenlund.audiofool.core.updates

import com.mpaulrenlund.audiofool.core.scrobbling.HttpAnswer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The update pop-up (spec 9): the versions, GitHub's release list, when to look,
// what to offer, and the checked download.

class UpdatesTest {
    private fun v(s: String) = AppVersion.parse(s)!!

    // ---- versions ----

    @Test
    fun `versions compare number by number, not as text`() {
        assertTrue(v("0.18.1") > v("0.18.0"))
        assertTrue(v("0.19.0") > v("0.18.10"))
        assertTrue(v("0.10.0") > v("0.9.9"))
        assertTrue(v("1.0") > v("0.99.99"))
        assertEquals(v("0.19"), v("0.19.0"))
        assertEquals(v("v0.18.1"), v("0.18.1"))
        assertEquals("0.18.1", v("v0.18.1").text)
    }

    @Test
    fun `only plain numbered versions are read`() {
        for (bad in listOf("", "v", "0.18.1-beta", "0..1", "1.2.3.4.5", "latest", "0.x.1", "1234567890.0")) assertNull(AppVersion.parse(bad), bad)
    }

    // ---- GitHub's list ----

    private fun release(tag: String, apk: Boolean = true, draft: Boolean = false, digest: String? = "sha256:" + "ab".repeat(32)) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": true, "name": "AudioFool $tag",
         "assets": [${if (apk) """{"name": "AudioFool-${tag.removePrefix("v")}.apk", "state": "uploaded", "size": 27123325,
           ${digest?.let { "\"digest\": \"$it\"," } ?: ""}
           "content_type": "application/vnd.android.package-archive",
           "browser_download_url": "https://github.com/mpaulrenlund/AudioFool-Android/releases/download/$tag/AudioFool-${tag.removePrefix("v")}.apk"}""" else ""}]}
    """.trimIndent()

    private fun answer(vararg releases: String) = HttpAnswer(200, releases.joinToString(",", "[", "]"))

    @Test
    fun `releases with an APK are read, drafts and APK-less ones left out`() = runBlocking {
        val list = GitHubReleases(transport = { answer(release("v0.18.1"), release("v0.18.0", apk = false), release("v0.19.0", draft = true), release("nightly")) }).list()
        assertEquals(1, list.size)
        val r = list.single()
        assertEquals(v("0.18.1"), r.version)
        assertEquals(27123325, r.apkSize)
        assertEquals("ab".repeat(32), r.apkSha256)
        assertTrue(r.apkUrl.endsWith("/v0.18.1/AudioFool-0.18.1.apk"))
    }

    @Test
    fun `a release without GitHub's digest is still read, with no SHA-256`() = runBlocking {
        assertNull(GitHubReleases(transport = { answer(release("v0.18.1", digest = null)) }).list().single().apkSha256)
    }

    @Test
    fun `GitHub unreachable, refusing or answering nonsense says so`() {
        val unreachable = GitHubReleases(transport = { throw IOException("no route") })
        assertEquals("Couldn't reach GitHub: no route", assertFailsWith<UpdateException> { runBlocking { unreachable.list() } }.message)
        val limited = GitHubReleases(transport = { HttpAnswer(403, """{"message": "API rate limit exceeded"}""") })
        assertEquals("GitHub answered HTTP 403.", assertFailsWith<UpdateException> { runBlocking { limited.list() } }.message)
        val nonsense = GitHubReleases(transport = { HttpAnswer(200, "<html>") })
        assertFailsWith<UpdateException> { runBlocking { nonsense.list() } }
    }

    // ---- when to look, what to offer ----

    @Test
    fun `GitHub is looked at again after six hours, and after the clock goes back`() {
        val hour = 60 * 60 * 1000L
        assertTrue(UpdateRules.isDue(null, 0))
        assertFalse(UpdateRules.isDue(100 * hour, 105 * hour))
        assertTrue(UpdateRules.isDue(100 * hour, 106 * hour))
        assertTrue(UpdateRules.isDue(100 * hour, 99 * hour))
    }

    private fun r(version: String) = AppRelease(v(version), "https://example.invalid/$version.apk", 1, null)

    @Test
    fun `the newest release is offered when it is newer than this build`() {
        val list = listOf(r("0.18.1"), r("0.19.0"), r("0.18.0"))
        assertEquals(v("0.19.0"), UpdateRules.offer(list, v("0.18.1"), skipped = null)?.version)
        assertNull(UpdateRules.offer(list, v("0.19.0"), skipped = null))
        assertNull(UpdateRules.offer(list, v("0.20.0"), skipped = null))
        assertNull(UpdateRules.offer(emptyList(), v("0.18.1"), skipped = null))
    }

    @Test
    fun `a version turned down is not offered again, a newer one is`() {
        assertNull(UpdateRules.offer(listOf(r("0.19.0")), v("0.18.1"), skipped = v("0.19.0")))
        assertEquals(v("0.19.1"), UpdateRules.offer(listOf(r("0.19.0"), r("0.19.1")), v("0.18.1"), skipped = v("0.19.0"))?.version)
    }

    @Test
    fun `progress is in whole megabytes`() {
        assertEquals("0 of 27 MB", UpdateRules.progress(0, 27_123_325))
        assertEquals("12 of 27 MB", UpdateRules.progress(12_400_000, 27_123_325))
    }

    // ---- the download ----

    private val apk = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val apkSha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private fun releaseOf(bytes: ByteArray = apk, sha256: String? = apkSha) = AppRelease(v("0.19.0"), "https://example.invalid/a.apk", bytes.size.toLong(), sha256)
    private fun serving(bytes: ByteArray) = ApkDownload { Download(ByteArrayInputStream(bytes)) }
    private fun tempFile() = Files.createTempDirectory("apk").resolve("update/AudioFool.apk").toFile()

    @Test
    fun `the file GitHub lists is kept, with progress up to its size`() = runBlocking {
        val file = tempFile()
        var last = 0L
        serving(apk).fetch(releaseOf(), file) { done, total ->
            assertTrue(done >= last)
            assertEquals(apk.size.toLong(), total)
            last = done
        }
        assertEquals(apk.size.toLong(), last)
        assertTrue(file.readBytes().contentEquals(apk))
    }

    @Test
    fun `a download that doesn't match GitHub's SHA-256 is deleted`() {
        val file = tempFile()
        val e = assertFailsWith<UpdateException> { runBlocking { serving(apk).fetch(releaseOf(sha256 = "00".repeat(32)), file) { _, _ -> } } }
        assertEquals("The download didn't match the file on GitHub.", e.message)
        assertFalse(file.exists())
    }

    @Test
    fun `a download cut short, or longer than listed, is deleted`() {
        val file = tempFile()
        val short = assertFailsWith<UpdateException> { runBlocking { serving(apk.copyOf(1000)).fetch(releaseOf(), file) { _, _ -> } } }
        assertTrue(short.message!!.startsWith("The download stopped early"))
        assertFalse(file.exists())
        assertFailsWith<UpdateException> { runBlocking { serving(apk + apk).fetch(releaseOf(), file) { _, _ -> } } }
        assertFalse(file.exists())
    }

    @Test
    fun `without GitHub's SHA-256, the size alone decides`() = runBlocking {
        val file = tempFile()
        serving(apk).fetch(releaseOf(sha256 = null), file) { _, _ -> }
        assertNotNull(file.takeIf { it.length() == apk.size.toLong() })
        Unit
    }

    @Test
    fun `a network failure mid-download says so and leaves no file`() {
        val file = tempFile()
        val failing = ApkDownload {
            Download(object : java.io.InputStream() {
                var n = 0
                override fun read(): Int = if (n++ < 5000) 1 else throw IOException("connection reset")
            })
        }
        val e = assertFailsWith<UpdateException> { runBlocking { failing.fetch(releaseOf(), file) { _, _ -> } } }
        assertEquals("Couldn't download the update: connection reset", e.message)
        assertFalse(file.exists())
    }
}

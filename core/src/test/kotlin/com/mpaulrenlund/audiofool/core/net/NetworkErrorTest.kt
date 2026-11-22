package com.mpaulrenlund.audiofool.core.net

import org.junit.After
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The UI/UX review of 2026-11-22, finding 2: network failures in plain words,
// Java's own text to the log. The exceptions are the ones Android's java.net throws.

class NetworkErrorTest {
    @After
    fun quiet() {
        NetworkError.log = {}
    }

    @Test
    fun `no answer in time says so`() {
        assertEquals("Genius didn't answer in time.", NetworkError.sentence("Genius", SocketTimeoutException("timeout")))
    }

    @Test
    fun `a name that doesn't resolve means no internet`() {
        val e = UnknownHostException("Unable to resolve host \"lrclib.net\": No address associated with hostname")
        assertEquals("No internet connection.", NetworkError.sentence("LRCLIB", e))
    }

    @Test
    fun `a certificate Android doesn't trust is no secure connection`() {
        val e = SSLHandshakeException("java.security.cert.CertPathValidatorException: Trust anchor for certification path not found.")
        e.initCause(CertPathValidatorException("Trust anchor for certification path not found."))
        assertEquals("Couldn't make a secure connection to Genius.", NetworkError.sentence("Genius", e))
    }

    @Test
    fun `the cause is found under a wrapper`() {
        val e = IOException("request failed", UnknownHostException("Unable to resolve host \"ws.audioscrobbler.com\""))
        assertEquals("No internet connection.", NetworkError.sentence("Last.fm", e))
    }

    @Test
    fun `anything else is couldn't reach, or the caller's own words`() {
        val e = ConnectException("failed to connect to github.com/140.82.121.3 (port 443): connect failed: ECONNREFUSED")
        assertEquals("Couldn't reach GitHub.", NetworkError.sentence("GitHub", e))
        assertEquals(
            "Couldn't download the update.",
            NetworkError.sentence("GitHub", IOException("connection reset"), otherwise = "Couldn't download the update."),
        )
    }

    @Test
    fun `Java's own text goes to the log, causes and all`() {
        val logged = mutableListOf<String>()
        NetworkError.log = { logged += it }
        val e = SSLHandshakeException("handshake failed")
        e.initCause(CertPathValidatorException("Trust anchor for certification path not found."))

        NetworkError.sentence("Genius", e)

        val line = logged.single()
        assertTrue(line.startsWith("Genius: javax.net.ssl.SSLHandshakeException: handshake failed"), line)
        assertTrue("CertPathValidatorException: Trust anchor for certification path not found." in line, line)
    }
}

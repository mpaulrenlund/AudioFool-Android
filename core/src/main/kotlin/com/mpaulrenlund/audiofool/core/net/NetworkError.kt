package com.mpaulrenlund.audiofool.core.net

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * A request that failed before any answer came (Genius, LRCLIB, Last.fm, GitHub),
 * in a sentence for the screen: the UI/UX review of 2026-11-22, finding 2.
 *
 * The desktop puts .NET's own message after "Couldn't reach Genius:"
 * (`GeniusClient.cs`, `LastFmApi.cs`), and .NET's read as sentences ("No such host
 * is known."). Java's don't ("java.security.cert.CertPathValidatorException: Trust
 * anchor for certification path not found."), so Android words the common causes
 * itself and sends Java's text to [log] instead.
 */
object NetworkError {
    /** Where Java's own text goes; the app points it at logcat. Quiet in the tests. */
    @Volatile
    var log: (String) -> Unit = {}

    /**
     * Why [service] couldn't be asked: no answer in time, no internet (the name
     * didn't resolve: no signal, airplane mode), no secure connection (a Wi-Fi
     * sign-in page, a wrong clock), or else [otherwise].
     */
    fun sentence(service: String, e: IOException, otherwise: String = "Couldn't reach $service."): String {
        val causes = generateSequence<Throwable>(e) { it.cause }.take(MAX_CAUSES).toList()
        log("$service: " + causes.joinToString(" / caused by "))
        return when {
            causes.any { it is SocketTimeoutException } -> "$service didn't answer in time."
            causes.any { it is UnknownHostException } -> "No internet connection."
            causes.any { it is SSLException } -> "Couldn't make a secure connection to $service."
            else -> otherwise
        }
    }

    /** Enough to find the cause under a wrapper or two, and an end should causes ever loop. */
    private const val MAX_CAUSES = 8
}

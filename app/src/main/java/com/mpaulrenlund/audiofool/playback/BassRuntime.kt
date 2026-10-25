package com.mpaulrenlund.audiofool.playback

import com.un4seen.bass.BASS
import com.un4seen.bass.BASSDSD

/**
 * One-time BASS start-up and add-on loading. Ported from the desktop's
 * `AudioFool.Core/Playback/BassRuntime.cs`.
 *
 * BASS is only ever a decoder here: it starts on device 0 ("no sound") and
 * AudioFool's own output stage ([OutputStage]) writes what it decodes to an
 * `AudioTrack`, so BASS never holds an output of its own. Missing add-ons are
 * reported rather than thrown, so the app still runs and can say which formats
 * are unavailable.
 */
object BassRuntime {
    /** Add-on library to the formats it enables, as the desktop lists them. */
    private val optionalPlugins = listOf(
        "libbassflac.so" to "FLAC",
        "libbassdsd.so" to "DSD (.dsf, .dff)",
        "libbassopus.so" to "Opus",
        "libbass_aac.so" to "AAC / M4A",
        "libbassalac.so" to "ALAC",
        "libbassape.so" to "APE (Monkey's Audio)",
        "libbasswv.so" to "WavPack",
        "libbass_mpc.so" to "Musepack",
        "libbass_ac3.so" to "AC-3",
        // WMA is absent: un4seen publishes basswma for Windows only. TAK and DTS
        // have no add-on on any platform (the desktop's README).
    )

    /** The rate DSD is converted to PCM at. */
    const val DSD_PCM_RATE = 88200

    @Volatile var isInitialised = false
        private set

    /** Set when BASS itself could not start. */
    @Volatile var initError: String? = null
        private set

    var availableFormats: List<String> = emptyList()
        private set
    var missingFormats: List<String> = emptyList()
        private set

    /** BASS's version, "2.4.17.x", for the debug panel. */
    var version: String = ""
        private set

    @Synchronized
    fun initialise(): Boolean {
        if (isInitialised) return true
        try {
            if (!BASS.BASS_Init(0, 48000, 0) && BASS.BASS_ErrorGetCode() != BASS.BASS_ERROR_ALREADY) {
                initError = "BASS could not initialise (error ${BASS.BASS_ErrorGetCode()})."
                return false
            }
        } catch (e: UnsatisfiedLinkError) {
            initError = "The BASS library is missing from this build."
            return false
        }

        val v = BASS.BASS_GetVersion()
        version = "${v ushr 24}.${(v ushr 16) and 0xff}.${(v ushr 8) and 0xff}.${v and 0xff}"

        // Always present in BASS itself.
        val available = mutableListOf("MP3", "Ogg Vorbis", "WAV / AIFF", "MOD / S3M / XM / IT")
        val missing = mutableListOf<String>()
        for ((library, formats) in optionalPlugins) {
            if (BASS.BASS_PluginLoad(library, 0) != 0) available += formats else missing += formats
        }
        availableFormats = available
        missingFormats = missing

        // DSD is converted to PCM until DoP (session 4). The desktop picks the highest
        // 44.1-family rate its exclusive device clocks, else 88.2 kHz; the shared output
        // here resamples anyway, so 88.2 kHz (DSD64's rate over 32).
        BASS.BASS_SetConfig(BASSDSD.BASS_CONFIG_DSD_FREQ, DSD_PCM_RATE)
        isInitialised = true
        return true
    }
}

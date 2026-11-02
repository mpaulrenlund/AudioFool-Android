package com.mpaulrenlund.audiofool.core.analysis

import com.mpaulrenlund.audiofool.core.library.AudioFormats
import com.mpaulrenlund.audiofool.core.model.Track
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Ported from the desktop's `AudioFool.Core/Analysis/QualityOpinion.cs`, with its
// wording unchanged. The library-wide Quality Check the flags feed isn't on the
// phone; they are kept so the rules and their tests stay the desktop's.

/** How well the measured audio matches what the file says it is. */
enum class Verdict {
    /** Nothing in the audio contradicts the file's format. */
    Consistent,

    /** The audio is too quiet or too dull to judge. */
    Inconclusive,

    /** A sign of a worse source, but one an honest file can also show. */
    Suspect,

    /** The audio can't have come from what the format promises. */
    Inconsistent,
}

/** What the file says about itself: its kind, bit depth, sample rate and bitrate. */
data class QualityClaim(
    val kind: String,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val isLossy: Boolean,
    val isDsd: Boolean,
    val isModule: Boolean,
) {
    /** "FLAC · 24-bit · 96 kHz", "MP3 · 320 kbps · 44.1 kHz", "DSD128". */
    fun describe(): String {
        if (isDsd) return if (sampleRate != null && sampleRate > 0) "DSD${sampleRate / 44100}" else "DSD"

        val parts = mutableListOf(kind)
        if (isLossy) {
            if (bitrate != null && bitrate > 0) parts += "$bitrate kbps"
        } else if (bitDepth != null && bitDepth > 0) {
            parts += "$bitDepth-bit"
        }
        if (sampleRate != null && sampleRate > 0) parts += QualityOpinion.khz(sampleRate.toDouble())

        return parts.filter { it.isNotEmpty() }.joinToString(" · ")
    }

    companion object {
        fun from(track: Track) = QualityClaim(
            track.kind, track.bitDepth, track.sampleRate, track.bitrate,
            AudioFormats.isLossy(track.filePath), AudioFormats.isDsd(track.filePath), AudioFormats.isModule(track.filePath),
        )
    }
}

/** One thing the analysis found, in a sentence or two. [flag] says which problem it is; null when there is none. */
data class Finding(val verdict: Verdict, val headline: String, val explanation: String, val flag: QualityFlag? = null)

/** The problems the desktop's library-wide check counts, one Statistics row each there. */
enum class QualityFlag {
    /** A lossless file cut off below 19 kHz: made from an MP3 or AAC. */
    LossySource,

    /** A lossless file cut off at 19-20.6 kHz, where a high-bitrate encoder cuts. */
    PossiblyLossySource,

    /** A lossy file cut off below what its bitrate keeps: re-encoded from a lower one. */
    ReEncodedLossy,

    /** A lossy file cut off a little below what its bitrate keeps; some encoders do that anyway. */
    PossiblyReEncodedLossy,

    /** Hi-res or DSD with nothing a CD-quality original couldn't have. */
    Upsampled,

    /** Hi-res or DSD with only faint noise above 25 kHz. */
    PossiblyUpsampled,

    /** A 24-bit file whose music uses only 16 bits. */
    Padded24Bit,
}

/** AudioFool's opinion: the worst finding's verdict and headline, then every finding. */
data class Opinion(val verdict: Verdict, val headline: String, val findings: List<Finding>, val cutoffHz: Double?) {
    /** Every problem found, worst first. */
    val flags: List<QualityFlag> get() = findings.mapNotNull { it.flag }
}

/**
 * Compares what a [SpectrumAnalysis] measured with what the file claims. Pure,
 * so it is tested on synthetic spectra.
 *
 * The rules were set against real files from the desktop's library and fakes
 * made from them (an MP3 decoded to a WAV, a CD track upsampled to 96 kHz,
 * 16-bit audio padded to 24):
 * - A lossy encoder cuts everything above its lowpass, which shows as a cliff:
 *   20 dB or more lost within half a kilohertz, never recovering. MP3s put it at
 *   16-20.5 kHz depending on bitrate. Honest CD masters can have one too, from
 *   the converter's filter, but at 21 kHz or above.
 * - Upsampled hi-res has nothing above the original's limit (22.05 or 24 kHz),
 *   or a mirror image of the treble just above it where the interpolation
 *   filter let it through.
 * - Padded 24-bit audio has eight zero bits under every sample.
 */
object QualityOpinion {
    /** The grid the spectrum is read on, in Hz. */
    private const val STEP = 125.0

    /** Each grid point averages this band, in Hz. */
    private const val BAND = 250.0

    /** A cliff loses at least this much within [CLIFF_WIDTH]. */
    private const val CLIFF_DROP = 20.0
    private const val CLIFF_WIDTH = 500.0

    /** After a cliff, nothing comes back within this much of the level before it. */
    private const val STAYS_DOWN = 15.0

    /** A cliff at or above this is a converter's filter, not a lossy encoder's. */
    const val LOSSY_CEILING_HZ = 20600.0

    /** Below this, a lossy cliff is unmistakable; between it and the ceiling, it's a high bitrate. */
    private const val CLEARLY_LOSSY_HZ = 19000.0

    /**
     * No encoder cuts lower than this, even at 64 kbps. A cliff below it is the
     * music's own edge: an N64 soundtrack's low-rate samples, a lo-fi piano.
     */
    const val LOWEST_ENCODER_CUTOFF_HZ = 11000.0

    /**
     * An MP3 this far short of its bitrate's usual cutoff was re-encoded. Less
     * than this is only possibly so: older iTunes and Xing encoders cut at
     * 16 kHz whatever the bitrate.
     */
    private const val CLEARLY_RE_ENCODED_MARGIN = 2000.0

    /** The treble the other bands are compared with. */
    private const val TREBLE_FROM = 14000.0
    private const val TREBLE_TO = 19000.0

    /** Treble quieter than this is too faint to judge a cutoff by. */
    private const val NO_TREBLE_DB = -110.0

    /** The band only a real hi-res source fills. */
    private const val ULTRASONIC_FROM = 25000.0
    private const val ULTRASONIC_TO = 32000.0

    /** A decoder's output for "nothing here" is far below any recording's noise. */
    private const val SILENCE_DB = -125.0

    /**
     * Ultrasonic content this far below the treble is only faint noise: possibly
     * upsampled. The desktop raised it from 35 after the user vouched for real
     * 96 kHz tracks at 35-44 dB. Upsampled files are still caught by silence above
     * 24 kHz, a cliff below 24.5 kHz or a mirror image.
     */
    const val FAINT_ULTRASONIC_DB = 45.0

    /**
     * Real 24-bit audio uses the extra bits in nearly every sample (99% in the
     * desktop's library). Under half means the music is 16-bit, whatever a fade
     * or an edit added later.
     */
    const val EXTRA_BITS_MINIMUM = 0.5

    fun form(analysis: SpectrumAnalysis, claim: QualityClaim): Opinion {
        val findings = mutableListOf<Finding>()
        val nyquist = analysis.sampleRate / 2.0
        val cliff = findCliff(analysis)

        if (claim.isModule) {
            findings += Finding(
                Verdict.Inconclusive, "Nothing to check",
                "A tracker module isn't a recording: AudioFool renders it from its instruments as it plays, so it has no source quality to compare.",
            )
            return conclude(findings, cliff, claim)
        }

        if (analysis.peakSampleDb < -60 || analysis.durationSeconds < 3) {
            findings += Finding(Verdict.Inconclusive, "Can't tell", "The track is too short or too quiet to judge its source from the spectrum.")
            return conclude(findings, cliff, claim)
        }

        // A 22 or 32 kHz file has no 14-19 kHz; its own top quarter stands in.
        val trebleTo = min(TREBLE_TO, nyquist - 1000)
        val trebleFrom = min(TREBLE_FROM, trebleTo * 0.75)
        val treble = level(analysis, trebleFrom, trebleTo)
        val hasTreble = treble > NO_TREBLE_DB
        val isHiRes = claim.isDsd || analysis.sampleRate > 48000

        if (cliff != null && cliff < LOWEST_ENCODER_CUTOFF_HZ) {
            findings += Finding(
                Verdict.Inconclusive, "Can't tell",
                "The audio stops at ${khz(cliff)}, far below where any encoder cuts: the recording itself has nothing higher (low-rate samples, lo-fi, or filtered on purpose), so its source can't be judged from the spectrum.",
            )
        } else if (claim.isLossy) {
            findings += lossyFinding(claim, nyquist, cliff, if (hasTreble) null else trebleFrom)
        } else {
            findings += cutoffFinding(cliff, if (hasTreble) null else trebleFrom)

            if (isHiRes && hasTreble && (cliff == null || cliff >= LOSSY_CEILING_HZ)) findings += hiResFinding(analysis, claim, cliff, treble)
        }

        val used = analysis.usedBits
        if (!claim.isLossy && !claim.isDsd && claim.bitDepth != null && claim.bitDepth > 16 && used != null) {
            findings += bitsFinding(claim.bitDepth, used, analysis.extraBitsShare)
        }

        return conclude(findings, cliff, claim)
    }

    /**
     * The worst finding sets the verdict and headline. When everything checks
     * out, the headline names the claim instead of the first check.
     */
    private fun conclude(findings: List<Finding>, cutoff: Double?, claim: QualityClaim): Opinion {
        val worst = findings.maxBy { it.verdict }
        val headline = if (worst.verdict == Verdict.Consistent) "Consistent with ${claim.describe()}" else worst.headline
        // The reasons for the verdict first; the sort is stable, so ties keep their order.
        return Opinion(worst.verdict, headline, findings.sortedByDescending { it.verdict }, cutoff)
    }

    private fun lossyFinding(claim: QualityClaim, nyquist: Double, cliff: Double?, noTrebleAbove: Double?): Finding {
        // A 32 kHz MP3 can't keep 19.5 kHz whatever its bitrate.
        val expected = expectedLossyCutoff(claim.bitrate)?.let { min(it, nyquist - 1000) }
        val rate = if (claim.bitrate != null && claim.bitrate > 0) "${claim.bitrate} kbps" else "its bitrate"

        if (cliff != null && expected != null && cliff < expected - CLEARLY_RE_ENCODED_MARGIN) {
            return Finding(
                Verdict.Inconsistent, "Re-encoded from a lower bitrate",
                "The audio stops at ${khz(cliff)}, which is typical of ${bitrateFor(cliff)}. A real $rate file keeps about ${khz(expected)} or more, so this was probably made from a lower-quality file.",
                QualityFlag.ReEncodedLossy,
            )
        }

        if (cliff != null && expected != null && cliff < expected - 300) {
            return Finding(
                Verdict.Suspect, "Possibly re-encoded from a lower bitrate",
                "The audio stops at ${khz(cliff)}, short of the ${khz(expected)} or so a $rate file usually keeps. It may have been made from ${bitrateFor(cliff)}, though some older encoders (iTunes, Xing) cut at 16 kHz whatever the bitrate.",
                QualityFlag.PossiblyReEncodedLossy,
            )
        }

        if (cliff != null) {
            return Finding(
                Verdict.Consistent, "Consistent with ${claim.describe()}",
                "The audio stops at ${khz(cliff)}, which is normal for $rate. Nothing suggests it was made from a lower-quality file.",
            )
        }

        if (noTrebleAbove != null) {
            return Finding(Verdict.Inconclusive, "Can't tell", "There is almost nothing above ${khz(noTrebleAbove)}, so the encoder's cutoff can't be seen.")
        }

        return Finding(
            Verdict.Consistent, "Consistent with ${claim.describe()}",
            "The encoder kept the full frequency range with no cutoff, which some encoders do at high bitrates. Nothing suggests a lower-quality source.",
        )
    }

    private fun cutoffFinding(cliff: Double?, noTrebleAbove: Double?): Finding {
        if (cliff != null && cliff < CLEARLY_LOSSY_HZ) {
            return Finding(
                Verdict.Inconsistent, "Made from a lossy file",
                "The audio stops dead at ${khz(cliff)}, the way an MP3 or AAC encoder cuts it, typical of ${bitrateFor(cliff)}. Lossless audio from a real master doesn't stop like that, so this was probably converted from a lossy file.",
                QualityFlag.LossySource,
            )
        }

        if (cliff != null && cliff < LOSSY_CEILING_HZ) {
            return Finding(
                Verdict.Suspect, "Possibly from a lossy file",
                "The audio stops sharply at ${khz(cliff)}, where a high-bitrate encoder (${bitrateFor(cliff)}) cuts it. A few masters are filtered there too, so this isn't certain.",
                QualityFlag.PossiblyLossySource,
            )
        }

        if (noTrebleAbove != null) {
            return Finding(
                Verdict.Inconclusive, "Can't tell",
                "There is almost nothing above ${khz(noTrebleAbove)}, so a lossy encoder's cutoff wouldn't show. The recording may simply be dull, or very old.",
            )
        }

        val edge = if (cliff != null) "No sign of a lossy encoder: the audio runs to ${khz(cliff)}, where the converter's own filter ends it."
        else "No sign of a lossy encoder: the audio fades out gradually at the top rather than stopping dead."

        return Finding(Verdict.Consistent, "No sign of a lossy source", edge)
    }

    private fun hiResFinding(analysis: SpectrumAnalysis, claim: QualityClaim, cliff: Double?, treble: Double): Finding {
        val what = if (claim.isDsd) "DSD" else "hi-res"

        if (cliff != null && cliff < 24500) {
            return Finding(
                Verdict.Inconsistent, "Not true $what",
                "Nothing comes above ${khz(cliff)}, which is where a 44.1 or 48 kHz original ends. It was probably upsampled, and holds nothing a CD-quality file couldn't.",
                QualityFlag.Upsampled,
            )
        }

        for ((boundary, source) in listOf(22050.0 to "44.1 kHz", 24000.0 to "48 kHz")) {
            if (hasImageNotch(analysis, boundary)) {
                return Finding(
                    Verdict.Inconsistent, "Not true $what",
                    "The spectrum has a gap at ${khz(boundary)} with a mirror image of the treble above it. That is the mark of $source audio upsampled to a higher rate.",
                    QualityFlag.Upsampled,
                )
            }
        }

        // Hi-res rates are 88.2 kHz and up, but stay inside an odd one's range.
        val ultrasonicTo = min(ULTRASONIC_TO, analysis.sampleRate / 2.0 - 1000)
        if (ultrasonicTo < ULTRASONIC_FROM + 2000) {
            return Finding(
                Verdict.Inconclusive, "Can't tell",
                "At ${khz(analysis.sampleRate.toDouble())} there is too little room above CD's range to check for hi-res content.",
            )
        }

        val ultrasonic = level(analysis, ULTRASONIC_FROM, ultrasonicTo)
        val below = treble - ultrasonic

        if (ultrasonic <= SILENCE_DB && below >= 30) {
            return Finding(
                Verdict.Inconsistent, "Not true $what",
                "There is nothing at all above about 24 kHz, where a 44.1 or 48 kHz original ends. It was probably upsampled, and holds nothing a CD-quality file couldn't.",
                QualityFlag.Upsampled,
            )
        }

        if (below >= FAINT_ULTRASONIC_DB) {
            return Finding(
                Verdict.Suspect, "Possibly not true $what",
                "Above 25 kHz there is only faint noise, ${whole(below)} dB below the treble. It may have been upsampled from CD quality, or the recording may simply have little up there.",
                QualityFlag.PossiblyUpsampled,
            )
        }

        return Finding(
            Verdict.Consistent, "Consistent with ${claim.describe()}",
            "There is real content above 25 kHz (${whole(below)} dB below the treble), which a CD-quality original couldn't have.",
        )
    }

    private fun bitsFinding(stated: Int, used: Int, extraShare: Double?): Finding {
        if (used <= 16) {
            return Finding(
                Verdict.Inconsistent, "Not true $stated-bit",
                "Every sample is a 16-bit value with ${stated - 16} zero bits added underneath. This is CD-resolution audio in a $stated-bit file.",
                QualityFlag.Padded24Bit,
            )
        }

        if (extraShare != null && extraShare < EXTRA_BITS_MINIMUM) {
            return Finding(
                Verdict.Inconsistent, "Not true $stated-bit",
                "The music is 16-bit with zero bits added underneath: only ${percent(extraShare)} of samples use more, likely fades or edits made after. Real $stated-bit audio uses them in nearly every sample.",
                QualityFlag.Padded24Bit,
            )
        }

        return Finding(
            Verdict.Consistent, "$stated-bit is real",
            if (used == stated) "The samples use all $stated bits." else "The samples use $used of the $stated bits, finer than CD's 16.",
        )
    }

    /**
     * The lowest point where the spectrum falls off a cliff and stays down, as
     * the frequency halfway down it; null when there is none.
     *
     * The level before a candidate is the median over the kilohertz below it,
     * so a single loud tone (chiptune, FM synthesis) can't fake one.
     */
    fun findCliff(analysis: SpectrumAnalysis): Double? {
        val top = analysis.sampleRate / 2.0 - 150
        val levels = mutableListOf<Pair<Double, Double>>()
        var hz = 1000.0
        while (hz <= top) {
            levels += hz to level(analysis, hz - BAND / 2, hz + BAND / 2)
            hz += STEP
        }

        val ahead = (CLIFF_WIDTH / STEP).toInt()
        val behind = (1000 / STEP).toInt()

        var i = behind
        while (i + ahead < levels.size) {
            val before = median(levels, i - behind, i)
            val here = levels[i].second
            val after = levels[i + ahead].second

            if (here < before - 6 || before - after < CLIFF_DROP) {
                i++
                continue
            }

            val rest = levels.subList(i + ahead, levels.size).maxOf { it.second }
            if (rest > before - STAYS_DOWN) {
                i++
                continue
            }

            // Halfway between the level before and the floor after.
            val floor = median(levels, i + ahead, min(levels.size - 1, i + ahead + behind))
            val target = (before + floor) / 2
            for (j in i until levels.size - 1) {
                if (levels[j + 1].second <= target) return levels[j].first + STEP / 2
            }

            return levels[i].first
        }

        return null
    }

    /**
     * A gap just around [boundary] with louder content both below and above it:
     * the treble of a lower-rate source, mirrored about its Nyquist frequency by
     * upsampling.
     */
    fun hasImageNotch(analysis: SpectrumAnalysis, boundary: Double): Boolean {
        if (analysis.sampleRate / 2.0 < boundary + 3000) return false

        var notch = Double.MAX_VALUE
        var hz = boundary - 500
        while (hz <= boundary + 500) {
            notch = min(notch, level(analysis, hz - BAND / 2, hz + BAND / 2))
            hz += STEP
        }

        val below = level(analysis, boundary - 2500, boundary - 1500)
        val above = level(analysis, boundary + 1500, boundary + 2500)

        return below - notch >= 15 && above - notch >= 10
    }

    /** The mean power between two frequencies, in dB. */
    fun level(analysis: SpectrumAnalysis, fromHz: Double, toHz: Double): Double {
        val lo = analysis.binOf(fromHz)
        val hi = max(lo + 1, analysis.binOf(toHz))
        var sum = 0.0
        for (k in lo until hi) sum += 10.0.pow(analysis.average[k] / 10)
        val mean = sum / (hi - lo)
        val floor = SpectrumAccumulator.FLOOR_DB
        return if (mean <= 0) floor else max(floor, 10 * log10(mean))
    }

    private fun median(levels: List<Pair<Double, Double>>, from: Int, to: Int): Double {
        val values = (from..to).map { levels[it].second }.sorted()
        return values[values.size / 2]
    }

    /**
     * The lowest cutoff an honest file at this bitrate has, from LAME's
     * defaults and what the desktop library's own MP3s show, with some slack.
     */
    fun expectedLossyCutoff(kbps: Int?): Double? = when {
        kbps == null -> null
        kbps >= 300 -> 19500.0
        kbps >= 250 -> 19000.0
        kbps >= 200 -> 18000.0
        kbps >= 170 -> 17000.0
        kbps >= 140 -> 16000.0
        kbps >= 110 -> 15000.0
        else -> null
    }

    /** The bitrate a lossy encoder cutting at this frequency typically runs at. */
    fun bitrateFor(hz: Double): String = when {
        hz < 15500 -> "128 kbps or lower"
        hz < 16500 -> "about 128 kbps"
        hz < 17500 -> "about 128–160 kbps"
        hz < 18600 -> "about 160–192 kbps"
        hz < 19600 -> "about 192–256 kbps"
        else -> "about 256–320 kbps"
    }

    /**
     * "96 kHz", "44.1 kHz", "22.05 kHz" for a sample rate or boundary; a
     * measured frequency to one decimal, "20.2 kHz".
     */
    fun khz(hz: Double): String {
        val pattern = if (hz % 1000 == 0.0) "0" else if (hz % 50 == 0.0) "0.0#" else "0.0"
        return format(pattern, hz / 1000) + " kHz"
    }

    /** .NET's "0": a whole number, halves away from zero. */
    private fun whole(value: Double): String = format("0", value)

    /** .NET's "0.#%": "7%", "7.5%". */
    private fun percent(share: Double): String = format("0.#", share * 100) + "%"

    /** A .NET custom number format: invariant digits, halves rounded away from zero. */
    private fun format(pattern: String, value: Double): String {
        val format = DecimalFormat(pattern, DecimalFormatSymbols(Locale.ROOT))
        format.roundingMode = RoundingMode.HALF_UP
        return format.format(BigDecimal(value))
    }
}

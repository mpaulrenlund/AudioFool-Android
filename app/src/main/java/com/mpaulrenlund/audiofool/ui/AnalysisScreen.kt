package com.mpaulrenlund.audiofool.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mpaulrenlund.audiofool.core.analysis.Opinion
import com.mpaulrenlund.audiofool.core.analysis.QualityClaim
import com.mpaulrenlund.audiofool.core.analysis.QualityOpinion
import com.mpaulrenlund.audiofool.core.analysis.SpectrumAnalysis
import com.mpaulrenlund.audiofool.core.format.Display
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.playback.AnalysisException
import com.mpaulrenlund.audiofool.playback.TrackAnalyzer
import com.mpaulrenlund.audiofool.theme.AppTheme
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Where an analysis has got to. */
sealed interface AnalysisState {
    /** The file being read: 0 to 1. */
    data class Reading(val fraction: Double) : AnalysisState

    /** [picture] is the spectrogram already painted with [stops]; null to paint it here. */
    class Done(val analysis: SpectrumAnalysis, val opinion: Opinion, val picture: ImageBitmap? = null, val stops: List<Color>? = null) : AnalysisState

    class Failed(val message: String) : AnalysisState
}

/**
 * Analyze (0.6.0, the user's call), from Now Playing's cover menu: the desktop's
 * Analysis window (`AnalysisWindow`, `AnalysisViewModel`) as a screen. The song's
 * title and "Artist · Album · FLAC · 24-bit · 96 kHz · 6:14" at the top, then the
 * SPECTRUM panel (the spectrogram, its frequency and time axes, the colour scale
 * and the cutoff's dashed line), then AudioFool's opinion and the figures behind
 * it. The whole file is decoded in the background as the screen opens; leaving it
 * (system back) stops the analysis. Nothing is written. There are no Quality Check
 * buttons: the phone has no library check.
 *
 * [initial] is ThemeLab's: a state to show instead of reading the file.
 */
@Composable
fun AnalysisScreen(track: Track, initial: AnalysisState? = null) {
    val t = LocalAppTheme.current
    val claim = remember(track) { QualityClaim.from(track) }
    var state by remember(track.filePath) { mutableStateOf(initial ?: AnalysisState.Reading(0.0)) }
    var progress by remember(track.filePath) { mutableDoubleStateOf(0.0) }
    val stops = spectrogramStops(t)

    if (initial == null) {
        LaunchedEffect(track.filePath) {
            state = try {
                withContext(Dispatchers.IO) {
                    val analysis = TrackAnalyzer.analyze(track.filePath) { progress = it }
                    val opinion = QualityOpinion.form(analysis, claim)
                    AnalysisState.Done(analysis, opinion, spectrogramImage(analysis, stops, t), stops)
                }
            } catch (e: CancellationException) {
                throw e // the screen was left first
            } catch (e: AnalysisException) {
                AnalysisState.Failed(e.message ?: "The analysis failed.")
            } catch (e: Exception) {
                AnalysisState.Failed("The analysis failed: ${e.message}")
            }
        }
    }
    val shown = state.let { if (it is AnalysisState.Reading && initial == null) AnalysisState.Reading(progress) else it }

    ScreenFrame(topBar = { Header(track, claim) }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel("Spectrum") {
                Box(
                    Modifier.padding(
                        start = t.dp("analysis.panelPaddingX"),
                        end = t.dp("analysis.panelPaddingX"),
                        top = t.dp("analysis.panelPaddingTop"),
                        bottom = t.dp("analysis.panelPaddingBottom"),
                    ),
                ) { Spectrum(shown, stops) }
            }
            if (shown is AnalysisState.Done) OpinionText(shown, claim)
        }
    }
}

/** The song's title over "Artist · Album · FLAC · 24-bit · 96 kHz · 6:14" (the desktop's subtitle), two lines at most. */
@Composable
private fun Header(track: Track, claim: QualityClaim) {
    val t = LocalAppTheme.current
    val artist = track.artist.ifBlank { track.groupingArtist }
    val subtitle = listOf(artist, track.album, claim.describe(), Display.time(track.durationMs)).filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = t.dp("analysis.headerPaddingX"), vertical = t.dp("analysis.headerPaddingY")),
        verticalArrangement = Arrangement.spacedBy(t.dp("analysis.headerLineGap")),
    ) {
        Line(track.displayTitle, t.text("analysis.titleSize", "color.text.heading", weight = t.number("analysis.titleWeight").toInt()))
        Line(subtitle, t.text("analysis.subtitleSize", "color.text.muted"), maxLines = 2)
    }
}

/**
 * The picture with its axes, laid out as the desktop's: the frequencies on the
 * left, the colour scale and its dB on the right, the times underneath. The
 * picture is `analysis.picture` of the screen height. While the file is read it
 * shows "Reading the file… 42%" and a bar on the panel grey; a file that can't be
 * read, the reason.
 */
@Composable
private fun Spectrum(state: AnalysisState, stops: List<Color>) {
    val t = LocalAppTheme.current
    val measurer = rememberTextMeasurer()
    val screenHeight = LocalConfiguration.current.screenHeightDp.toDouble()
    val pictureHeight = (screenHeight * t.number("analysis.picture.ofScreenHeight"))
        .coerceIn(t.number("analysis.picture.min"), t.number("analysis.picture.max")).dp
    val freqAxis = t.dp("analysis.freqAxisWidth")
    val gap = t.dp("analysis.axisGap")
    val legendGap = t.dp("analysis.legendGap")
    val legendWidth = t.dp("analysis.legendWidth")
    val legendLabels = t.dp("analysis.legendLabelWidth")
    val border = t.dp("borderWidth")
    val rightReserve = legendGap + legendWidth + gap + legendLabels
    val done = state as? AnalysisState.Done
    // Painted in the background with the colours it was given; a theme with other spectrogram colours (or
    // ThemeLab, which hands over none) paints it here.
    val picture = done?.let { d ->
        remember(d, stops) { if (d.picture != null && d.stops == stops) d.picture else spectrogramImage(d.analysis, stops, t) }
    }

    Box(Modifier.fillMaxWidth().height(pictureHeight + gap + t.dp("analysis.timeAxisHeight"))) {
        Canvas(Modifier.fillMaxSize()) {
            val label = t.text("analysis.axisLabelSize", "color.text.muted")
            val b = border.toPx()
            val left = (freqAxis + gap).toPx()
            val right = size.width - rightReserve.toPx()
            val bottom = pictureHeight.toPx()
            val inner = Size(right - left - 2 * b, bottom - 2 * b)
            val frame = t.color("color.art.border")

            // The picture's frame and, behind the picture, the scale's quietest colour.
            drawRect(t.color("color.spectrogram.level0"), Offset(left, 0f), Size(right - left, bottom))
            if (picture != null) {
                drawImage(
                    picture,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(picture.width, picture.height),
                    dstOffset = IntOffset((left + b).roundToInt(), b.roundToInt()),
                    dstSize = IntSize(inner.width.roundToInt(), inner.height.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
            }
            drawRect(frame, Offset(left + b / 2, b / 2), Size(right - left - b, bottom - b), style = Stroke(b))

            // The colour scale, quietest at the bottom, labelled every legendStepDb.
            val legendLeft = right + legendGap.toPx()
            val legendRight = legendLeft + legendWidth.toPx()
            drawRect(Brush.verticalGradient(stops.reversed(), startY = b, endY = bottom - b), Offset(legendLeft, b), Size(legendRight - legendLeft, bottom - 2 * b))
            drawRect(frame, Offset(legendLeft + b / 2, b / 2), Size(legendRight - legendLeft - b, bottom - b), style = Stroke(b))
            val floorDb = t.desktopNumber("analysis.floorDb")
            val topDb = t.desktopNumber("analysis.topDb")
            var db = topDb
            while (db >= floorDb) {
                val y = b + ((topDb - db) / (topDb - floorDb) * inner.height).toFloat()
                axisLabel(measurer.measure(formatDb(db), label), legendRight + gap.toPx(), y, alignRight = false)
                db -= t.desktopNumber("analysis.legendStepDb")
            }

            val analysis = done?.analysis ?: return@Canvas
            val nyquist = analysis.sampleRate / 2.0
            val freqStep = niceStep(nyquist / 1000, max(2, (inner.height / t.desktopNumber("analysis.freqLabelSpacing").dp.toPx()).toInt())) * 1000
            var hz = 0.0
            while (hz <= nyquist + 1) {
                val y = b + inner.height - (hz / nyquist * inner.height).toFloat()
                axisLabel(measurer.measure(if (hz == 0.0) "0" else QualityOpinion.khz(hz), label), freqAxis.toPx(), y, alignRight = true)
                hz += freqStep
            }

            val seconds = analysis.durationSeconds
            if (seconds > 0) {
                // At least a second apart: the labels are whole seconds, so a short file would repeat "0:00".
                val tick = max(1.0, niceStep(seconds, max(2, (inner.width / t.desktopNumber("analysis.timeLabelSpacing").dp.toPx()).toInt())))
                var s = 0.0
                while (s <= seconds + 0.001) {
                    val text = measurer.measure(Display.time((s * 1000).toLong()), label)
                    val x = left + b + (s / seconds * inner.width).toFloat() - text.size.width / 2f
                    val maxX = max(left, right - text.size.width)
                    drawText(text, topLeft = Offset(x.coerceIn(left, maxX), bottom + gap.toPx()))
                    s += tick
                }
            }

            // The cutoff: a dashed line across the picture, labelled at its right end.
            val cutoff = done.opinion.cutoffHz ?: return@Canvas
            val marker = t.color("color.spectrogram.marker")
            val thickness = t.desktopNumber("analysis.markerThickness").dp.toPx()
            val dash = t.desktopNumber("analysis.markerDash").dp.toPx()
            val y = b + Math.round(inner.height - cutoff / nyquist * inner.height).toFloat() + thickness / 2
            drawLine(marker, Offset(left + b, y), Offset(right - b, y), thickness, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
            val text = measurer.measure(QualityOpinion.khz(cutoff), label.copy(color = marker))
            drawText(text, topLeft = Offset(right - b - text.size.width - gap.toPx(), max(b, y - text.size.height - gap.toPx() / 2)))
        }

        if (state !is AnalysisState.Done) {
            Box(
                Modifier
                    .padding(start = freqAxis + gap + border, end = rightReserve + border, top = border)
                    .fillMaxWidth()
                    .height(pictureHeight - border * 2)
                    .background(t.color("color.panel.bg")),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    is AnalysisState.Reading -> Reading(state.fraction)
                    is AnalysisState.Failed -> BasicText(
                        state.message,
                        Modifier.padding(horizontal = t.dp("analysis.errorPaddingX")),
                        t.text("analysis.statusSize", "color.text.heading", weight = 600).copy(textAlign = TextAlign.Center),
                    )
                    else -> {}
                }
            }
        }
    }
}

/** "Reading the file… 42%" over a bar in the desktop's scan colours. */
@Composable
private fun Reading(fraction: Double) {
    val t = LocalAppTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(t.dp("analysis.progressGap"))) {
        Line("Reading the file… ${(fraction * 100).toInt()}%", t.text("analysis.statusSize", "color.text.secondary"))
        val shape = RoundedCornerShape(t.radius("radius.sliderTrack"))
        Box(
            Modifier
                .width(t.dp("analysis.progressWidth"))
                .height(t.desktopNumber("statusBar.scanProgressHeight").dp)
                .clip(shape)
                .background(t.color("color.status.scanTrack")),
        ) {
            Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f)).height(t.desktopNumber("statusBar.scanProgressHeight").dp).background(t.color("color.status.scanFill")))
        }
    }
}

/** The verdict, each finding's reasons, then "Cutoff 20.2 kHz · Peak −0.1 dB · 16 of 24 bits used". */
@Composable
private fun OpinionText(done: AnalysisState.Done, claim: QualityClaim) {
    val t = LocalAppTheme.current
    val body = t.text("analysis.bodySize", "color.text.secondary", lineHeight = t.number("analysis.bodyLineHeight"))
    Column(
        Modifier.fillMaxWidth().padding(
            start = t.dp("analysis.opinionPaddingX"),
            end = t.dp("analysis.opinionPaddingX"),
            top = t.dp("analysis.opinionTop"),
            bottom = t.dp("analysis.bottomPadding"),
        ),
        verticalArrangement = Arrangement.spacedBy(t.dp("analysis.findingGap")),
    ) {
        BasicText(done.opinion.headline, style = t.text("analysis.headlineSize", "color.text.primary", weight = t.number("analysis.headlineWeight").toInt()))
        for (finding in done.opinion.findings) if (finding.explanation.isNotEmpty()) BasicText(finding.explanation, style = body)
        BasicText(
            facts(done.analysis, done.opinion, claim),
            Modifier.padding(top = t.dp("analysis.factsTop") - t.dp("analysis.findingGap")),
            t.text("analysis.factsSize", "color.text.muted"),
        )
    }
}

/** The figures behind the opinion, the desktop's footer: "Cutoff 20.2 kHz · Peak −0.1 dB · 16 of 24 bits used". */
internal fun facts(analysis: SpectrumAnalysis, opinion: Opinion, claim: QualityClaim): String {
    val parts = mutableListOf(
        opinion.cutoffHz?.let { "Cutoff ${QualityOpinion.khz(it)}" } ?: "No cutoff",
        "Peak " + String.format(Locale.ROOT, "%.1f", analysis.peakSampleDb).replace("-", "−") + " dB",
    )
    val used = analysis.usedBits
    if (used != null && claim.bitDepth != null) parts += "$used of ${claim.bitDepth} bits used"
    if (claim.isDsd) parts += "Read as ${QualityOpinion.khz(analysis.sampleRate.toDouble())} PCM"
    return parts.joinToString(" · ")
}

/** The six `color.spectrogram.level*` colours, quietest first, read from the theme now. */
@Composable
private fun spectrogramStops(t: AppTheme): List<Color> = remember(t) { (0 until LEVELS).map { t.color("color.spectrogram.level$it") } }

/**
 * Paints the picture as a heat map, one pixel per cell: time left to right,
 * frequency bottom to top, through the scale's colours between `analysis.floorDb`
 * (black) and `analysis.topDb` (yellow). Ported from the desktop's
 * `Services/SpectrogramImage.cs`.
 */
internal fun spectrogramImage(analysis: SpectrumAnalysis, stops: List<Color>, t: AppTheme): ImageBitmap {
    val floorDb = t.desktopNumber("analysis.floorDb")
    val topDb = t.desktopNumber("analysis.topDb")
    val width = analysis.columns
    val height = analysis.rows
    val argb = stops.map { it.toArgb() }
    val pixels = IntArray(width * height)

    for (column in 0 until width) {
        for (row in 0 until height) {
            val db = analysis.picture[column * height + row]
            val f = ((db - floorDb) / (topDb - floorDb)).coerceIn(0.0, 1.0)
            // Row 0 is the lowest frequency, drawn at the bottom.
            pixels[(height - 1 - row) * width + column] = blend(argb, f)
        }
    }

    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun blend(stops: List<Int>, f: Double): Int {
    val position = f * (stops.size - 1)
    val i = minOf(stops.size - 2, position.toInt())
    val w = position - i
    val a = stops[i]
    val b = stops[i + 1]
    fun channel(shift: Int): Int {
        val x = (a shr shift) and 0xFF
        val y = (b shr shift) and 0xFF
        return Math.round(x + (y - x) * w).toInt() and 0xFF
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** A label centred on [y], its right edge at [x] (the frequencies) or its left edge there (the scale's dB). */
private fun DrawScope.axisLabel(text: androidx.compose.ui.text.TextLayoutResult, x: Float, y: Float, alignRight: Boolean) {
    drawText(text, topLeft = Offset(if (alignRight) x - text.size.width else x, y - text.size.height / 2f))
}

/** A round step (1, 2, 5 × a power of ten) giving at most about [count] ticks. The desktop's `NiceStep`. */
internal fun niceStep(range: Double, count: Int): Double {
    val raw = range / max(1, count)
    val power = 10.0.pow(floor(log10(raw)))
    for (m in listOf(1, 2, 5, 10)) if (m * power >= raw) return m * power
    return 10 * power
}

/** "−20 dB", with a proper minus sign as the desktop's legend. */
private fun formatDb(db: Double): String = "${db.roundToInt()} dB".replace("-", "−")

private const val LEVELS = 6

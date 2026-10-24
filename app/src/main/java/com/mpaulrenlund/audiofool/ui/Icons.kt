package com.mpaulrenlund.audiofool.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The mockups' icons (design/mockup), drawn on a 24-unit grid with round caps
 * and joins. Each part is stroked, filled, or both, as the mockup's SVG has it.
 * The stroke width is a token (`icon.strokeOn24Grid`, `icon.searchStrokeOn24Grid`,
 * the checkbox's `settings.checkStrokeOn24Grid`) and the colour is applied where
 * the icon is drawn.
 */
class AppIcon(val parts: List<Part>, val viewportWidth: Float = 24f, val viewportHeight: Float = 24f) {
    class Part(val pathData: String, val stroked: Boolean = true, val filled: Boolean = false)

    constructor(vararg parts: Part) : this(parts.toList())

    fun vector(strokeWidth: Double): ImageVector {
        val builder = ImageVector.Builder(
            defaultWidth = viewportWidth.dp,
            defaultHeight = viewportHeight.dp,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        for (part in parts) {
            builder.addPath(
                pathData = PathParser().parsePathString(part.pathData).toNodes(),
                fill = if (part.filled) SolidColor(Color.Black) else null,
                stroke = if (part.stroked) SolidColor(Color.Black) else null,
                strokeLineWidth = strokeWidth.toFloat(),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}

object AppIcons {
    private fun stroked(path: String) = AppIcon(AppIcon.Part(path))

    /** Settings > Appearance, PS2 Dark. */
    val MOON = stroked("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")

    /** Settings > Appearance, PS1 Light. */
    val SUN = stroked(
        "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0" +
            "M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4",
    )

    /** Settings > Music folders header. */
    val REFRESH = stroked("M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5")

    /** The folder checkbox. */
    val CHECK = stroked("M5 12.5l5 5 9-10.5")

    /** The search field and the top bars' search button (`icon.searchStrokeOn24Grid`). */
    val SEARCH = stroked("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0M16 16l4 4")

    /** Search's clear (x) button. */
    val CLEAR = stroked("M7 7l10 10M17 7L7 17")

    val PREVIOUS = AppIcon(AppIcon.Part("M6 5v14"), AppIcon.Part("M18 5.5L8.5 12l9.5 6.5z", filled = true))

    val NEXT = AppIcon(AppIcon.Part("M18 5v14"), AppIcon.Part("M6 5.5L15.5 12L6 18.5z", filled = true))

    /** Two rounded bars, filled (the mockup's two `rect`s, rx 1.2). */
    val PAUSE = AppIcon(
        AppIcon.Part(
            "M7.7 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1 -1.2 1.2h-1.6a1.2 1.2 0 0 1 -1.2 -1.2v-11.6a1.2 1.2 0 0 1 1.2 -1.2z" +
                "M14.7 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1 -1.2 1.2h-1.6a1.2 1.2 0 0 1 -1.2 -1.2v-11.6a1.2 1.2 0 0 1 1.2 -1.2z",
            stroked = false,
            filled = true,
        ),
    )

    /** No mockup shows it; the skip buttons' triangle, pointing right and centred. */
    val PLAY = AppIcon(AppIcon.Part("M8 5.5L18 12L8 18.5z", filled = true))

    /**
     * The Album table's now-playing triangle (8 x 10, its own grid): the mockup's
     * `M2.2 1.3v7.4L8.5 5z` in a 7.4-wide viewBox, centred in the 8-wide slot.
     */
    val TRIANGLE = AppIcon(listOf(AppIcon.Part("M0.9 1.3v7.4L7.2 5z", filled = true)), viewportWidth = 8f, viewportHeight = 10f)

    /**
     * The no-art placeholder (spec 2: the desktop's music note, `albums.placeholderIconSize`).
     * The desktop draws Fluent's MusicNote2 outline; this is the same two beamed
     * notes in this app's stroked style.
     */
    val MUSIC_NOTE = stroked("M9 17.5V6l10.5-2.5v12M4 17.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0M14.5 15.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0")
}

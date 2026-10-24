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
 * and joins. The stroke width is a token: `icon.strokeOn24Grid`, or the
 * checkbox's own `settings.checkStrokeOn24Grid`. Tinted where they are drawn.
 */
object AppIcons {
    /** Settings > Appearance, PS2 Dark. */
    const val MOON = "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"

    /** Settings > Appearance, PS1 Light. */
    const val SUN = "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0" +
        "M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4"

    /** Settings > Music folders header. */
    const val REFRESH = "M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5"

    /** The folder checkbox. */
    const val CHECK = "M5 12.5l5 5 9-10.5"

    fun stroked(pathData: String, strokeOn24Grid: Double): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeOn24Grid.toFloat(),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}

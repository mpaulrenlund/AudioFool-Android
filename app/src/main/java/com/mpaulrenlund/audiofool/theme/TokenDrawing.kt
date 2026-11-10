package com.mpaulrenlund.audiofool.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.mpaulrenlund.audiofool.core.theme.ShadowLayer
import kotlin.math.max

/**
 * A surface drawn the way the desktop's CSS-style tokens describe it: outer
 * box shadows behind, then the fill, then inset shadows, then a border, then
 * the content. All of it from tokens: [shadows] is a `shadow.*` value, the
 * colours come from the theme.
 */
fun Modifier.tokenSurface(
    shape: Shape,
    background: Color,
    shadows: List<ShadowLayer> = emptyList(),
    border: Color? = null,
    borderWidth: Dp? = null,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    // CSS paints the first shadow on top, so the list is drawn last to first.
    for (layer in shadows.filter { !it.inset }.asReversed()) drawOuterShadow(outline, layer)
    drawOutlineFill(outline, background)
    if (shadows.any { it.inset }) {
        val path = Path().apply { addOutline(outline) }
        clipPath(path) { for (layer in shadows.filter { it.inset }.asReversed()) drawInsetShadow(layer) }
    }
    if (border != null && borderWidth != null) {
        val w = borderWidth.toPx()
        val inner = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
        val path = Path().apply { addOutline(inner); translate(Offset(w / 2, w / 2)) }
        drawPath(path, border, style = Stroke(w))
    }
}

private fun DrawScope.drawOutlineFill(outline: Outline, color: Color) {
    drawPath(Path().apply { addOutline(outline) }, color)
}

private fun DrawScope.px(dp: Double): Float = (dp * density).toFloat()

/**
 * A CSS blur length (dp) as Android's blur radius (px): CSS blurs with a
 * Gaussian of sigma = blur / 2; Android turns a radius r into sigma = 0.57735 r + 0.5.
 */
private fun DrawScope.blurRadius(cssBlur: Double): Float = max(0f, (px(cssBlur) / 2f - 0.5f) / 0.57735f)

private fun DrawScope.drawOuterShadow(outline: Outline, layer: ShadowLayer) {
    if (layer.color.a == 0) return
    val color = layer.color.toCompose()
    val spread = px(layer.spread)
    val bounds = outline.bounds
    val grown = Path().apply { addOutline(outline) }
    val dx = px(layer.offsetX)
    val dy = px(layer.offsetY)
    val blur = blurRadius(layer.blur)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.save()
        // Like CSS, the shadow only shows outside the shape.
        canvas.nativeCanvas.clipOutPath(grown.asAndroid())
        canvas.nativeCanvas.translate(dx, dy)
        if (spread != 0f) {
            val sx = (bounds.width + 2 * spread) / bounds.width
            val sy = (bounds.height + 2 * spread) / bounds.height
            canvas.nativeCanvas.scale(sx, sy, bounds.center.x, bounds.center.y)
        }
        canvas.nativeCanvas.drawPath(grown.asAndroid(), paint)
        canvas.nativeCanvas.restore()
    }
}

private fun DrawScope.drawInsetShadow(layer: ShadowLayer) {
    if (layer.color.a == 0) return
    val dx = px(layer.offsetX)
    val dy = px(layer.offsetY)
    val spread = px(layer.spread)
    val blur = blurRadius(layer.blur)
    // Everything outside the shape moved by the offset (and shrunk by the
    // spread), seen through the shape: the CSS inset shadow.
    val hole = Rect(dx + spread, dy + spread, size.width + dx - spread, size.height + dy - spread)
    val margin = size.maxDimension + 100f
    val ring = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-margin, -margin, size.width + margin, size.height + margin))
        addRect(hole)
    }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color.toCompose().toArgb()
            if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawPath(ring.asAndroid(), paint)
    }
}

private fun Path.asAndroid(): android.graphics.Path = (this as androidx.compose.ui.graphics.AndroidPath).internalPath

/**
 * The window: plain `color.window.bg`. Dark had the desktop's title-bar glow over
 * its top (`color.window.titleGlow`, `titleBar.glow.*`; in the cover's colour on Now
 * Playing from 0.13.0) until 2026-11-10, when the user asked for it to go (spec 2).
 * The desktop's tokens for it are still in the colour files, which stay the desktop's.
 */
@Composable
fun WindowBackground(theme: AppTheme, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier.drawBehind { drawRect(theme.color("color.window.bg")) },
        content = content,
    )
}

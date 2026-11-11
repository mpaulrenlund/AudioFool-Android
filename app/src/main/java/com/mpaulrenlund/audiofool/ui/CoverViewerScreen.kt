package com.mpaulrenlund.audiofool.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mpaulrenlund.audiofool.core.model.Track
import com.mpaulrenlund.audiofool.library.CoverArt
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.WindowBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cover, full screen (spec 5.3; 2026-11-10, the user's call: "tapping on the
 * resolution opens up an album art view in full screen so that I can zoom in"), from
 * the Resolution row of Now Playing's cover menu. The picture at its own size (no more
 * than `coverViewer.maxDecodeSide` a side), fitted inside the system bars on the
 * window's background. Pinch to zoom, up to `coverViewer.maxZoom` times the fitted
 * size, and drag to move round it, kept from leaving a gap at an edge; a double tap
 * zooms to `doubleTapZoom` at the finger, or back out. No buttons: system back closes
 * it. It stays on the song it was opened for, as Analyze and Genius do.
 * [forPictures] is ThemeLab's cover, which can't read one, shown [zoomForPictures] times.
 */
@Composable
fun CoverViewerScreen(track: Track, forPictures: Bitmap? = null, zoomForPictures: Float = 1f) {
    val t = LocalAppTheme.current
    val maxSide = t.number("coverViewer.maxDecodeSide").toInt()
    var image by remember(track.filePath) { mutableStateOf(forPictures?.asImageBitmap()) }
    var read by remember(track.filePath) { mutableStateOf(forPictures != null) }
    if (forPictures == null) {
        LaunchedEffect(track.filePath) {
            image = withContext(Dispatchers.IO) { CoverArt.read(track)?.bytes?.let { decodeWhole(it, maxSide) }?.asImageBitmap() }
            read = true
        }
    }
    WindowBackground(t, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(barInsets()), contentAlignment = Alignment.Center) {
            val shown = image
            when {
                shown != null -> ZoomableCover(shown, t.number("coverViewer.maxZoom").toFloat(), t.number("coverViewer.doubleTapZoom").toFloat(), zoomForPictures)
                // A cover gone since the menu showed its size (the card taken out): the placeholder, as Now Playing's.
                read -> Icon(AppIcons.MUSIC_NOTE, t.desktopNumber("albumHeader.placeholderIconSize").dp, "color.text.muted")
            }
        }
    }
}

/** The cover's bytes decoded whole, halved until no side is over [maxSide] (a 6000 px scan would be 144 MB). */
private fun decodeWhole(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun ZoomableCover(image: ImageBitmap, maxZoom: Float, doubleTapZoom: Float, initialZoom: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val zoom = remember(image, constraints.maxWidth, constraints.maxHeight) {
            CoverZoom(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), Size(image.width.toFloat(), image.height.toFloat()), maxZoom)
        }
        var scale by remember(zoom) { mutableFloatStateOf(initialZoom.coerceIn(1f, maxZoom)) }
        var offset by remember(zoom) { mutableStateOf(Offset.Zero) }
        val scope = rememberCoroutineScope()
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Cover" }
                .pointerInput(zoom) {
                    detectTapGestures(
                        onDoubleTap = { at ->
                            val (toScale, toOffset) = zoom.doubleTap(scale, at, doubleTapZoom)
                            val fromScale = scale
                            val fromOffset = offset
                            scope.launch {
                                animate(0f, 1f) { f, _ ->
                                    scale = fromScale + (toScale - fromScale) * f
                                    offset = fromOffset + (toOffset - fromOffset) * f
                                }
                            }
                        },
                    )
                }
                .pointerInput(zoom) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val (s, o) = zoom.transform(scale, offset, centroid, pan, gestureZoom)
                        scale = s
                        offset = o
                    }
                }
                // Last, so the touches above are in the screen's terms, not the zoomed picture's.
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/**
 * The cover viewer's sums: a picture of [picture]'s size fitted (whole, centred) in
 * [box], drawn [scale] times that about the box's middle and moved by an offset. An
 * offset is kept so the picture never leaves a gap at an edge it reaches past.
 */
class CoverZoom(private val box: Size, picture: Size, private val maxZoom: Float) {
    private val fitted: Size = run {
        val fit = minOf(box.width / picture.width, box.height / picture.height)
        Size(picture.width * fit, picture.height * fit)
    }
    private val middle = Offset(box.width / 2, box.height / 2)

    /** [offset] kept within what [scale] allows: none on an axis where the picture fits in the box. */
    fun clamp(offset: Offset, scale: Float): Offset {
        val x = maxOf(0f, (fitted.width * scale - box.width) / 2)
        val y = maxOf(0f, (fitted.height * scale - box.height) / 2)
        return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
    }

    /** A pinch: zoomed by [zoom] about [centroid], keeping the point under it there, then moved by [pan]. */
    fun transform(scale: Float, offset: Offset, centroid: Offset, pan: Offset, zoom: Float): Pair<Float, Offset> {
        val to = (scale * zoom).coerceIn(1f, maxZoom)
        val c = centroid - middle
        return to to clamp((offset - c) * (to / scale) + c + pan, to)
    }

    /** A double tap at [at]: from the fitted size, [zoomTo] with that point kept under the finger; zoomed in, back to the fitted size. */
    fun doubleTap(scale: Float, at: Offset, zoomTo: Float): Pair<Float, Offset> =
        if (scale > 1.01f) 1f to Offset.Zero
        else {
            val to = zoomTo.coerceIn(1f, maxZoom)
            to to clamp((at - middle) * (1 - to), to)
        }
}

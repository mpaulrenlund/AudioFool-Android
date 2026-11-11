package com.mpaulrenlund.audiofool.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Test
import kotlin.test.assertEquals

/** The cover viewer's sums (spec 5.3): a 1000 × 1000 cover fitted in a 400 × 800 box is 400 × 400, centred. */
class CoverZoomTest {
    private val zoom = CoverZoom(Size(400f, 800f), Size(1000f, 1000f), maxZoom = 8f)
    private val middle = Offset(200f, 400f)

    private fun assertClose(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test fun fitted_it_cannot_be_moved() = assertClose(Offset.Zero, zoom.clamp(Offset(50f, -50f), 1f))

    /** At 3 times it is 1200 × 1200: 400 px spare each side across, 200 up and down. */
    @Test fun zoomed_it_moves_only_as_far_as_its_edges() {
        assertClose(Offset(400f, 200f), zoom.clamp(Offset(999f, 999f), 3f))
        assertClose(Offset(-400f, -200f), zoom.clamp(Offset(-999f, -999f), 3f))
        assertClose(Offset(100f, -50f), zoom.clamp(Offset(100f, -50f), 3f))
    }

    @Test fun a_pinch_keeps_the_point_under_the_fingers_there() {
        val fingers = Offset(300f, 450f)
        val (scale, offset) = zoom.transform(1f, Offset.Zero, fingers, Offset.Zero, 3f)
        assertEquals(3f, scale)
        // The picture point under the fingers before, (fingers − middle) / 1, is drawn at middle + it × 3 + offset.
        assertClose(fingers, middle + (fingers - middle) * 3f + offset)
    }

    @Test fun a_pinch_stops_at_the_fitted_size_and_at_the_most() {
        assertEquals(1f, zoom.transform(1.5f, Offset.Zero, middle, Offset.Zero, 0.2f).first)
        assertEquals(8f, zoom.transform(6f, Offset.Zero, middle, Offset.Zero, 3f).first)
    }

    @Test fun a_drag_moves_it_within_its_edges() {
        val (_, offset) = zoom.transform(3f, Offset.Zero, middle, Offset(150f, 999f), 1f)
        assertClose(Offset(150f, 200f), offset)
    }

    @Test fun a_double_tap_zooms_in_at_the_finger_then_back_out() {
        val at = Offset(250f, 420f)
        val (scale, offset) = zoom.doubleTap(1f, at, 3f)
        assertEquals(3f, scale)
        assertClose(at, middle + (at - middle) * 3f + offset)
        assertEquals(1f to Offset.Zero, zoom.doubleTap(scale, at, 3f))
    }
}

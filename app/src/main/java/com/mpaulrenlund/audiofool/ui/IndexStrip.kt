package com.mpaulrenlund.audiofool.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.mpaulrenlund.audiofool.core.library.ScrollIndex
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import com.mpaulrenlund.audiofool.theme.tokenSurface
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** What ThemeLab pictures of the strip show: the label under a finger. */
data class IndexPreview(val pressedLabel: String)

/** Whether [index] gets a strip: it needs two labels or more. */
fun showsStrip(index: ScrollIndex): Boolean = index.sections.size >= 2

/**
 * The width the rows leave free at the list's right for the strip: the strip and
 * `marginEnd` either side of it.
 */
@Composable
fun stripRoom(): Dp {
    val t = LocalAppTheme.current
    return t.dp("artists.index.width") + t.dp("artists.index.marginEnd") * 2
}

/**
 * A list's index strip (`artists.index.*`): Artists' letters (the user's call,
 * 0.2.0, always shown since 0.2.1) and Albums' years (0.11.0, shown when the
 * albums don't fit). The rows end short of it ([stripRoom]). A finger on it or
 * sliding along it jumps the list to that label's first row, with a tick at each
 * new label, and the bubble shows the label in the middle of the list.
 *
 * When the labels would sit closer than `minSlot` (an artist with forty years of
 * albums on the M21), only every few is written, each over its group's share of
 * the strip; the current one is written in its group's place. The finger still
 * picks each label on its own, and the bubble names it.
 */
@Composable
fun BoxScope.IndexStrip(index: ScrollIndex, list: LazyListState, preview: IndexPreview? = null) {
    if (!showsStrip(index)) return
    val t = LocalAppTheme.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf<Int?>(preview?.pressedLabel?.let { l -> index.sections.indexOfFirst { it.label == l }.takeIf { it >= 0 } }) }
    if (preview != null) {
        // The picture shows the list where the finger took it.
        LaunchedEffect(Unit) { pressed?.let { list.scrollToItem(index.sections[it].firstIndex) } }
    }

    val current = pressed ?: index.sectionAt(list.firstVisibleItemIndex)
    val sections by rememberUpdatedState(index.sections)
    BoxWithConstraints(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(t.dp("artists.index.touchWidth")),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val paddingY = t.dp("artists.index.paddingY")
        val room = maxHeight - t.dp("artists.index.marginY") * 2 - paddingY * 2
        val slot = minOf(t.dp("artists.index.letterSlot"), room / sections.size)
        val stripHeight = slot * sections.size + paddingY * 2
        val groups = labelGroups(sections.size, slot, t.dp("artists.index.minSlot"))
        val density = LocalDensity.current
        val slotPx = with(density) { slot.toPx() }
        val lettersTopPx = with(density) { ((maxHeight - stripHeight) / 2 + paddingY).toPx() }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(index, slotPx, lettersTopPx) {
                    // The whole column takes the finger, so above the first label is the
                    // first and below the last is the last.
                    fun pick(y: Float) {
                        val i = ((y - lettersTopPx) / slotPx).toInt().coerceIn(0, sections.lastIndex)
                        if (i != pressed) {
                            pressed = i
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            scope.launch { list.scrollToItem(sections[i].firstIndex) }
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                        pressed = null
                    }
                },
        )
        Column(
            Modifier
                .padding(end = t.dp("artists.index.marginEnd"))
                .width(t.dp("artists.index.width"))
                .height(stripHeight)
                .tokenSurface(CircleShape, t.color("color.panel.bg"), t.shadow("shadow.panelHighlight"), t.color("color.panel.border"), t.dp("borderWidth"))
                .padding(vertical = paddingY),
        ) {
            groups.forEach { group ->
                val active = current in group
                IndexLabel(sections[if (active) current else group.first].label, slot * group.count(), active)
            }
        }
    }
    val bubble = pressed
    if (bubble != null) IndexBubble(sections[bubble].bubble, Modifier.align(Alignment.Center))
}

/**
 * The labels split into runs that each get one written label: one per run while
 * [slot] is at least [minSlot], else runs of as many as make [minSlot]. A last run
 * shorter than [minSlot] joins the one before it.
 */
internal fun labelGroups(count: Int, slot: Dp, minSlot: Dp): List<IntRange> {
    val step = if (slot >= minSlot) 1 else ceil(minSlot / slot).toInt()
    val groups = (0 until count step step).map { it until minOf(it + step, count) }.toMutableList()
    if (groups.size >= 2 && slot * groups.last().count() < minSlot) {
        val last = groups.removeAt(groups.lastIndex)
        groups[groups.lastIndex] = groups.last().first..last.last
    }
    return groups
}

@Composable
private fun IndexLabel(label: String, height: Dp, active: Boolean) {
    val t = LocalAppTheme.current
    Box(Modifier.size(t.dp("artists.index.width"), height), contentAlignment = Alignment.Center) {
        BasicText(
            label,
            style = if (active) {
                t.text("artists.index.textSize", "color.text.primary", weight = t.number("artists.index.activeWeight").toInt())
            } else {
                t.text("artists.index.textSize", "color.text.muted")
            }.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The label under the finger, big, on the logo menu's lifted surface: a square for
 * a letter, wider for a year ("1997", `bubblePaddingX` either side).
 */
@Composable
private fun IndexBubble(label: String, modifier: Modifier) {
    val t = LocalAppTheme.current
    val size = t.dp("artists.index.bubble")
    Box(
        modifier
            .height(size)
            .widthIn(min = size)
            .tokenSurface(
                RoundedCornerShape(t.radius("radius.surface")),
                t.color("color.panel.bg"),
                t.shadow("shadow.headerArt") + t.shadow("shadow.panelHighlight"),
                t.color("color.panel.border"),
                t.dp("borderWidth"),
            )
            .padding(horizontal = t.dp("artists.index.bubblePaddingX")),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = t.text("artists.index.bubbleTextSize", "color.text.heading", weight = t.number("artists.index.bubbleWeight").toInt()), maxLines = 1)
    }
}

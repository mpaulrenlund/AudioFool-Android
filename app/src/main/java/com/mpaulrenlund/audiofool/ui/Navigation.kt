package com.mpaulrenlund.audiofool.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.mpaulrenlund.audiofool.theme.LocalAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where the app can be (spec 1, 4): a drill-down from Artists, plus Search,
 * Settings and Statistics. Artists and albums are named, not held, so a rescan
 * that rebuilds the library leaves an open screen showing the new one.
 */
@Serializable
sealed interface Route {
    @Serializable data object Artists : Route
    @Serializable data class Albums(val artist: String) : Route
    @Serializable data class Album(val artist: String, val title: String) : Route
    @Serializable data object Search : Route
    @Serializable data object Settings : Route
    @Serializable data object Statistics : Route
    @Serializable data object Debug : Route
    @Serializable data object NowPlaying : Route
    @Serializable data class TokenSheet(val page: SheetPage) : Route
}

/** The back stack. Artists is always at the bottom; system back goes up one level. */
class Navigator(routes: List<Route>) {
    val stack = mutableStateListOf<Route>().apply { addAll(routes.ifEmpty { listOf(Route.Artists) }) }

    val top: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun open(route: Route) {
        if (top != route) stack.add(route)
    }

    /** Shows [route] in place of the top screen (the token sheet's pages). */
    fun replaceTop(route: Route) {
        stack[stack.lastIndex] = route
    }

    /** Goes up one level. */
    fun back() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    companion object {
        private val json = Json

        val saver = Saver<Navigator, List<String>>(
            save = { nav -> nav.stack.map { json.encodeToString(Route.serializer(), it) } },
            restore = { saved -> Navigator(saved.mapNotNull { runCatching { json.decodeFromString(Route.serializer(), it) }.getOrNull() }) },
        )
    }
}

@Composable
fun rememberNavigator(): Navigator = rememberSaveable(saver = Navigator.saver) { Navigator(listOf(Route.Artists)) }

/**
 * Shows the top screen, keeping each screen's own state (its scroll position, the
 * search query) while it is under another. System back goes up one level with
 * predictive back (spec 1): during the gesture the screen underneath shows, and
 * the one being left shrinks toward `predictiveBack.scale` and moves toward the
 * swipe; let go and it slides away, or comes back if the gesture is cancelled.
 * At Artists, back is left to the system, which closes the app.
 */
@Composable
fun NavHost(nav: Navigator, content: @Composable (Route) -> Unit) {
    val t = LocalAppTheme.current
    val holder = rememberSaveableStateHolder()
    val progress = remember { Animatable(0f) }
    val leaving = remember { Animatable(0f) }
    var gesture by remember { mutableStateOf(false) }
    var fromLeft by remember { mutableStateOf(true) }
    val shiftPx = with(LocalDensity.current) { t.dp("predictiveBack.shift").toPx() }
    val scale = t.number("predictiveBack.scale").toFloat()
    val commitMs = t.number("predictiveBack.commitMs").toInt()

    PredictiveBackHandler(enabled = nav.canGoBack) { events ->
        try {
            events.collect { e ->
                gesture = true
                fromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                progress.snapTo(e.progress)
            }
            if (gesture) {
                leaving.animateTo(1f, tween(commitMs))
            }
            // Together, so no frame shows the screen below the one being returned to.
            gesture = false
            nav.back()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { progress.animateTo(0f, tween(commitMs)) }
            throw e
        } finally {
            gesture = false
            withContext(NonCancellable) {
                progress.snapTo(0f)
                leaving.snapTo(0f)
            }
        }
    }

    val stack = nav.stack.toList()
    // Keys by depth too, so the same screen opened twice keeps two states.
    fun stateKey(index: Int) = "$index:${stack[index]}"
    // Each screen shown is one keyed group, so the screen underneath stays the same
    // composition (its scroll position, its selection) when it becomes the top.
    val top = stack.lastIndex
    val shown = if (gesture && stack.size > 1) listOf(top - 1, top) else listOf(top)
    Box(Modifier.fillMaxSize()) {
        for (index in shown) key(stateKey(index)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (gesture && index == top) {
                        val p = progress.value
                        val s = 1f - (1f - scale) * p
                        scaleX = s
                        scaleY = s
                        val direction = if (fromLeft) 1f else -1f
                            translationX = direction * (shiftPx * p + size.width * leaving.value)
                        }
                    },
            ) {
                holder.SaveableStateProvider(stateKey(index)) { content(stack[index]) }
            }
        }
    }
    // Forget the state of screens that have been left.
    val seen = remember { HashSet<String>() }
    val keys = stack.indices.map(::stateKey).toSet()
    SideEffect {
        seen.filterNot { it in keys }.forEach(holder::removeState)
        seen.retainAll(keys)
        seen.addAll(keys)
    }
}

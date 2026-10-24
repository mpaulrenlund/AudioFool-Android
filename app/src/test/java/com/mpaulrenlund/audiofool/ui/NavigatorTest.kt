package com.mpaulrenlund.audiofool.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The back stack (spec 1): Artists at the bottom, back goes up one level, and the
// stack survives the activity being recreated.

class NavigatorTest {
    @Test fun it_starts_at_artists_and_cannot_go_back_from_there() {
        val nav = Navigator(emptyList())
        assertEquals(listOf<Route>(Route.Artists), nav.stack.toList())
        assertFalse(nav.canGoBack)
        nav.back()
        assertEquals(Route.Artists, nav.top)
    }

    @Test fun drilling_down_and_back_up() {
        val nav = Navigator(listOf(Route.Artists))
        nav.open(Route.Albums("Aphelion Drive"))
        nav.open(Route.Album("Aphelion Drive", "Second Sight"))
        assertTrue(nav.canGoBack)
        nav.back()
        assertEquals(Route.Albums("Aphelion Drive"), nav.top)
        nav.back()
        assertEquals(Route.Artists, nav.top)
    }

    @Test fun opening_the_screen_already_shown_does_nothing() {
        val nav = Navigator(listOf(Route.Artists))
        nav.open(Route.Search)
        nav.open(Route.Search)
        assertEquals(2, nav.stack.size)
    }

    @Test fun the_stack_is_saved_and_restored() {
        val nav = Navigator(listOf(Route.Artists))
        nav.open(Route.Search)
        nav.open(Route.Album("The Polygon Set", "Polygon Count"))
        nav.open(Route.TokenSheet(SheetPage.Surfaces))
        val saved = with(Navigator.saver) { SaverScope { true }.save(nav) }!!
        val restored = Navigator.saver.restore(saved)!!
        assertEquals(nav.stack.toList(), restored.stack.toList())
    }

    @Test fun replace_top_swaps_only_the_top_screen() {
        val nav = Navigator(listOf(Route.Artists, Route.Statistics, Route.TokenSheet(SheetPage.Colours1)))
        nav.replaceTop(Route.TokenSheet(SheetPage.Colours2))
        assertEquals(listOf(Route.Artists, Route.Statistics, Route.TokenSheet(SheetPage.Colours2)), nav.stack.toList())
    }
}

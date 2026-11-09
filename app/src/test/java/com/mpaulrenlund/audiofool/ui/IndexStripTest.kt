package com.mpaulrenlund.audiofool.ui

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

// The strip's thinning (0.11.0): labels closer than minSlot share a written label.

class IndexStripTest {
    @Test fun every_label_written_while_they_fit() {
        assertEquals((0 until 28).map { it..it }, labelGroups(28, 17.5.dp, 14.dp))
        assertEquals((0 until 5).map { it..it }, labelGroups(5, 14.dp, 14.dp))
    }

    @Test fun runs_of_as_many_as_make_the_minimum() {
        // 8 dp slots need two to make 14 dp.
        val groups = labelGroups(60, 8.dp, 14.dp)
        assertEquals(30, groups.size)
        assertEquals(listOf(0..1, 2..3), groups.take(2))
        assertEquals((0 until 60).toList(), groups.flatMap { it.toList() })
    }

    @Test fun a_short_last_run_joins_the_one_before() {
        // 7 dp slots: runs of two; the 61st label alone would be 7 dp.
        val groups = labelGroups(61, 7.dp, 14.dp)
        assertEquals(30, groups.size)
        assertEquals(58..60, groups.last())
        assertEquals((0 until 61).toList(), groups.flatMap { it.toList() })
    }
}

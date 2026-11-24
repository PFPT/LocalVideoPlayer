package com.pfpt.localvideoplayer.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHomeRowTest {
    @Test
    fun showsForSectionedTabsThatAreNotMarked() {
        assertTrue(showPinnedHomeRow(true, LibraryTypeTab.ALL, false, false, 2))
        assertTrue(showPinnedHomeRow(true, LibraryTypeTab.EPISODES, false, false, 1))
    }

    @Test
    fun hidesForMarkedSearchSelectionFlatAndEmpty() {
        assertFalse(showPinnedHomeRow(true, LibraryTypeTab.MARKED, false, false, 2))
        assertFalse(showPinnedHomeRow(true, LibraryTypeTab.ALL, true, false, 2))
        assertFalse(showPinnedHomeRow(true, LibraryTypeTab.ALL, false, true, 2))
        assertFalse(showPinnedHomeRow(false, LibraryTypeTab.ALL, false, false, 2))
        assertFalse(showPinnedHomeRow(true, LibraryTypeTab.ALL, false, false, 0))
    }
}

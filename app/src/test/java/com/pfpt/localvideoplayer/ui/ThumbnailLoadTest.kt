package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailLoadTest {
    @Test
    fun scrollingSkipsVisibleKey() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = true,
                itemKey = "row-a",
                visibleKeys = setOf("row-a"),
            ),
        )
    }

    @Test
    fun restingVisibleKeyLoads() {
        assertTrue(
            thumbnailShouldLoad(
                isScrolling = false,
                itemKey = "row-a",
                visibleKeys = setOf("row-a", "row-b"),
            ),
        )
    }

    @Test
    fun restingHiddenKeyStaysDeferred() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = false,
                itemKey = "row-a",
                visibleKeys = setOf("row-b"),
            ),
        )
    }

    @Test
    fun scrollingHiddenKeyStaysDeferred() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = true,
                itemKey = "row-a",
                visibleKeys = setOf("row-b"),
            ),
        )
    }

    @Test
    fun emptyVisibleKeysStayDeferred() {
        assertFalse(
            thumbnailShouldLoad(
                isScrolling = false,
                itemKey = "row-a",
                visibleKeys = emptySet(),
            ),
        )
    }
}
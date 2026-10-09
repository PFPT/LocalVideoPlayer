package com.zhaoshuo.localvideoplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkListTest {
    @Test
    fun dedupesAndKeepsTheLimit() {
        val capped = (0 until 25).fold(emptyList<Long>()) { existing, index ->
            updateBookmarks(existing, index * 1_000L)
        }
        assertEquals(20, capped.size)
        assertEquals(5_000L, capped.first())
        assertEquals(24_000L, capped.last())
        val again = updateBookmarks(capped, 24_000L)
        assertEquals(20, again.size)
        assertEquals(1, again.count { it == 24_000L })
    }
}

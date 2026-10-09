package com.zhaoshuo.localvideoplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentOrderTest {
    @Test
    fun keepsFifteenNewestFirst() {
        val order = (1..20).fold(emptyList<String>()) { acc, index ->
            updateRecentOrder(acc, "uri-$index")
        }
        assertEquals(15, order.size)
        assertEquals("uri-20", order.first())
        assertEquals("uri-6", order.last())
        val moved = updateRecentOrder(order, "uri-10")
        assertEquals("uri-10", moved.first())
        assertEquals(15, moved.size)
        assertEquals(1, moved.count { it == "uri-10" })
    }
}

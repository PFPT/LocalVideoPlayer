package com.pfpt.localvideoplayer.player

fun abLoopTarget(pointA: Long?, pointB: Long?, positionMs: Long): Long? {
    if (pointA == null || pointB == null || pointB <= pointA) return null
    if (positionMs < pointB) return null
    return pointA
}

package com.pfpt.localvideoplayer.download

fun nextDownloadAttempt(stopped: Boolean, canceled: Boolean, attempt: Int, maxAttempts: Int): Int? {
    if (stopped || canceled) return null
    val next = attempt + 1
    if (next >= maxAttempts) return null
    return next
}

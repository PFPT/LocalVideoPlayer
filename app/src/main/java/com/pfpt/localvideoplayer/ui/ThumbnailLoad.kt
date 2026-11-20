package com.pfpt.localvideoplayer.ui

fun thumbnailShouldLoad(
    isScrolling: Boolean,
    itemKey: Any,
    visibleKeys: Set<Any>,
): Boolean {
    if (isScrolling) return false
    return itemKey in visibleKeys
}
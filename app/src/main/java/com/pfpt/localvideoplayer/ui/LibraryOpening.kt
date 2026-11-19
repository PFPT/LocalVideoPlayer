package com.pfpt.localvideoplayer.ui

enum class LibraryOpening {
    Hold,
    List,
    Loading,
    Empty,
}

fun libraryOpening(
    cacheResolved: Boolean,
    entryCount: Int,
    isLibraryLoading: Boolean,
): LibraryOpening {
    if (!cacheResolved) return LibraryOpening.Hold
    if (entryCount > 0) return LibraryOpening.List
    if (isLibraryLoading) return LibraryOpening.Loading
    return LibraryOpening.Empty
}
package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOpeningTest {
    @Test
    fun unresolvedStaysBlankEvenWithEntriesAndLoading() {
        assertEquals(
            LibraryOpening.Hold,
            libraryOpening(cacheResolved = false, entryCount = 3, isLibraryLoading = true),
        )
    }

    @Test
    fun unresolvedEmptyStaysBlank() {
        assertEquals(
            LibraryOpening.Hold,
            libraryOpening(cacheResolved = false, entryCount = 0, isLibraryLoading = false),
        )
    }

    @Test
    fun entriesShowListWhileLoading() {
        assertEquals(
            LibraryOpening.List,
            libraryOpening(cacheResolved = true, entryCount = 1, isLibraryLoading = true),
        )
    }

    @Test
    fun emptyLoadingShowsSpinner() {
        assertEquals(
            LibraryOpening.Loading,
            libraryOpening(cacheResolved = true, entryCount = 0, isLibraryLoading = true),
        )
    }

    @Test
    fun resolvedEmptyShowsEmptyLibrary() {
        assertEquals(
            LibraryOpening.Empty,
            libraryOpening(cacheResolved = true, entryCount = 0, isLibraryLoading = false),
        )
    }
}
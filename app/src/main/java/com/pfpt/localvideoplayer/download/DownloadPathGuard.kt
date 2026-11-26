package com.pfpt.localvideoplayer.download

import com.pfpt.localvideoplayer.util.StoragePath
import java.io.File
import java.io.IOException

internal object DownloadPathGuard {
    fun resolveInside(root: File, relative: String): File? = StoragePath.resolveInside(root, relative)

    fun isInside(root: File, candidate: File): Boolean = StoragePath.isInside(root, candidate)

    fun isInsideAny(roots: List<File>, candidate: File): Boolean = StoragePath.isInsideAny(roots, candidate)

    fun requireInside(root: File, candidate: File): File {
        if (!isInside(root, candidate)) throw IOException("path escapes download directory")
        return candidate
    }
}

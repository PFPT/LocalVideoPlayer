package com.pfpt.localvideoplayer.util

import java.io.File

object StoragePath {
    fun resolveInside(root: File, relative: String): File? {
        if (relative.isBlank() || relative.contains('\u0000')) return null
        val rootFile = canonicalOrNull(root) ?: return null
        val candidate = canonicalOrNull(File(rootFile, relative)) ?: return null
        return candidate.takeIf { isInside(rootFile, candidate) }
    }

    fun isInside(root: File, candidate: File): Boolean {
        val rootFile = canonicalOrNull(root) ?: return false
        val candidateFile = canonicalOrNull(candidate) ?: return false
        val rootPath = rootFile.path
        val candidatePath = candidateFile.path
        return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
    }

    fun isInsideAny(roots: List<File>, candidate: File): Boolean =
        roots.any { root -> isInside(root, candidate) }

    private fun canonicalOrNull(file: File): File? = runCatching { file.canonicalFile }.getOrNull()
}

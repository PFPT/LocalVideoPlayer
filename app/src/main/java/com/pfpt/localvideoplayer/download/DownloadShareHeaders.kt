package com.pfpt.localvideoplayer.download

internal object DownloadShareHeaders {
    fun accepted(senderPackage: String?, ownPackage: String, headers: Map<String, String>): Map<String, String> {
        if (senderPackage.isNullOrBlank() || senderPackage != ownPackage) return emptyMap()
        return headers
    }
}

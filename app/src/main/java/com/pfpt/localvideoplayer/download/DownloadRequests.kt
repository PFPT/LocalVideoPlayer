package com.pfpt.localvideoplayer.download

import okhttp3.Request
import okhttp3.Response

internal object DownloadRequests {
    internal fun executeHlsRequest(
        address: String,
        headers: Map<String, String>,
        playlistUrl: String?,
    ): Response {
        val prepared = if (playlistUrl.isNullOrBlank()) {
            DownloadUrlResolver.ensureDownloadHeaders(headers)
        } else {
            DownloadUrlResolver.hlsChildHeaders(playlistUrl, headers)
        }
        val response = executeRequest(address, prepared)
        if (response.code != 403) return response
        val refererKey = prepared.keys.firstOrNull { it.equals("Referer", ignoreCase = true) } ?: return response
        response.close()
        return executeRequest(address, prepared.filterKeys { !it.equals(refererKey, ignoreCase = true) })
    }

    internal fun executeRequest(
        address: String,
        headers: Map<String, String>,
        method: String = "GET",
        rangeStart: Long? = null,
        rangeHeader: String? = null,
    ): Response {
        val builder = Request.Builder().url(address).method(method, null)
        headers.forEach { (name, value) -> builder.header(name, value) }
        when {
            rangeHeader != null -> builder.header("Range", rangeHeader)
            rangeStart != null -> builder.header("Range", "bytes=$rangeStart-")
        }
        if (method == "HEAD") {
            builder.header("Accept-Encoding", "identity")
        }
        return DownloadHttpClient.client.newCall(builder.build()).execute()
    }

    internal fun contentTotalBytes(response: Response, offset: Long): Long {
        val range = response.header("Content-Range")
        val rangeTotal = range?.substringAfterLast('/')?.toLongOrNull()
        val length = response.body?.contentLength()?.takeIf { it >= 0 }
        return rangeTotal ?: length?.plus(offset) ?: -1
    }
}

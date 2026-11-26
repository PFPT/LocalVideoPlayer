package com.pfpt.localvideoplayer.download

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.pfpt.localvideoplayer.R

class DownloadShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shareText = extractSharedDownloadText(intent)
        val parsed = shareText?.let(DownloadNameResolver::parseSharedText)
        val parsedUrl = shareText?.let { text ->
            val url = parsed?.url ?: DownloadUrlClassifier.extractFirstMagnet(text.trim())
            url?.takeIf(::isDownloadableUrl)
        }
        if (parsedUrl.isNullOrBlank()) {
            toast(R.string.download_invalid_url)
            finish()
            return
        }
        val trusted = if (parsed?.explicitTitle == true) parsed.title?.trim().orEmpty() else ""
        val trustName = trusted.isNotBlank()
        val appContext = applicationContext
        DownloadManager.get(appContext).enqueue(
            url = parsedUrl,
            fileName = trusted,
            headers = DownloadShareHeaders.accepted(
                senderPackage = callingPackage ?: referrer?.host,
                ownPackage = packageName,
                headers = extractSharedHeaders(intent),
            ),
            saveToMovies = false,
            trustFileName = trustName,
        ) { usedProxy ->
            val message = if (usedProxy) R.string.download_proxy_unwrapped else R.string.download_add_queue
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(applicationContext, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun isDownloadableUrl(url: String): Boolean {
        val isHttp = url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        return isHttp || DownloadUrlClassifier.isMagnetUrl(url)
    }

    private fun extractSharedHeaders(intent: Intent): Map<String, String> {
        val bundle = intent.getBundleExtra("extra_headers") ?: return emptyMap()
        val headers = LinkedHashMap<String, String>()
        for (key in bundle.keySet()) {
            val value = bundle.getString(key)?.trim().orEmpty()
            if (key.isNotBlank() && value.isNotBlank()) headers[key] = value
        }
        return headers
    }

    private fun extractSharedDownloadText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isBlank()) return null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
            .ifBlank { intent.getStringExtra(Intent.EXTRA_TITLE)?.trim().orEmpty() }
        return if (text.isHttpUrl() && subject.isNotBlank()) {
            "$subject|$text"
        } else {
            text
        }
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://") || startsWith("https://")
}

package com.pfpt.localvideoplayer.download

import java.io.IOException
import java.net.SocketTimeoutException

class AdvancedDownloadUnavailable : IOException()

fun downloadFailureMessage(
    error: Exception,
    unavailableMessage: String,
    timeoutMessage: String,
    fallbackMessage: String,
): String {
    if (error is AdvancedDownloadUnavailable) return unavailableMessage
    if (error is SocketTimeoutException || error.cause is SocketTimeoutException) return timeoutMessage
    return error.message?.takeIf { it.isNotBlank() } ?: fallbackMessage
}
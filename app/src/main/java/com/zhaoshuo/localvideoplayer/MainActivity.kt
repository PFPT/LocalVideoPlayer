package com.zhaoshuo.localvideoplayer

import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhaoshuo.localvideoplayer.util.PictureInPictureHelper
import com.zhaoshuo.localvideoplayer.download.DownloadService

class MainActivity : ComponentActivity() {

    var isInPipMode: Boolean = false
        private set

    var onPipModeChanged: ((Boolean) -> Unit)? = null
    var onPipAction: (() -> Unit)? = null
    private var sharedDownloadText by mutableStateOf<String?>(null)
    private var downloadsNavigationTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        sharedDownloadText = extractSharedDownloadText(intent)
        if (intent?.getBooleanExtra(DownloadService.EXTRA_OPEN_DOWNLOADS, false) == true) {
            downloadsNavigationTick++
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        setContent {
            LocalVideoPlayerApp(
                sharedDownloadText = sharedDownloadText,
                onSharedDownloadConsumed = { sharedDownloadText = null },
                downloadsNavigationTick = downloadsNavigationTick,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedDownloadText = extractSharedDownloadText(intent)
        if (intent.getBooleanExtra(DownloadService.EXTRA_OPEN_DOWNLOADS, false)) {
            downloadsNavigationTick++
        }
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

    fun enterPipMode(
        hasNext: Boolean,
        isPlaying: Boolean,
        aspectRatio: Rational = Rational(16, 9),
    ): Boolean {
        return PictureInPictureHelper.enterPip(this, aspectRatio, hasNext, isPlaying)
    }

    fun updatePipActions(hasNext: Boolean, isPlaying: Boolean) {
        PictureInPictureHelper.updatePipActions(this, Rational(16, 9), hasNext, isPlaying)
    }

    fun isPipSupported(): Boolean {
        return PictureInPictureHelper.isSupported(this)
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1001
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        onPipModeChanged?.invoke(isInPictureInPictureMode)
    }
}

package com.zhaoshuo.localvideoplayer

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhaoshuo.localvideoplayer.util.PictureInPictureHelper

class MainActivity : ComponentActivity() {

    var isInPipMode: Boolean = false
        private set

    var onPipModeChanged: ((Boolean) -> Unit)? = null
    var onPipAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LocalVideoPlayerApp()
        }
    }

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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        onPipModeChanged?.invoke(isInPictureInPictureMode)
    }
}

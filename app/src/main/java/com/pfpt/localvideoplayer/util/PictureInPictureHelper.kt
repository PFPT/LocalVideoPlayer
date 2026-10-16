package com.pfpt.localvideoplayer.util

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.pip.PipActionReceiver

object PictureInPictureHelper {

    private val MinAspect = Rational(100, 239)
    private val MaxAspect = Rational(239, 100)
    private val FallbackAspect = Rational(16, 9)

    fun isSupported(activity: Activity): Boolean {
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun aspectRatioForVideo(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) return FallbackAspect
        val raw = Rational(width, height)
        val value = raw.toFloat()
        return when {
            value < MinAspect.toFloat() -> MinAspect
            value > MaxAspect.toFloat() -> MaxAspect
            else -> raw
        }
    }

    fun buildPipParams(
        context: Context,
        aspectRatio: Rational = FallbackAspect,
        hasNext: Boolean,
        isPlaying: Boolean,
    ): PictureInPictureParams {
        val actions = buildActions(context, hasNext, isPlaying)
        val paramsBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(actions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paramsBuilder.setSeamlessResizeEnabled(true)
        }
        return paramsBuilder.build()
    }

    fun enterPip(
        activity: Activity,
        aspectRatio: Rational = FallbackAspect,
        hasNext: Boolean,
        isPlaying: Boolean,
    ): Boolean {
        if (!isSupported(activity)) return false
        return activity.enterPictureInPictureMode(
            buildPipParams(activity, aspectRatio, hasNext, isPlaying),
        )
    }

    fun updatePipActions(
        activity: Activity,
        aspectRatio: Rational = FallbackAspect,
        hasNext: Boolean,
        isPlaying: Boolean,
    ) {
        if (!isSupported(activity) || !activity.isInPictureInPictureMode) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.setPictureInPictureParams(
                buildPipParams(activity, aspectRatio, hasNext, isPlaying),
            )
        }
    }

    private fun buildActions(
        context: Context,
        hasNext: Boolean,
        isPlaying: Boolean,
    ): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()
        actions.add(createPlayPauseAction(context, isPlaying))
        actions.add(createSeekForwardAction(context))
        if (hasNext) {
            actions.add(createNextAction(context))
        }
        return actions
    }

    private fun createPlayPauseAction(context: Context, isPlaying: Boolean): RemoteAction {
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val title = context.getString(if (isPlaying) R.string.pip_pause else R.string.pip_play)
        return RemoteAction(
            Icon.createWithResource(context, iconRes),
            title,
            title,
            pendingIntent(context, PipActionReceiver.ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE),
        )
    }

    private fun createSeekForwardAction(context: Context): RemoteAction {
        val title = context.getString(R.string.seek_forward)
        val description = context.getString(R.string.seek_forward_desc)
        return RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_media_ff),
            title,
            description,
            pendingIntent(context, PipActionReceiver.ACTION_SEEK_FORWARD, REQUEST_SEEK_FORWARD),
        )
    }

    private fun createNextAction(context: Context): RemoteAction {
        val title = context.getString(R.string.next)
        return RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_media_next),
            title,
            title,
            pendingIntent(context, PipActionReceiver.ACTION_PLAY_NEXT, REQUEST_PLAY_NEXT),
        )
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PipActionReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private const val REQUEST_PLAY_PAUSE = 1001
    private const val REQUEST_SEEK_FORWARD = 1002
    private const val REQUEST_PLAY_NEXT = 1003
}

package com.zhaoshuo.localvideoplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
import com.zhaoshuo.localvideoplayer.R
import java.util.concurrent.Executors

object CastHelper {

    @Volatile
    private var castPlayer: CastPlayer? = null

    fun startCast(
        context: Context,
        title: String,
        uri: Uri,
        localPlayer: Player? = null,
    ): Boolean {
        val castContext = tryGetCastContext(context)
        if (castContext != null) {
            val player = castPlayer ?: CastPlayer(castContext).also { castPlayer = it }
            if (player.isCastSessionAvailable) {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .build(),
                    )
                    .build()
                player.setMediaItem(mediaItem, localPlayer?.currentPosition ?: 0L)
                player.prepare()
                player.playWhenReady = true
                localPlayer?.pause()
                return true
            }
            Toast.makeText(
                context,
                context.getString(R.string.cast_no_devices),
                Toast.LENGTH_SHORT,
            ).show()
            return openSystemChooser(context, title, uri)
        }
        return openSystemChooser(context, title, uri)
    }

    private fun tryGetCastContext(context: Context): CastContext? {
        return try {
            CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor()).result
        } catch (_: Exception) {
            try {
                CastContext.getSharedInstance(context)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun openSystemChooser(context: Context, title: String, uri: Uri): Boolean {
        return try {
            val castIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(
                castIntent,
                context.getString(R.string.cast),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.cast_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }
}

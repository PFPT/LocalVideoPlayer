package com.zhaoshuo.localvideoplayer.pip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhaoshuo.localvideoplayer.player.PlayerSession

class PipActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> PlayerSession.togglePlayPause()
            ACTION_SEEK_FORWARD -> PlayerSession.seekForward(SEEK_FORWARD_MS)
            ACTION_PLAY_NEXT -> PlayerSession.seekToNext()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.zhaoshuo.localvideoplayer.pip.PLAY_PAUSE"
        const val ACTION_SEEK_FORWARD = "com.zhaoshuo.localvideoplayer.pip.SEEK_FORWARD"
        const val ACTION_PLAY_NEXT = "com.zhaoshuo.localvideoplayer.pip.PLAY_NEXT"
        const val SEEK_FORWARD_MS = 10_000L
    }
}

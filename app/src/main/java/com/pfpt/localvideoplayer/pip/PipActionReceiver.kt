package com.pfpt.localvideoplayer.pip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pfpt.localvideoplayer.player.PlayerSession

class PipActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> PlayerSession.togglePlayPause()
            ACTION_SEEK_FORWARD -> PlayerSession.seekForward(SEEK_FORWARD_MS)
            ACTION_PLAY_NEXT -> PlayerSession.seekToNext()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.pfpt.localvideoplayer.pip.PLAY_PAUSE"
        const val ACTION_SEEK_FORWARD = "com.pfpt.localvideoplayer.pip.SEEK_FORWARD"
        const val ACTION_PLAY_NEXT = "com.pfpt.localvideoplayer.pip.PLAY_NEXT"
        const val SEEK_FORWARD_MS = 10_000L
    }
}

package com.pfpt.localvideoplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pfpt.localvideoplayer.MainActivity
import com.pfpt.localvideoplayer.R

@UnstableApi
class PlaybackMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var sessionPlayer: Player? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_notification_channel)
                .build(),
        )
        ensureChannel()
        ensureSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureSession()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        sessionPlayer = null
        super.onDestroy()
    }

    private fun ensureSession() {
        val raw = PlayerSession.current()
        if (raw == null) {
            if (mediaSession == null) stopSelf()
            return
        }
        if (mediaSession != null && sessionPlayer === raw) return
        showPlaceholderForeground()
        val replacement = MediaSession.Builder(this, EpisodeSkipPlayer(raw))
            .setSessionActivity(sessionActivityIntent())
            .build()
            .also { session -> session.setMediaButtonPreferences(transportButtons()) }
        val previous = mediaSession
        mediaSession = replacement
        sessionPlayer = raw
        previous?.release()
    }

    private fun transportButtons(): List<CommandButton> {
        return listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .setDisplayName(getString(R.string.previous))
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .setDisplayName(getString(R.string.next))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun showPlaceholderForeground() {
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            placeholderNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun placeholderNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.playback_notification_channel))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(sessionActivityIntent())
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun sessionActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_PLAYER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private class EpisodeSkipPlayer(player: Player) : ForwardingPlayer(player) {
        override fun seekToNext() {
            PlayerSession.skipBy(1)
        }

        override fun seekToNextMediaItem() {
            PlayerSession.skipBy(1)
        }

        override fun seekToPrevious() {
            PlayerSession.skipBy(-1)
        }

        override fun seekToPreviousMediaItem() {
            PlayerSession.skipBy(-1)
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        private const val CHANNEL_ID = "playback"
    }
}

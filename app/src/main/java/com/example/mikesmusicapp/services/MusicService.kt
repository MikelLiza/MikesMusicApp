package com.example.mikesmusicapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.mikesmusicapp.R

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val channelId = "music_channel_01"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize with dummy player, will be replaced
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        // Create and show notification immediately
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Music Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Music Player")
            .setContentText("Playing music")
            .setSmallIcon(R.drawable.ic_music_note)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    fun updatePlayer(player: ExoPlayer) {
        mediaSession?.release()
        mediaSession = MediaSession.Builder(this, player).build()
        // Update notification with new player info
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        stopForeground(true)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 101
    }
}
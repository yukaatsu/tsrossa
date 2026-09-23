package com.yuka.musicplayer.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class AudioForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Acquire Wakelock to protect the C++ Isochronous Thread from Doze mode
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer::AudioIsochronousLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L /*10 hours max*/) 

        // Listen for player state changes to keep the notification in sync
        AudioPlayerManager.onNotificationUpdateRequired = {
            updateNotification()
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // 1. Content Intent (Tapping notification opens MainActivity)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, com.yuka.musicplayer.MainActivity::class.java)
        launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val contentPendingIntent = PendingIntent.getActivity(this, 0, launchIntent, flags)

        // 2. Transport Pending Intents
        val prevIntent = Intent(this, AudioForegroundService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, flags)

        val togglePlayIntent = Intent(this, AudioForegroundService::class.java).apply { action = ACTION_TOGGLE_PLAY }
        val togglePlayPendingIntent = PendingIntent.getService(this, 2, togglePlayIntent, flags)

        val nextIntent = Intent(this, AudioForegroundService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, flags)

        val stopIntent = Intent(this, AudioForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, flags)

        val track = AudioPlayerManager.currentTrack
        val isPlaying = AudioPlayerManager.isPlaying ||
            (AudioPlayerManager.isInitialized && AudioPlayerManager.audioEngine.isPlaying())
        val isDac = AudioPlayerManager.isDacConnected

        val title = track?.title ?: "tsrossa Audiophile Player"
        val subtitle = if (track != null) {
            val dacLabel = if (isDac) "Bit-Perfect UAC2" else "Audio Ready"
            "${track.artist} • $dacLabel"
        } else {
            if (isDac) "USB DAC Connected" else "Standby"
        }

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        // MediaStyle configuration
        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(0, 1, 2) // Prev, Play/Pause, Next in compact view

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setStyle(mediaStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, togglePlayPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setLargeIcon(track?.coverArt)

        return builder.build()
    }

    private var lastNotificationTime = 0L
    private val notificationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val notificationRunnable = Runnable {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification())
            lastNotificationTime = System.currentTimeMillis()
        } catch (e: Exception) {
            android.util.Log.w("AudioForegroundService", "Error posting notification: ${e.message}")
        }
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationTime >= 250L) {
            notificationHandler.removeCallbacks(notificationRunnable)
            notificationRunnable.run()
        } else {
            notificationHandler.removeCallbacks(notificationRunnable)
            notificationHandler.postDelayed(notificationRunnable, 250L - (now - lastNotificationTime))
        }
    }
    
    override fun onDestroy() {
        notificationHandler.removeCallbacks(notificationRunnable)
        AudioPlayerManager.onNotificationUpdateRequired = null
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> {
                AudioPlayerManager.requestPlayPrev()
            }
            ACTION_TOGGLE_PLAY -> {
                AudioPlayerManager.requestTogglePlay()
            }
            ACTION_NEXT -> {
                AudioPlayerManager.requestPlayNext()
            }
            ACTION_PLAY -> {
                if (wakeLock?.isHeld != true) wakeLock?.acquire(10 * 60 * 60 * 1000L)
                updateNotification()
            }
            ACTION_PAUSE -> {
                if (wakeLock?.isHeld == true) wakeLock?.release()
                updateNotification()
            }
            ACTION_UPDATE -> {
                updateNotification()
            }
            ACTION_STOP -> {
                AudioPlayerManager.release()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val isStillPlaying = AudioPlayerManager.isPlaying ||
            (AudioPlayerManager.isInitialized && AudioPlayerManager.audioEngine.isPlaying())
        
        if (!isStillPlaying) {
            android.util.Log.i("AudioForegroundService", "onTaskRemoved: Playback is paused/stopped. Gracefully releasing DAC and stopping service.")
            AudioPlayerManager.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } else {
            android.util.Log.i("AudioForegroundService", "onTaskRemoved: Playback is active. Maintaining background service.")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bit-Perfect Audio Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "AUDIO_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 1
        
        const val ACTION_PREV = "com.yuka.musicplayer.ACTION_PREV"
        const val ACTION_TOGGLE_PLAY = "com.yuka.musicplayer.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.yuka.musicplayer.ACTION_NEXT"
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
    }
}

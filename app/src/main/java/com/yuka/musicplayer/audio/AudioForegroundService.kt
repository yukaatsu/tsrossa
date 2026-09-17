package com.yuka.musicplayer.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class AudioForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Acquire Wakelock immediately to protect the C++ Isochronous Thread
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer::AudioIsochronousLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L /*10 hours max*/) 
        
        val stopIntent = Intent(this, AudioForegroundService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        )

        createNotificationChannel()
        startForeground(1, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val stopIntent = Intent(this, AudioForegroundService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, com.yuka.musicplayer.MainActivity::class.java)
        launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val contentPendingIntent = android.app.PendingIntent.getActivity(
            this, 0, launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        )

        val track = AudioPlayerManager.currentTrack
        val title = track?.title ?: "Audiophile Engine Active"
        val subtitle = if (track != null) "${track.artist} • Bit-Perfect UAC2" else "Bit-Perfect USB Engine is running"

        return NotificationCompat.Builder(this, "AUDIO_SERVICE_CHANNEL")
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop & Release DAC", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PLAY" -> {
                if (wakeLock?.isHeld != true) wakeLock?.acquire(10 * 60 * 60 * 1000L)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(1, buildNotification())
            }
            "ACTION_PAUSE" -> {
                if (wakeLock?.isHeld == true) wakeLock?.release()
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(1, buildNotification())
            }
            "ACTION_STOP" -> {
                AudioPlayerManager.release()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
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
        // Do NOT call stopSelf() here! We want the music to keep playing
        // and the DAC to stay locked even if the user swipes the app away.
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "AUDIO_SERVICE_CHANNEL",
                "Bit-Perfect Audio Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}

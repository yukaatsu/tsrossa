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
        val notification = NotificationCompat.Builder(this, "AUDIO_SERVICE_CHANNEL")
            .setContentTitle("Audiophile Engine Active")
            .setContentText("Bit-Perfect USB Engine is running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop & Release DAC", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Priority low to stay out of the way
            .build()
            
        startForeground(1, notification)
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
            }
            "ACTION_PAUSE" -> {
                if (wakeLock?.isHeld == true) wakeLock?.release()
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

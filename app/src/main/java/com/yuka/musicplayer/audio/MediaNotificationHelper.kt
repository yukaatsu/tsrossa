package com.yuka.musicplayer.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.yuka.musicplayer.R
import com.yuka.musicplayer.model.TrackInfo

object MediaNotificationHelper {

    private var cachedCoverTrackPath: String? = null
    private var cachedSafeCover1024: Bitmap? = null
    private var cachedSafeCover512: Bitmap? = null

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                AudioForegroundService.CHANNEL_ID,
                "Bit-Perfect Audio Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    fun getSafeCoverArt(rawBitmap: Bitmap?, trackPath: String?, maxDimension: Int = 1024): Bitmap? {
        if (rawBitmap == null) {
            cachedCoverTrackPath = null
            cachedSafeCover1024 = null
            cachedSafeCover512 = null
            return null
        }
        if (cachedCoverTrackPath == trackPath) {
            if (maxDimension >= 1024 && cachedSafeCover1024 != null) return cachedSafeCover1024
            if (maxDimension <= 512 && cachedSafeCover512 != null) return cachedSafeCover512
        }

        val width = rawBitmap.width
        val height = rawBitmap.height
        val scaled = if (width > maxDimension || height > maxDimension) {
            try {
                val ratio = width.toFloat() / height.toFloat()
                val targetW: Int
                val targetH: Int
                if (ratio >= 1f) {
                    targetW = maxDimension
                    targetH = (maxDimension / ratio).toInt().coerceAtLeast(1)
                } else {
                    targetH = maxDimension
                    targetW = (maxDimension * ratio).toInt().coerceAtLeast(1)
                }
                Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
            } catch (_: Exception) {
                rawBitmap
            }
        } else {
            rawBitmap
        }

        cachedCoverTrackPath = trackPath
        if (maxDimension >= 1024) {
            cachedSafeCover1024 = scaled
        } else {
            cachedSafeCover512 = scaled
        }
        return scaled
    }

    fun buildNotification(
        context: Context,
        mediaSession: MediaSessionCompat?,
        track: TrackInfo?,
        isPlaying: Boolean,
        isDac: Boolean
    ): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.yuka.musicplayer.MainActivity::class.java)
        launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val contentPendingIntent = PendingIntent.getActivity(context, 0, launchIntent, flags)

        val prevIntent = Intent(context, AudioForegroundService::class.java).apply { action = AudioForegroundService.ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(context, 1, prevIntent, flags)

        val togglePlayIntent = Intent(context, AudioForegroundService::class.java).apply { action = AudioForegroundService.ACTION_TOGGLE_PLAY }
        val togglePlayPendingIntent = PendingIntent.getService(context, 2, togglePlayIntent, flags)

        val nextIntent = Intent(context, AudioForegroundService::class.java).apply { action = AudioForegroundService.ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(context, 3, nextIntent, flags)

        val stopIntent = Intent(context, AudioForegroundService::class.java).apply { action = AudioForegroundService.ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(context, 4, stopIntent, flags)

        val title = track?.title ?: "tsrossa Audiophile Player"
        val subtitle = if (track != null) {
            val dacLabel = if (isDac) "Bit-Perfect UAC2" else "Audio Ready"
            "${track.artist} • $dacLabel"
        } else {
            if (isDac) "USB DAC Connected" else "Standby"
        }

        val playPauseIcon = if (isPlaying) {
            R.drawable.ic_notif_pause
        } else {
            R.drawable.ic_notif_play
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val scaledCover = getSafeCoverArt(track?.coverArt, track?.file?.absolutePath, maxDimension = 512)

        return NotificationCompat.Builder(context, AudioForegroundService.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_stat_tsrossa)
            .setContentIntent(contentPendingIntent)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_notif_prev, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, togglePlayPendingIntent)
            .addAction(R.drawable.ic_notif_next, "Next", nextPendingIntent)
            .addAction(R.drawable.ic_notif_close, "Close", stopPendingIntent)
            .setLargeIcon(scaledCover)
            .build()
    }
}

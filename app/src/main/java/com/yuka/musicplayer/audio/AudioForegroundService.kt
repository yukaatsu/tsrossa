package com.yuka.musicplayer.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.yuka.musicplayer.R
import kotlinx.coroutines.launch

class AudioForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedByTransientLoss = false

    private var mediaSession: MediaSessionCompat? = null
    private var cachedSeekPosSec: Double? = null
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
    private var positionHeartbeatJob: kotlinx.coroutines.Job? = null
    private var audioFlingerKeepAliveTrack: android.media.AudioTrack? = null

    private fun startAudioFlingerKeepAlive() {
        if (audioFlingerKeepAliveTrack != null) return
        try {
            val sampleRate = 8000
            val numFrames = 4000 // 0.5s buffer
            val bufferSizeBytes = numFrames * 2 // 16-bit mono = 2 bytes/frame
            val silentPcm = ByteArray(bufferSizeBytes)

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                                }
                            }
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.media.AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeBytes,
                    android.media.AudioTrack.MODE_STATIC
                )
            }

            track.write(silentPcm, 0, bufferSizeBytes)
            track.setLoopPoints(0, numFrames, -1) // Infinite static loop in AudioFlinger (0% CPU)
            track.setVolume(0.0f)
            track.play()
            audioFlingerKeepAliveTrack = track
            android.util.Log.i("AudioForegroundService", "AudioFlinger keep-alive track started (PID registered in Active Tracks)")
        } catch (e: Throwable) {
            android.util.Log.w("AudioForegroundService", "Failed to start AudioFlinger keep-alive: ${e.message}")
        }
    }

    private fun stopAudioFlingerKeepAlive() {
        try {
            audioFlingerKeepAliveTrack?.let {
                if (it.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Throwable) {}
        audioFlingerKeepAliveTrack = null
    }

    private fun startPositionHeartbeat() {
        positionHeartbeatJob?.cancel()
        positionHeartbeatJob = serviceScope.launch {
            while (AudioPlayerManager.isPlaying || (AudioPlayerManager.isInitialized && AudioPlayerManager.audioEngine.isPlaying())) {
                syncPlaybackState(isPlaying = true)
                // MediaSession standard: Android's SystemUI media card interpolates position locally using speed=1.0f.
                // 10s heartbeat keeps it calibrated without spamming SystemServer Binder transactions during window transitions.
                kotlinx.coroutines.delay(10000L)
            }
        }
    }

    private fun stopPositionHeartbeat() {
        positionHeartbeatJob?.cancel()
        positionHeartbeatJob = null
    }

    private var cachedCoverTrackPath: String? = null
    private var cachedSafeCover1024: Bitmap? = null
    private var cachedSafeCover512: Bitmap? = null

    private fun getSafeCoverArt(rawBitmap: Bitmap?, trackPath: String?, maxDimension: Int = 1024): Bitmap? {
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

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            cachedSeekPosSec = null
            com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "MediaSession: onPlay received")
            if (!AudioPlayerManager.isPlaying) {
                AudioPlayerManager.requestTogglePlay()
            }
        }

        override fun onPause() {
            com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "MediaSession: onPause received")
            if (AudioPlayerManager.isPlaying) {
                AudioPlayerManager.requestTogglePlay()
            }
        }

        override fun onSkipToNext() {
            cachedSeekPosSec = null
            com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "MediaSession: onSkipToNext received")
            AudioPlayerManager.requestPlayNext()
        }

        override fun onSkipToPrevious() {
            cachedSeekPosSec = null
            com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "MediaSession: onSkipToPrevious received")
            AudioPlayerManager.requestPlayPrev()
        }

        override fun onStop() {
            cachedSeekPosSec = null
            com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "MediaSession: onStop received")
            handleStopRequest()
        }

        override fun onSeekTo(pos: Long) {
            val targetSec = (pos.coerceAtLeast(0L)) / 1000.0
            cachedSeekPosSec = targetSec
            com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "MediaSession: onSeekTo pos=${targetSec}s")
            AudioPlayerManager.requestSeekTo(targetSec)
            syncPlaybackState(AudioPlayerManager.isPlaying, targetSec)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == CUSTOM_ACTION_CLOSE || action == ACTION_STOP) {
                cachedSeekPosSec = null
                com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "MediaSession custom action: $action")
                handleStopRequest()
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Ignore transient ducking (e.g. system gesture ticks, notification chimes).
                android.util.Log.d("AudioForegroundService", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Ignoring to prevent gesture stutter.")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                com.yuka.musicplayer.utils.AppLogger.w("AUDIO", "AudioFocus: AUDIOFOCUS_LOSS_TRANSIENT")
                if (AudioPlayerManager.isPlaying) {
                    AudioPlayerManager.audioEngine.pauseAudio()
                    AudioPlayerManager.isPlaying = false
                    pausedByTransientLoss = true
                    AudioPlayerManager.notifyStateChanged()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                com.yuka.musicplayer.utils.AppLogger.w("AUDIO", "AudioFocus: AUDIOFOCUS_LOSS")
                if (AudioPlayerManager.isPlaying) {
                    AudioPlayerManager.audioEngine.pauseAudio()
                    AudioPlayerManager.isPlaying = false
                    pausedByTransientLoss = false
                    AudioPlayerManager.notifyStateChanged()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                com.yuka.musicplayer.utils.AppLogger.i("AUDIO", "AudioFocus: AUDIOFOCUS_GAIN (pausedByTransient=$pausedByTransientLoss)")
                if (pausedByTransientLoss) {
                    pausedByTransientLoss = false
                    val resumed = AudioPlayerManager.audioEngine.resumeAudio()
                    if (resumed) {
                        AudioPlayerManager.isPlaying = true
                        AudioPlayerManager.notifyStateChanged()
                    }
                }
            }
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
            }
            am.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Throwable) {}

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer::AudioIsochronousLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L /*10 hours max*/)

        // Initialize MediaSessionCompat for Android 11–15 / One UI rich media card
        mediaSession = MediaSessionCompat(this, "tsrossaMediaSession").apply {
            setCallback(mediaSessionCallback)
            isActive = true
        }

        // Listen for player state changes to keep the notification in sync
        AudioPlayerManager.onNotificationUpdateRequired = {
            updateNotification()
        }

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun syncMediaMetadata() {
        val session = mediaSession ?: return
        val track = AudioPlayerManager.currentTrack
        val isDac = AudioPlayerManager.isDacConnected

        val title = track?.title ?: "tsrossa Audiophile Player"
        val artist = if (track != null) {
            val dacLabel = if (isDac) "Bit-Perfect UAC2" else "Audio Ready"
            "${track.artist} • $dacLabel"
        } else {
            if (isDac) "USB DAC Connected" else "Standby"
        }
        val album = track?.album ?: "tsrossa"
        val durationMs = if (track != null && track.durationSeconds > 0) {
            (track.durationSeconds * 1000.0).toLong()
        } else {
            -1L
        }

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

        getSafeCoverArt(track?.coverArt, track?.file?.absolutePath, maxDimension = 1024)?.let { safeArt ->
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, safeArt)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, safeArt)
        }

        session.setMetadata(metaBuilder.build())
    }

    private fun syncPlaybackState(isPlaying: Boolean, explicitPosSec: Double? = null) {
        val session = mediaSession ?: return
        val posSec = explicitPosSec ?: if (!isPlaying && cachedSeekPosSec != null) {
            cachedSeekPosSec!!
        } else if (AudioPlayerManager.isInitialized) {
            try {
                AudioPlayerManager.audioEngine.getPosition()
            } catch (_: Exception) {
                0.0
            }
        } else {
            0.0
        }
        val posMs = (posSec.coerceAtLeast(0.0) * 1000.0).toLong()

        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        val speed = if (isPlaying) 1.0f else 0.0f

        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, posMs, speed)
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_CLOSE,
                    "Close",
                    R.drawable.ic_notif_close
                ).build()
            )

        session.setPlaybackState(stateBuilder.build())
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
            R.drawable.ic_notif_pause
        } else {
            R.drawable.ic_notif_play
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        // Sync MediaSession metadata & state for Android 11–15 System Media Player
        syncMediaMetadata()
        syncPlaybackState(isPlaying)

        // Native Android / One UI Rich MediaStyle attached to session token
        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val scaledCover = getSafeCoverArt(track?.coverArt, track?.file?.absolutePath, maxDimension = 512)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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

        return builder.build()
    }

    private var lastNotificationTime = 0L
    private var lastNotificationTrackTitle: String? = null
    private var lastNotificationIsPlaying: Boolean? = null

    private fun updateNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val track = AudioPlayerManager.currentTrack
        val isPlaying = AudioPlayerManager.isPlaying ||
            (AudioPlayerManager.isInitialized && AudioPlayerManager.audioEngine.isPlaying())

        val trackChanged = (track?.title != lastNotificationTrackTitle)
        if (trackChanged) {
            cachedSeekPosSec = null
        }

        if (isPlaying) {
            startPositionHeartbeat()
        } else {
            stopPositionHeartbeat()
        }

        if (!force && !trackChanged && now - lastNotificationTime < 300L) {
            if (isPlaying == lastNotificationIsPlaying) {
                return
            }
        }
        lastNotificationTime = now
        lastNotificationTrackTitle = track?.title
        lastNotificationIsPlaying = isPlaying

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun handleStopRequest() {
        stopPositionHeartbeat()
        stopAudioFlingerKeepAlive()
        abandonAudioFocus()
        AudioPlayerManager.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
    
    override fun onDestroy() {
        stopPositionHeartbeat()
        stopAudioFlingerKeepAlive()
        AudioPlayerManager.onNotificationUpdateRequired = null
        abandonAudioFocus()
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val act = intent?.action
        if (act != null) {
            com.yuka.musicplayer.utils.AppLogger.i("PLAYBACK", "Service onStartCommand action: $act")
        }
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
                requestAudioFocus()
                startAudioFlingerKeepAlive()
                if (wakeLock?.isHeld != true) wakeLock?.acquire(10 * 60 * 60 * 1000L)
                updateNotification()
            }
            ACTION_PAUSE -> {
                stopAudioFlingerKeepAlive()
                if (wakeLock?.isHeld == true) wakeLock?.release()
                updateNotification()
            }
            ACTION_UPDATE -> {
                if (AudioPlayerManager.isPlaying) {
                    requestAudioFocus()
                    startAudioFlingerKeepAlive()
                } else {
                    stopAudioFlingerKeepAlive()
                }
                updateNotification()
            }
            ACTION_STOP -> {
                handleStopRequest()
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
            handleStopRequest()
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
        const val CUSTOM_ACTION_CLOSE = "ACTION_CLOSE"
        
        const val ACTION_PREV = "com.yuka.musicplayer.ACTION_PREV"
        const val ACTION_TOGGLE_PLAY = "com.yuka.musicplayer.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.yuka.musicplayer.ACTION_NEXT"
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
    }
}

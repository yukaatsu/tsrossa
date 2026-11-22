package com.yuka.musicplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yuka.musicplayer.audio.AudioPlayerManager
import com.yuka.musicplayer.ui.app.KewApp
import com.yuka.musicplayer.ui.theme.KewMobileTheme
import com.yuka.musicplayer.util.AlbumArtThumbnailCache

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Prevent launcher bug where opening app creates a duplicate MainActivity instance
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.action != null && intent.action == Intent.ACTION_MAIN) {
            finish()
            return
        }

        com.yuka.musicplayer.core.migration.CleanInstallMigrationHelper.ensureCleanInstallOnUpdate(applicationContext)
        AudioPlayerManager.initialize(applicationContext)
        AlbumArtThumbnailCache.init(applicationContext)

        setContent {
            KewMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    KewApp(AudioPlayerManager.audioEngine)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            android.util.Log.i("MainActivity", "onNewIntent: USB device attached event. Scanning DAC...")
            AudioPlayerManager.usbAudioController.scanAndRequestPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (AudioPlayerManager.isInitialized && !AudioPlayerManager.isDacConnected) {
            AudioPlayerManager.usbAudioController.scanAndRequestPermission()
        }
    }

    override fun onTrimMemory(level: Int) {
        // Prevent aggressive UI cache eviction / GC pause from starving native USB audio thread
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            val isAudioActive = AudioPlayerManager.isPlaying ||
                (AudioPlayerManager.isInitialized && AudioPlayerManager.audioEngine.isPlaying())
            if (isAudioActive) {
                return
            }
        }
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val hasActiveTrack = AudioPlayerManager.currentTrack != null
            val isStillPlaying = AudioPlayerManager.isPlaying ||
                (AudioPlayerManager.isInitialized && AudioPlayerManager.audioEngine.isPlaying())
            if (!isStillPlaying && !hasActiveTrack) {
                android.util.Log.i("MainActivity", "onDestroy: Activity is finishing and no track active. Stopping AudioForegroundService.")
                val stopIntent = Intent(this, com.yuka.musicplayer.audio.AudioForegroundService::class.java).apply {
                    action = com.yuka.musicplayer.audio.AudioForegroundService.ACTION_STOP
                }
                startService(stopIntent)
            }
        }
    }
}

package com.yuka.musicplayer.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yuka.musicplayer.audio.AudioEngine
import com.yuka.musicplayer.model.TrackInfo
import kotlinx.coroutines.delay
import kotlin.reflect.KProperty

@Stable
class PlaybackPositionHolder(initialSeconds: Double = 0.0) {
    var positionSeconds by mutableDoubleStateOf(initialSeconds)
        private set

    fun updateFromPolling(seconds: Double) {
        if (seconds == 0.0 || kotlin.math.abs(seconds - positionSeconds) >= 0.05) {
            positionSeconds = seconds
        }
    }

    fun forceSet(seconds: Double) {
        positionSeconds = seconds
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Double {
        return positionSeconds
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
        forceSet(value)
    }
}

@Composable
fun rememberPlaybackPositionHolder(
    audioEngine: AudioEngine,
    currentTrack: TrackInfo?,
    isPlaying: Boolean
): PlaybackPositionHolder {
    val holder = remember { PlaybackPositionHolder(0.0) }
    LaunchedEffect(currentTrack, isPlaying) {
        while (isPlaying && currentTrack != null) {
            holder.updateFromPolling(audioEngine.getPosition())
            delay(200)
        }
    }
    return holder
}

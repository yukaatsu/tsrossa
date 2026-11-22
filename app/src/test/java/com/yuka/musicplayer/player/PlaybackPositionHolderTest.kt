package com.yuka.musicplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionHolderTest {

    @Test
    fun updateFromPolling_throttlesMicroJitterButAlwaysAllowsZeroResetAndForceSeek() {
        val holder = PlaybackPositionHolder()
        assertEquals(0.0, holder.positionSeconds, 0.0001)

        // Normal advance >= 0.05s updates position
        holder.updateFromPolling(0.20)
        assertEquals(0.20, holder.positionSeconds, 0.0001)

        // Micro jitter < 0.05s is ignored to prevent redundant recomposition
        holder.updateFromPolling(0.22)
        assertEquals(0.20, holder.positionSeconds, 0.0001)

        // Reset to 0.0 (e.g. track restart from 0.03s) MUST always succeed even if delta < 0.05s
        holder.forceSet(0.03)
        holder.updateFromPolling(0.0)
        assertEquals(0.0, holder.positionSeconds, 0.0001)
    }
}

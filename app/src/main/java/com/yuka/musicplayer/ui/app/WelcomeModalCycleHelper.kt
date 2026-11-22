package com.yuka.musicplayer.ui.app

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yuka.musicplayer.audio.AudioPlayerManager
import com.yuka.musicplayer.model.TrackInfo
import com.yuka.musicplayer.update.UpdateCheckState
import kotlin.random.Random

fun pickNextRandomModal(prefs: SharedPreferences): String {
    val lastType = prefs.getString("modal_cycle_last_type", "") ?: ""
    val streak = prefs.getInt("modal_cycle_streak_count", 0)
    val chosen = if (streak >= 2 && (lastType == "DONATION" || lastType == "DISCORD")) {
        if (lastType == "DONATION") "DISCORD" else "DONATION"
    } else {
        if (Random.nextBoolean()) "DONATION" else "DISCORD"
    }
    val newStreak = if (chosen == lastType) streak + 1 else 1
    prefs.edit()
        .putString("modal_cycle_last_type", chosen)
        .putInt("modal_cycle_streak_count", newStreak)
        .apply()
    return chosen
}

fun advanceReopenModalCycle(
    prefs: SharedPreferences,
    onActiveModalSelected: (String) -> Unit = {}
) {
    val seenDonation = prefs.getBoolean("has_seen_welcome_donation_modal", false)
    val seenDiscord = prefs.getBoolean("has_seen_welcome_discord_modal", false)
    if (seenDonation && seenDiscord) {
        val currentCount = prefs.getInt("modal_cycle_reopen_count", 0) + 1
        val target = prefs.getInt("modal_cycle_target_reopen", 0).let {
            if (it in 2..4) it else (2..4).random().also { r ->
                prefs.edit().putInt("modal_cycle_target_reopen", r).apply()
            }
        }
        prefs.edit().putInt("modal_cycle_reopen_count", currentCount).apply()
        if (currentCount >= target) {
            onActiveModalSelected(pickNextRandomModal(prefs))
        }
    } else {
        if (!prefs.contains("modal_cycle_target_reopen")) {
            prefs.edit().putInt("modal_cycle_target_reopen", (2..4).random()).apply()
        }
    }
}

@Stable
class WelcomeModalCycleState(
    initialShowDonation: Boolean = false,
    initialShowDiscord: Boolean = false,
    initialShowUpdate: Boolean = false
) {
    var showDonationModal by mutableStateOf(initialShowDonation)
    var showDiscordModal by mutableStateOf(initialShowDiscord)
    var showUpdatePanel by mutableStateOf(initialShowUpdate)

    var onDismissDonationModal: () -> Unit = { showDonationModal = false }
    var onDismissDiscordModal: () -> Unit = { showDiscordModal = false }
    var onDismissUpdatePanel: () -> Unit = { showUpdatePanel = false }
}

@Composable
fun rememberWelcomeModalCycleState(
    sharedPref: SharedPreferences,
    isStorageGranted: Boolean,
    isMusicDirectoryConfigured: Boolean,
    showPermissionDialog: Boolean,
    showFolderPickerDialog: Boolean,
    isPlaying: Boolean,
    currentTrack: TrackInfo?,
    updateCheckState: UpdateCheckState
): WelcomeModalCycleState {
    val state = remember { WelcomeModalCycleState() }

    var hasSeenWelcomeDonation by remember {
        mutableStateOf(sharedPref.getBoolean("has_seen_welcome_donation_modal", false))
    }
    var hasSeenWelcomeDiscord by remember {
        mutableStateOf(sharedPref.getBoolean("has_seen_welcome_discord_modal", false))
    }
    val firstInstallOrderDonationFirst = remember { Random.nextBoolean() }
    var reopenActiveModalType by remember { mutableStateOf<String?>(null) }
    var hasAutoShownUpdateModal by remember { mutableStateOf(false) }

    state.onDismissDonationModal = {
        state.showDonationModal = false
        hasSeenWelcomeDonation = true
        if (reopenActiveModalType == "DONATION") {
            reopenActiveModalType = null
        }
        val nextTarget = (2..4).random()
        sharedPref.edit()
            .putBoolean("has_seen_welcome_donation_modal", true)
            .putInt("modal_cycle_reopen_count", 0)
            .putInt("modal_cycle_target_reopen", nextTarget)
            .apply()
    }

    state.onDismissDiscordModal = {
        state.showDiscordModal = false
        hasSeenWelcomeDiscord = true
        if (reopenActiveModalType == "DISCORD") {
            reopenActiveModalType = null
        }
        val nextDiscordTarget = (2..4).random()
        sharedPref.edit()
            .putBoolean("has_seen_welcome_discord_modal", true)
            .putInt("modal_cycle_reopen_count", 0)
            .putInt("modal_cycle_target_reopen", nextDiscordTarget)
            .apply()
    }

    state.onDismissUpdatePanel = {
        state.showUpdatePanel = false
    }

    // Cold Start modal cycle check - only runs once per app process launch!
    LaunchedEffect(Unit) {
        if (!AudioPlayerManager.hasEvaluatedStartupModalSession) {
            AudioPlayerManager.hasEvaluatedStartupModalSession = true
            advanceReopenModalCycle(sharedPref) { selected ->
                if (reopenActiveModalType == null) {
                    reopenActiveModalType = selected
                }
            }
        }
    }

    // Unified First-Install Dual Welcome + Randomized Reopen Modal Orchestrator
    LaunchedEffect(
        isStorageGranted,
        isMusicDirectoryConfigured,
        showPermissionDialog,
        showFolderPickerDialog,
        state.showDonationModal,
        state.showDiscordModal,
        hasSeenWelcomeDonation,
        hasSeenWelcomeDiscord,
        reopenActiveModalType,
        isPlaying,
        currentTrack
    ) {
        if (!isStorageGranted || !isMusicDirectoryConfigured || showPermissionDialog || showFolderPickerDialog) {
            return@LaunchedEffect
        }
        if (state.showDonationModal || state.showDiscordModal) {
            return@LaunchedEffect
        }
        if (isPlaying || AudioPlayerManager.isPlaying || currentTrack != null) {
            reopenActiveModalType = null
            return@LaunchedEffect
        }

        // 1. FASE FIRST INSTALL: Pastikan KEDUA modal muncul bergantian
        if (!hasSeenWelcomeDonation || !hasSeenWelcomeDiscord) {
            if (!hasSeenWelcomeDonation && !hasSeenWelcomeDiscord) {
                if (firstInstallOrderDonationFirst) {
                    state.showDonationModal = true
                } else {
                    state.showDiscordModal = true
                }
            } else if (!hasSeenWelcomeDonation) {
                state.showDonationModal = true
            } else {
                state.showDiscordModal = true
            }
            return@LaunchedEffect
        }

        // 2. FASE REOPEN CYCLE
        when (reopenActiveModalType) {
            "DONATION" -> state.showDonationModal = true
            "DISCORD" -> state.showDiscordModal = true
        }
    }

    // Auto Update Notification Modal
    LaunchedEffect(updateCheckState, showPermissionDialog, showFolderPickerDialog, state.showDonationModal, state.showDiscordModal) {
        if (updateCheckState is UpdateCheckState.UpdateAvailable &&
            !hasAutoShownUpdateModal &&
            !showPermissionDialog &&
            !showFolderPickerDialog &&
            !state.showDonationModal &&
            !state.showDiscordModal
        ) {
            hasAutoShownUpdateModal = true
            state.showUpdatePanel = true
        }
    }

    return state
}

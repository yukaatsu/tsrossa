package com.yuka.musicplayer.util

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Context.findComponentActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Stable
class ScreenPinningState(
    val isTaskLocked: Boolean,
    val toggleLockTask: () -> Unit
)

@Composable
fun rememberScreenPinningState(): ScreenPinningState {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findComponentActivity() }
    val activityManager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }

    fun checkIsTaskLocked(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            false
        }
    }

    var isTaskLocked by remember { mutableStateOf(checkIsTaskLocked()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isTaskLocked = checkIsTaskLocked()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val toggleLockTask: () -> Unit = remember(activity, activityManager) {
        {
            activity?.let { act ->
                try {
                    if (checkIsTaskLocked()) {
                        act.stopLockTask()
                        isTaskLocked = false
                        Toast.makeText(act, "APP LOCK DEACTIVATED", Toast.LENGTH_SHORT).show()
                    } else {
                        act.startLockTask()
                        coroutineScope.launch {
                            delay(600)
                            val locked = checkIsTaskLocked()
                            isTaskLocked = locked
                            if (locked) {
                                Toast.makeText(act, "APP LOCKED (SCREEN PINNED)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(act, "Enable 'App Pinning' in Android Security Settings first", Toast.LENGTH_LONG).show()
                                try {
                                    act.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(act, "Lock task error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    return remember(isTaskLocked, toggleLockTask) {
        ScreenPinningState(
            isTaskLocked = isTaskLocked,
            toggleLockTask = toggleLockTask
        )
    }
}

package app.cue.launcher.system

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import android.service.notification.Condition
import android.util.Log
import app.cue.launcher.data.scenario.RingerModeState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@SingleIn(AppScope::class)
@Inject
class SystemActions(private val application: Application) {

    // Every call here crosses a binder, and the tap that starts it comes from the composition, so
    // none of it belongs on the main thread.
    suspend fun setRingerMode(mode: RingerModeState): ActionOutcome = withContext(Dispatchers.Default) {
        val audio = application.getSystemService(AudioManager::class.java)
        val notifications = application.getSystemService(NotificationManager::class.java)
        val value = ringerModeValue(mode)
        if (audio == null || notifications == null || value == null) return@withContext ActionOutcome.FAILED
        policyGuarded("ringer mode $mode") {
            // A ringer that rings or vibrates ends Do Not Disturb, but the system does it by
            // snoozing the active modes, and a snooze is not persisted: the launcher's own mode,
            // still on underneath, would come back with the next reboot. It is removed first.
            if (mode != RingerModeState.SILENT &&
                notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            ) {
                removeOwnModes(notifications, keepActive = false)
            }
            audio.ringerMode = value
            ActionOutcome.DONE
        }
    }

    // Since Android 15 an app cannot move the global interruption filter: the system routes the call
    // into a mode it keeps on the app's behalf, and that mode is the real Do Not Disturb. The mode
    // lives only while it is on: switching off deletes it, and switching on lets the system create
    // it anew, which copies the system Do Not Disturb settings of that moment into it.
    suspend fun setDoNotDisturb(enabled: Boolean): ActionOutcome = withContext(Dispatchers.Default) {
        val notifications = application.getSystemService(NotificationManager::class.java)
            ?: return@withContext ActionOutcome.FAILED
        policyGuarded("Do Not Disturb $enabled") {
            if (enabled) {
                // One switched off from the shade is still there, holding an old copy.
                removeOwnModes(notifications, keepActive = true)
                notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                removeOwnModes(notifications, keepActive = false)
            }
            when {
                // Switching off cannot be read back the same way: somebody else's mode may hold Do
                // Not Disturb open, and that is not this call failing.
                !enabled -> ActionOutcome.DONE
                notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL ->
                    ActionOutcome.DONE

                else -> {
                    Log.w(TAG, "Do Not Disturb stayed off — the system refused the mode")
                    ActionOutcome.FAILED
                }
            }
        }
    }

    // Whatever ends the mode outside the launcher — the shade, the volume keys, another app moving
    // the ringer — leaves it behind switched off or only snoozed, and a snoozed one comes back on
    // with the next reboot.
    suspend fun removeInactiveModes() = withContext(Dispatchers.Default) {
        val notifications = application.getSystemService(NotificationManager::class.java)
            ?: return@withContext
        try {
            removeOwnModes(notifications, keepActive = true)
        } catch (denied: SecurityException) {
            Log.w(TAG, "The launcher's Do Not Disturb mode cannot be removed", denied)
        }
    }

    // getAutomaticZenRules() returns only the caller's own modes, and the launcher never adds one of
    // its own — whatever is there is the mode the system keeps for it.
    private fun removeOwnModes(notifications: NotificationManager, keepActive: Boolean) {
        notifications.automaticZenRules.keys
            .filterNot { keepActive && notifications.getAutomaticZenRuleState(it) == Condition.STATE_TRUE }
            .forEach(notifications::removeAutomaticZenRule)
    }

    suspend fun toggleTorch(): ActionOutcome = withContext(Dispatchers.Default) {
        val manager = application.getSystemService(CameraManager::class.java)
            ?: return@withContext ActionOutcome.TARGET_MISSING
        val cameraId = try {
            torchCameraId(torchCamerasOf(manager))
        } catch (unavailable: CameraAccessException) {
            Log.w(TAG, "Cameras cannot be enumerated", unavailable)
            null
        } ?: return@withContext ActionOutcome.TARGET_MISSING

        val enabled = torchEnabled(manager, cameraId) ?: false
        try {
            manager.setTorchMode(cameraId, !enabled)
            ActionOutcome.DONE
        } catch (busy: CameraAccessException) {

            Log.w(TAG, "The flashlight cannot be toggled right now", busy)
            ActionOutcome.FAILED
        } catch (gone: IllegalArgumentException) {
            Log.w(TAG, "Camera $cameraId disappeared", gone)
            ActionOutcome.TARGET_MISSING
        }
    }

    private fun torchCamerasOf(manager: CameraManager): List<TorchCamera> =
        manager.cameraIdList.map { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            TorchCamera(
                id = id,
                hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                facingBack = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK,
            )
        }

    private suspend fun torchEnabled(manager: CameraManager, cameraId: String): Boolean? =
        withTimeoutOrNull(TORCH_STATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                lateinit var callback: CameraManager.TorchCallback
                callback = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) answer(continuation, manager, callback, enabled)
                    }

                    override fun onTorchModeUnavailable(id: String) {
                        if (id == cameraId) answer(continuation, manager, callback, answer = null)
                    }
                }
                manager.registerTorchCallback(application.mainExecutor, callback)

                continuation.invokeOnCancellation { manager.unregisterTorchCallback(callback) }
            }
        }

    private fun answer(
        continuation: CancellableContinuation<Boolean?>,
        manager: CameraManager,
        callback: CameraManager.TorchCallback,
        answer: Boolean?,
    ) {
        manager.unregisterTorchCallback(callback)
        if (continuation.isActive) continuation.resume(answer)
    }

    private inline fun policyGuarded(what: String, change: () -> ActionOutcome): ActionOutcome = try {
        change()
    } catch (denied: SecurityException) {
        Log.w(TAG, "\"$what\" needs Do Not Disturb access", denied)
        ActionOutcome.PERMISSION_REQUIRED
    }

    private companion object {
        const val TAG = "SystemActions"

        const val TORCH_STATE_TIMEOUT_MS = 500L
    }
}

data class TorchCamera(val id: String, val hasFlash: Boolean, val facingBack: Boolean)

internal fun torchCameraId(cameras: List<TorchCamera>): String? {
    val withFlash = cameras.filter { it.hasFlash }
    return (withFlash.firstOrNull { it.facingBack } ?: withFlash.firstOrNull())?.id
}

internal fun ringerModeValue(mode: RingerModeState): Int? = when (mode) {
    RingerModeState.SILENT -> AudioManager.RINGER_MODE_SILENT
    RingerModeState.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
    RingerModeState.NORMAL -> AudioManager.RINGER_MODE_NORMAL
    RingerModeState.DND -> null
}

internal fun systemScreenIntent(screen: SystemScreenTarget): Intent = Intent(
    when (screen) {
        SystemScreenTarget.VOLUME_PANEL -> Settings.Panel.ACTION_VOLUME
        SystemScreenTarget.INTERNET_PANEL -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        SystemScreenTarget.WIFI_PANEL -> Settings.Panel.ACTION_WIFI
        SystemScreenTarget.NFC_PANEL -> Settings.Panel.ACTION_NFC
        SystemScreenTarget.SETTINGS -> Settings.ACTION_SETTINGS
    },
)

fun notificationPolicyAccessGranted(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

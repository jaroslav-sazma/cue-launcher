package app.cue.launcher.system

import android.app.Application
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import app.cue.launcher.engine.StateAspect
import app.cue.launcher.engine.StateProvider
import app.cue.launcher.engine.SystemState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SingleIn(AppScope::class)
@Inject
class AudioConditions(private val application: Application) : StateProvider {

    override val aspect: StateAspect = StateAspect.AUDIO

    override fun observe(): Flow<SystemState> = callbackFlow {
        val audio = application.getSystemService(AudioManager::class.java)
        if (audio == null) {

            send(SystemState())
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(measure(audio))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(measure(audio))
            }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        send(measure(audio))
        awaitClose { audio.unregisterAudioDeviceCallback(callback) }
    }

    private fun measure(audio: AudioManager): SystemState {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return SystemState(audioOutput = outputs.any { isExternalAudioOutput(it.type) })
    }
}

internal fun isExternalAudioOutput(type: Int): Boolean = type in EXTERNAL_OUTPUT_TYPES

private val EXTERNAL_OUTPUT_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_DOCK,
    AudioDeviceInfo.TYPE_DOCK_ANALOG,
    AudioDeviceInfo.TYPE_HEARING_AID,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
)

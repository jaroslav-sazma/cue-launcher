package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.EndEvent
import app.cue.launcher.data.scenario.Scenario

// An end event matches exactly as the condition of the same type would start a scenario.
fun endEventMatches(until: EndEvent.Notification, notification: PostedNotification): Boolean {
    val condition = Condition.NotificationFrom(packageName = until.packageName, contains = until.contains)
    return notificationMatches(condition, notification)
}

fun endEventMatches(until: EndEvent.BtDisconnected, mac: String): Boolean =
    btDisconnectedMatches(Condition.BtDisconnected(mac = until.mac), mac)

fun endEventMatches(until: EndEvent.HaEvent, code: String): Boolean =
    haEventMatches(Condition.HaEvent(code = until.code), code)

fun endEventCanArrive(until: EndEvent): Boolean = when (until) {
    is EndEvent.Notification -> until.packageName.isNotBlank()
    is EndEvent.HaEvent -> haCodeKey(until.code).isNotEmpty()
    is EndEvent.AtTime, EndEvent.Alarm, is EndEvent.BtDisconnected -> true
}

// An end is not gated by the state conditions, just as a dismissed notification ends its tile.
fun endedByNotification(activations: List<Activation>, notification: PostedNotification): List<Activation> =
    endedBy(activations) { it is EndEvent.Notification && endEventMatches(it, notification) }

fun endedByBluetoothDisconnect(activations: List<Activation>, mac: String): List<Activation> =
    endedBy(activations) { it is EndEvent.BtDisconnected && endEventMatches(it, mac) }

fun endedByHaEvent(activations: List<Activation>, code: String): List<Activation> =
    endedBy(activations) { it is EndEvent.HaEvent && endEventMatches(it, code) }

// A ring noticed late — after a reboot — must not end what fired after it.
fun endedByAlarm(activations: List<Activation>, rangAt: Long): List<Activation> =
    endedBy(activations.filter { it.lastFiredAt < rangAt }) { it is EndEvent.Alarm }

fun Scenario.endsBy(matches: (EndEvent) -> Boolean): Boolean =
    enabled && instantConditions.any { condition -> condition.until?.let(matches) == true }

val Activation.notificationEnd: EndEvent.Notification?
    get() = payload.until as? EndEvent.Notification

val Activation.lastFiredAt: Long
    get() = payload.firedAt ?: since

private fun endedBy(activations: List<Activation>, matches: (EndEvent) -> Boolean): List<Activation> =
    activations.filter { it.payload.until?.let(matches) == true }

package app.cue.launcher.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NextAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) return
        deliverAsync(context) { graph -> graph.alarmMonitor.onNextAlarm { nextAlarmMillis(context) } }
    }
}

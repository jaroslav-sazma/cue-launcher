package app.cue.launcher.system.notifications

import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification

// The bridge's own feed of the shade, apart from the scenario index. Called on the listener's
// thread, so an implementation only queues what it is handed.
interface NotificationMirrorSink {

    fun posted(sbn: StatusBarNotification, rankings: RankingMap?)

    fun removed(sbn: StatusBarNotification)
}

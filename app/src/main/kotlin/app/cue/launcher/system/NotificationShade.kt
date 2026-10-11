package app.cue.launcher.system

import android.content.Context
import android.util.Log

// expandNotificationsPanel is a hidden StatusBarManager API with no SDK replacement, reached via
// reflection. It sits on the non-SDK blocklist's mercy: if a future build closes it, invoke()
// throws, expand() returns false and callers already degrade to doing nothing.
object NotificationShade {

    private const val TAG = "NotificationShade"

    private const val STATUS_BAR_SERVICE = "statusbar"
    private const val STATUS_BAR_CLASS = "android.app.StatusBarManager"
    private const val EXPAND_METHOD = "expandNotificationsPanel"

    fun expand(context: Context): Boolean {
        val service = context.getSystemService(STATUS_BAR_SERVICE) ?: return false
        return try {
            Class.forName(STATUS_BAR_CLASS).getMethod(EXPAND_METHOD).invoke(service)
            true
        } catch (denied: ReflectiveOperationException) {
            Log.w(TAG, "The system refused $STATUS_BAR_CLASS#$EXPAND_METHOD", denied)
            false
        } catch (denied: SecurityException) {
            Log.w(TAG, "Missing permission for $STATUS_BAR_CLASS#$EXPAND_METHOD", denied)
            false
        }
    }
}

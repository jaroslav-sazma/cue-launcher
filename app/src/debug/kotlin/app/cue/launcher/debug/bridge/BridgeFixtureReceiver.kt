package app.cue.launcher.debug.bridge

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cue.launcher.system.bridge.BridgeLog

// Debug builds only, and only the shell may send it (see the debug manifest):
// adb -e shell am broadcast -a app.cue.launcher.debug.BRIDGE_FIXTURE --es fixture plain \
//   -n app.cue.launcher.debug/app.cue.launcher.debug.bridge.BridgeFixtureReceiver
class BridgeFixtureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val fixture = BridgeFixture.of(intent.getStringExtra(EXTRA_FIXTURE))
        when {
            intent.action != ACTION_FIXTURE || fixture == null ->
                BridgeLog.info("fixture broadcast ignored: expected $ACTION_FIXTURE with a known $EXTRA_FIXTURE")
            fixture != BridgeFixture.CANCEL && !FixtureNotifications.allowed(context) ->
                BridgeLog.info("fixture ${fixture.wire} not posted: POST_NOTIFICATIONS is not granted")
            else -> FixtureNotifications.post(context, fixture)
        }
    }

    private companion object {
        const val ACTION_FIXTURE = "app.cue.launcher.debug.BRIDGE_FIXTURE"
        const val EXTRA_FIXTURE = "fixture"
    }
}

// Only the reply's length is logged, for a test to check against; the text never is.
class BridgeFixtureReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(FixtureNotifications.FIXTURE_REPLY_KEY)
        val length = reply?.length ?: 0
        BridgeLog.info("fixture reply received, length=$length")
        if (FixtureNotifications.allowed(context)) FixtureNotifications.replied(context, length)
    }
}

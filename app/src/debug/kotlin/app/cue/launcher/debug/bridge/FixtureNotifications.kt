package app.cue.launcher.debug.bridge

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import androidx.annotation.StringRes
import app.cue.launcher.R
import app.cue.launcher.data.bridge.BRIDGE_FIXTURE_CHANNEL_PREFIX
import app.cue.launcher.system.bridge.BridgeLog
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal enum class BridgeFixture(val wire: String, @param:StringRes val label: Int) {
    PLAIN("plain", R.string.bridge_fixture_plain),
    UPDATING("updating", R.string.bridge_fixture_updating),
    GROUPED("grouped", R.string.bridge_fixture_grouped),
    REPLY("reply", R.string.bridge_fixture_reply),
    PROGRESS("progress", R.string.bridge_fixture_progress),
    LOW("low", R.string.bridge_fixture_low),
    PRIVATE("private", R.string.bridge_fixture_private),
    SECRET("secret", R.string.bridge_fixture_secret),
    CANCEL("cancel", R.string.bridge_fixture_cancel),
    ;

    companion object {
        fun of(wire: String?): BridgeFixture? = entries.firstOrNull { it.wire == wire }
    }
}

// Debug builds only. Every fixture has a fixed id, so posting it again updates it in place. The
// channels carry the prefix the mirror filter exempts from the own-package rule in debug builds.
internal object FixtureNotifications {

    fun allowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, fixture: BridgeFixture) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (fixture == BridgeFixture.CANCEL) {
            manager.cancelAll()
            BridgeLog.info("fixtures cancelled")
            return
        }
        createChannels(context, manager)
        when (fixture) {
            BridgeFixture.PLAIN -> manager.notify(PLAIN_ID, plain(context))
            BridgeFixture.UPDATING -> manager.notify(UPDATING_ID, updating(context))
            BridgeFixture.GROUPED -> grouped(context).forEach { (id, notification) -> manager.notify(id, notification) }
            BridgeFixture.REPLY -> manager.notify(REPLY_ID, reply(context, null))
            BridgeFixture.PROGRESS -> manager.notify(PROGRESS_ID, progress(context))
            BridgeFixture.LOW -> manager.notify(LOW_ID, low(context))
            BridgeFixture.PRIVATE -> manager.notify(PRIVATE_ID, private(context))
            BridgeFixture.SECRET -> manager.notify(SECRET_ID, secret(context))
            BridgeFixture.CANCEL -> Unit
        }
        BridgeLog.info("fixture ${fixture.wire} posted")
    }

    // The update a messaging app makes after a reply; it also ends the spinner of the reply field.
    fun replied(context: Context, length: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannels(context, manager)
        manager.notify(REPLY_ID, reply(context, length))
    }

    private fun createChannels(context: Context, manager: NotificationManager) {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    DEFAULT_CHANNEL,
                    context.getString(R.string.bridge_fixture_channel_default),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    LOW_CHANNEL,
                    context.getString(R.string.bridge_fixture_channel_low),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    private fun builder(context: Context, channel: String, title: String, text: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)

    private fun plain(context: Context): Notification = builder(
        context,
        DEFAULT_CHANNEL,
        context.getString(R.string.bridge_fixture_plain_title),
        context.getString(R.string.bridge_fixture_plain_text),
    ).build()

    private fun updating(context: Context): Notification = builder(
        context,
        DEFAULT_CHANNEL,
        context.getString(R.string.bridge_fixture_updating_title),
        context.getString(
            R.string.bridge_fixture_updating_text,
            LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        ),
    ).build()

    private fun grouped(context: Context): List<Pair<Int, Notification>> {
        val children = (1..GROUP_CHILDREN).map { index ->
            GROUP_FIRST_ID + index - 1 to builder(
                context,
                DEFAULT_CHANNEL,
                context.getString(R.string.bridge_fixture_grouped_title, index),
                context.getString(R.string.bridge_fixture_grouped_text, index, GROUP_CHILDREN),
            ).setGroup(GROUP_KEY).build()
        }
        val summary = builder(
            context,
            DEFAULT_CHANNEL,
            context.getString(R.string.bridge_fixture_grouped_summary),
            context.getString(R.string.bridge_fixture_grouped_summary_text),
        ).setGroup(GROUP_KEY).setGroupSummary(true).build()
        return children + (GROUP_SUMMARY_ID to summary)
    }

    // The PendingIntent has to be mutable for the system to fill the RemoteInput in; it names its
    // receiver, so nothing else can catch it.
    private fun reply(context: Context, repliedLength: Int?): Notification {
        val label = context.getString(R.string.bridge_fixture_reply_action)
        val input = RemoteInput.Builder(FIXTURE_REPLY_KEY).setLabel(label).build()
        val intent = PendingIntent.getBroadcast(
            context,
            REPLY_ID,
            Intent(context, BridgeFixtureReplyReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(context, android.R.drawable.ic_menu_send),
            label,
            intent,
        ).addRemoteInput(input).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).build()
        val text = if (repliedLength == null) {
            context.getString(R.string.bridge_fixture_reply_text)
        } else {
            context.getString(R.string.bridge_fixture_reply_received, repliedLength)
        }
        return builder(context, DEFAULT_CHANNEL, context.getString(R.string.bridge_fixture_reply_title), text)
            .setShortcutId(REPLY_SHORTCUT)
            .addAction(action)
            .build()
    }

    // Promotion to a Live Update is the system's call; asking for it is as far as an app can go.
    private fun progress(context: Context): Notification = builder(
        context,
        DEFAULT_CHANNEL,
        context.getString(R.string.bridge_fixture_progress_title),
        context.getString(R.string.bridge_fixture_progress_text),
    ).setStyle(Notification.ProgressStyle().setProgress(PROGRESS_VALUE))
        .setOngoing(true)
        .setRequestPromotedOngoing(true)
        .build()

    private fun low(context: Context): Notification = builder(
        context,
        LOW_CHANNEL,
        context.getString(R.string.bridge_fixture_low_title),
        context.getString(R.string.bridge_fixture_low_text),
    ).build()

    // Redacted only while the lock screen hides sensitive content, as an app's own private is.
    private fun private(context: Context): Notification = builder(
        context,
        DEFAULT_CHANNEL,
        context.getString(R.string.bridge_fixture_private_title),
        context.getString(R.string.bridge_fixture_private_text),
    ).setVisibility(Notification.VISIBILITY_PRIVATE)
        .setPublicVersion(
            builder(
                context,
                DEFAULT_CHANNEL,
                context.getString(R.string.bridge_fixture_private_public_title),
                context.getString(R.string.bridge_fixture_private_public_text),
            ).build(),
        )
        .build()

    private fun secret(context: Context): Notification = builder(
        context,
        DEFAULT_CHANNEL,
        context.getString(R.string.bridge_fixture_secret_title),
        context.getString(R.string.bridge_fixture_secret_text),
    ).setVisibility(Notification.VISIBILITY_SECRET).build()

    const val FIXTURE_REPLY_KEY = "cue.bridge.fixture.reply"
    private const val DEFAULT_CHANNEL = "${BRIDGE_FIXTURE_CHANNEL_PREFIX}default"
    private const val LOW_CHANNEL = "${BRIDGE_FIXTURE_CHANNEL_PREFIX}low"
    private const val GROUP_KEY = "cue.bridge.fixture.group"
    private const val REPLY_SHORTCUT = "cue.bridge.fixture.chat"
    private const val PLAIN_ID = 1
    private const val UPDATING_ID = 2
    private const val GROUP_FIRST_ID = 10
    private const val GROUP_SUMMARY_ID = 19
    private const val GROUP_CHILDREN = 3
    private const val REPLY_ID = 20
    private const val PROGRESS_ID = 30
    private const val PROGRESS_VALUE = 40
    private const val LOW_ID = 40
    private const val PRIVATE_ID = 50
    private const val SECRET_ID = 60
}

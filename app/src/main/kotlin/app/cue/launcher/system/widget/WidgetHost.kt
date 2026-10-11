package app.cue.launcher.system.widget

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.UserProfiles
import app.cue.launcher.system.toComponentName
import app.cue.launcher.system.toComponentRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private const val CUE_WIDGET_HOST_ID = 0x4355

@Suppress("TooManyFunctions")
@SingleIn(AppScope::class)
@Inject
class WidgetHost(
    application: Application,
    private val profiles: UserProfiles,
    private val scope: CoroutineScope,
) {

    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(application)

    private val removals = MutableSharedFlow<Int>(
        extraBufferCapacity = REMOVAL_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Building one of these is a binder round trip plus a RemoteViews inflation, both on the main
    // thread. The pages that show widgets are composed and disposed as the user swipes between
    // them, so without this the cost lands in the first frames of every gesture, once per widget.
    //
    // Concurrent because this host is a singleton that composition reaches on the main thread while
    // widget maintenance and a config import reach it from their own coroutines, and a plain map
    // resized underneath a reader loses entries or throws. Only the bookkeeping is shared: a view
    // itself is still built, read and attached on the main thread alone.
    private val views = ConcurrentHashMap<Int, AppWidgetHostView>()

    // When each widget last had something to say: either the provider pushed content, or it was
    // asked to and given the chance. Both count as "no reason to wake this app again just yet".
    private val lastFresh = ConcurrentHashMap<Int, Long>()

    private val host = CueAppWidgetHost(
        context = application,
        onRemoved = { appWidgetId ->
            Log.i(TAG, "The system removed widget $appWidgetId")
            views.remove(appWidgetId)
            lastFresh.remove(appWidgetId)
            removals.tryEmit(appWidgetId)
        },
        // Building a view replays the last content the provider sent, which is exactly what may be
        // hours stale, so that one must not count. It lands while createView is still running, and
        // the view only reaches the map once it returns — so an unknown view is a building one.
        onContent = { view ->
            val appWidgetId = view.appWidgetId
            if (views[appWidgetId] === view) lastFresh[appWidgetId] = SystemClock.elapsedRealtime()
        },
    )

    private var listening = false

    val removedWidgets: Flow<Int> = removals.asSharedFlow()

    // Both go to the widget service, which can fail the call (a dead system binder, an oversized
    // transaction) as a RuntimeException. Like Launcher3, the home screen carries on without live
    // widget updates instead of crashing; a failed start is retried on the next onStart.
    fun startListening() {
        if (listening) return
        runCatching { host.startListening() }
            .onSuccess { listening = true }
            .onFailure { failure -> Log.w(TAG, "The widget host cannot start listening", failure) }
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        runCatching { host.stopListening() }
            .onFailure { failure -> Log.w(TAG, "The widget host cannot stop listening", failure) }
    }

    fun allocatedIds(): Set<Int> = host.appWidgetIds.toSet()

    fun allocateAppWidgetId(): Int = host.allocateAppWidgetId()

    fun deleteAppWidgetId(appWidgetId: Int) {
        views.remove(appWidgetId)
        lastFresh.remove(appWidgetId)
        host.deleteAppWidgetId(appWidgetId)
    }

    // Nothing lets a host demand fresh content, and the two obvious routes are both closed:
    // ACTION_APPWIDGET_UPDATE is a protected broadcast only the system may send, and
    // notifyAppWidgetViewDataChanged is deprecated and does nothing for a collection whose items the
    // provider pushes inline. What is left is an options update, which the service turns into
    // onAppWidgetOptionsChanged and delivers across profiles and to a provider whose process is gone
    // — measured to start one from dead. The bundle is empty on purpose: the service merges what it
    // is given, so echoing the options back would race the size the frame reports, and there is
    // nothing to say beyond that somebody is looking. Whether the provider answers with new content
    // is its decision; one that ignores the callback keeps showing what it last sent.
    // Fired whenever a widget comes on screen — a page swipe, a stack step, coming back to the
    // launcher at all — so the throttle is what keeps that from waking every provider over and over,
    // and it counts content the provider pushed by itself: an app already keeping its widget current
    // is never asked for anything.
    fun refresh(appWidgetId: Int) {
        val now = SystemClock.elapsedRealtime()
        if (!widgetRefreshDue(lastFresh[appWidgetId], now, REFRESH_THROTTLE_MS)) return
        lastFresh[appWidgetId] = now
        scope.launch {
            runCatching {
                appWidgetManager.updateAppWidgetOptions(appWidgetId, Bundle())
            }.onFailure { failure ->
                Log.w(TAG, "Widget $appWidgetId cannot be asked for fresh content", failure)
            }
        }
    }

    fun bindIfAllowed(appWidgetId: Int, provider: ComponentRef): Boolean {
        val user = profiles.userOf(provider.userSerial) ?: return false
        return runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId,
                user,
                provider.toComponentName(),
                null,
            )
        }.onFailure { failure ->
            Log.w(TAG, "Bind of $provider was denied", failure)
        }.getOrDefault(false)
    }

    fun bindPermissionIntent(appWidgetId: Int, provider: ComponentRef): Intent? {
        val user = profiles.userOf(provider.userSerial) ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.toComponentName())
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, user)
        }
    }

    fun startConfigure(activity: Activity, appWidgetId: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
        true
    } catch (failure: ActivityNotFoundException) {
        Log.w(TAG, "The configuration of widget $appWidgetId cannot be started", failure)
        false
    }

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)

    fun isReconfigurable(appWidgetId: Int): Boolean {
        val info = providerInfo(appWidgetId) ?: return false
        return widgetReconfigurable(info.configure != null, info.widgetFeatures)
    }

    // The size spec is read afresh every time — a provider can be updated underneath us — but the
    // view itself is kept. A cached view built by an activity that has since been recreated is of
    // no use to the new one, and neither is anything else built by that generation.
    fun hostedView(context: Context, appWidgetId: Int): HostedWidgetView? {
        val info = providerInfo(appWidgetId) ?: return null
        val cached = views[appWidgetId]?.takeIf { view -> view.context === context }
        if (cached == null) views.values.retainAll { view -> view.context === context }
        val view = cached ?: runCatching { host.createView(context, appWidgetId, info) }
            .onFailure { failure -> Log.w(TAG, "Widget $appWidgetId cannot be rendered", failure) }
            .getOrNull()
            ?.also { created -> views[appWidgetId] = created }
        return view?.let { hostView -> HostedWidgetView(hostView, info.toSizeSpec()) }
    }

    private companion object {
        const val TAG = "WidgetHost"

        const val REMOVAL_BUFFER = 16

        const val REFRESH_THROTTLE_MS = 30_000L
    }
}

data class HostedWidgetView(val view: AppWidgetHostView, val size: WidgetSizeSpec)

// The clock is elapsed real time on purpose: a phone that slept through the night is exactly when
// the content is stale, and uptime would not have moved.
internal fun widgetRefreshDue(lastRefreshAt: Long?, now: Long, throttleMs: Long): Boolean =
    lastRefreshAt == null || now - lastRefreshAt >= throttleMs

internal fun AppWidgetProviderInfo.toSizeSpec() = WidgetSizeSpec(
    minWidthPx = minWidth,
    minHeightPx = minHeight,
    horizontallyResizable = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
    verticallyResizable = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
    minResizeWidthPx = minResizeWidth,
    minResizeHeightPx = minResizeHeight,
)

private class CueAppWidgetHost(
    context: Context,
    private val onRemoved: (Int) -> Unit,
    private val onContent: (AppWidgetHostView) -> Unit,
) : AppWidgetHost(context, CUE_WIDGET_HOST_ID) {

    override fun onAppWidgetRemoved(appWidgetId: Int) {
        onRemoved(appWidgetId)
    }

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = CueAppWidgetHostView(context, onContent)
}

private class CueAppWidgetHostView(
    context: Context,
    private val onContent: (AppWidgetHostView) -> Unit,
) : AppWidgetHostView(context) {

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        // Null is the provider having nothing to show, which is the opposite of fresh content.
        if (remoteViews != null) onContent(this)
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
    }
}

package app.cue.launcher.system.widget

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import app.cue.launcher.data.widgets.HostedWidgetRepository
import app.cue.launcher.data.widgets.WidgetPlacement
import app.cue.launcher.system.launchOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class WidgetBinder(
    private val activity: ComponentActivity,
    private val host: WidgetHost,
    private val widgets: HostedWidgetRepository,
    private val scope: CoroutineScope,
) {

    private var pending: PendingBind? = null

    private val events = MutableSharedFlow<WidgetBindResult>(
        extraBufferCapacity = OUTCOME_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val permissionRequest = activity.registerForActivityResult(StartActivityForResult()) {
        onPermissionResult(granted = it.resultCode == Activity.RESULT_OK)
    }

    val outcomes: Flow<WidgetBindResult> = events.asSharedFlow()

    fun bind(widget: AvailableWidget, placement: WidgetPlacement = WidgetPlacement.PANEL) {
        val appWidgetId = host.allocateAppWidgetId()
        val request = PendingBind(appWidgetId, widget, placement)
        pending = request
        val bound = host.bindIfAllowed(appWidgetId, widget.provider)
        continueWith(widgetBindStage(bound, widget.needsConfiguration), request)
    }

    fun reconfigure(appWidgetId: Int): Boolean =
        host.startConfigure(activity, appWidgetId, CONFIGURE_REQUEST)

    fun unbind(appWidgetId: Int) {
        host.deleteAppWidgetId(appWidgetId)
        scope.launch { widgets.forget(listOf(appWidgetId)) }
    }

    fun onConfigureResult(requestCode: Int, granted: Boolean): Boolean {
        if (requestCode != CONFIGURE_REQUEST) return false

        pending?.let { request ->
            if (granted) finish(request) else abort(request, WidgetBindOutcome.CANCELED)
        }
        return true
    }

    private fun continueWith(stage: WidgetBindStage, request: PendingBind) = when (stage) {
        WidgetBindStage.PERMISSION -> requestPermission(request)
        WidgetBindStage.CONFIGURE -> configure(request)
        WidgetBindStage.DONE -> finish(request)
    }

    private fun requestPermission(request: PendingBind) {
        val intent = host.bindPermissionIntent(request.appWidgetId, request.widget.provider)
        if (intent == null) {
            abort(request, WidgetBindOutcome.FAILED)
        } else {
            permissionRequest.launchOrElse(intent) { abort(request, WidgetBindOutcome.FAILED) }
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        val request = pending ?: return
        if (!granted) {
            abort(request, WidgetBindOutcome.CANCELED)
            return
        }
        continueWith(
            widgetBindStage(bound = true, needsConfiguration = request.widget.needsConfiguration),
            request,
        )
    }

    private fun configure(request: PendingBind) {
        if (!host.startConfigure(activity, request.appWidgetId, CONFIGURE_REQUEST)) {
            abort(request, WidgetBindOutcome.FAILED)
        }
    }

    private fun finish(request: PendingBind) {
        pending = null
        scope.launch {
            widgets.remember(request.appWidgetId, request.widget.provider, request.placement)
        }
        Log.i(TAG, "Widget ${request.widget.provider} bound as ${request.appWidgetId}")
        events.tryEmit(request.result(WidgetBindOutcome.ADDED))
    }

    private fun abort(request: PendingBind, outcome: WidgetBindOutcome) {
        pending = null
        host.deleteAppWidgetId(request.appWidgetId)
        Log.i(TAG, "Binding of ${request.widget.provider} ended as $outcome")
        events.tryEmit(request.result(outcome))
    }

    private data class PendingBind(
        val appWidgetId: Int,
        val widget: AvailableWidget,
        val placement: WidgetPlacement,
    ) {
        fun result(outcome: WidgetBindOutcome) =
            WidgetBindResult(outcome, placement, appWidgetId, widget)
    }

    private companion object {
        const val TAG = "WidgetHost"

        const val CONFIGURE_REQUEST = 0x4356

        const val OUTCOME_BUFFER = 4
    }
}

package app.cue.launcher.system.widget

import android.appwidget.AppWidgetProviderInfo
import app.cue.launcher.data.widgets.WidgetPlacement

enum class WidgetBindStage {

    PERMISSION,

    CONFIGURE,

    DONE,
}

fun widgetBindStage(bound: Boolean, needsConfiguration: Boolean): WidgetBindStage = when {
    !bound -> WidgetBindStage.PERMISSION
    needsConfiguration -> WidgetBindStage.CONFIGURE
    else -> WidgetBindStage.DONE
}

fun widgetReconfigurable(hasConfigureActivity: Boolean, widgetFeatures: Int): Boolean =
    hasConfigureActivity &&
        widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

enum class WidgetBindOutcome {

    ADDED,

    CANCELED,

    FAILED,
}

data class WidgetBindResult(
    val outcome: WidgetBindOutcome,
    val placement: WidgetPlacement,
    val appWidgetId: Int,
    val widget: AvailableWidget,
)

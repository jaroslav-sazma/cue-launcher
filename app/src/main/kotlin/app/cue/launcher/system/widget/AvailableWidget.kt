package app.cue.launcher.system.widget

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.COMPONENT_REF_ORDER
import java.text.Collator

data class AvailableWidget(
    val provider: ComponentRef,
    val label: String,

    val appLabel: String,

    val needsConfiguration: Boolean,

    val size: WidgetSizeSpec,
) {

    val minWidthPx: Int get() = size.minWidthPx
    val minHeightPx: Int get() = size.minHeightPx
}

fun availableWidgetOrder(collator: Collator = Collator.getInstance()): Comparator<AvailableWidget> =
    Comparator { first, second ->
        val byApp = collator.compare(first.appLabel, second.appLabel)
        if (byApp != 0) return@Comparator byApp
        val byLabel = collator.compare(first.label, second.label)
        if (byLabel != 0) byLabel else COMPONENT_REF_ORDER.compare(first.provider, second.provider)
    }

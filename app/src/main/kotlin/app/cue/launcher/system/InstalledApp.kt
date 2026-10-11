package app.cue.launcher.system

import app.cue.launcher.data.layout.ComponentRef
import java.text.Collator

data class InstalledApp(val component: ComponentRef, val label: String)

data class SearchableApp(val app: InstalledApp, val englishLabel: String? = null)

fun installedAppOrder(collator: Collator = Collator.getInstance()): Comparator<InstalledApp> =
    Comparator { first, second ->
        val byLabel = collator.compare(first.label, second.label)
        if (byLabel != 0) byLabel else COMPONENT_REF_ORDER.compare(first.component, second.component)
    }

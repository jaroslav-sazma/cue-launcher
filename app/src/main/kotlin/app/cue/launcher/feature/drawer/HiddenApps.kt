package app.cue.launcher.feature.drawer

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.system.SearchableApp

fun visibleApps(apps: List<SearchableApp>, hidden: Set<ComponentRef>): List<SearchableApp> =
    if (hidden.isEmpty()) apps else apps.filterNot { it.app.component in hidden }

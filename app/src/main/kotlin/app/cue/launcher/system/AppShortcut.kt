package app.cue.launcher.system

import app.cue.launcher.data.layout.ComponentRef

data class AppShortcut(val component: ComponentRef, val id: String, val label: String)

data class AppShortcutOffer(
    val id: String,
    val label: String,
    val activityClassName: String?,
    val enabled: Boolean,
    val declaredInManifest: Boolean,
    val rank: Int,
)

const val ICON_MENU_SHORTCUT_LIMIT = 4

fun iconMenuShortcuts(
    offers: List<AppShortcutOffer>,
    activityClassName: String,
    limit: Int = ICON_MENU_SHORTCUT_LIMIT,
): List<AppShortcutOffer> = offers
    .filter { offer ->
        offer.enabled &&
            offer.label.isNotBlank() &&
            (offer.activityClassName == null || offer.activityClassName == activityClassName)
    }

    .sortedWith(compareBy({ !it.declaredInManifest }, { it.rank }))
    .take(limit)

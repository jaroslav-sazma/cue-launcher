package app.cue.launcher.data.layout

enum class DockRole {
    PHONE,
    MESSAGES,
    CAMERA,
    BROWSER,
    ;

    val dockPosition: Int get() = ordinal
}

fun dockSeedPlacements(resolved: Map<DockRole, ComponentRef?>): Map<Int, ComponentRef> {
    val placed = mutableSetOf<ComponentRef>()
    return DockRole.entries
        .mapNotNull { role ->
            val component = resolved[role] ?: return@mapNotNull null
            if (!placed.add(component)) return@mapNotNull null
            role.dockPosition to component
        }
        .toMap()
}

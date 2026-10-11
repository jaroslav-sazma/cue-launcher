package app.cue.launcher.system

import app.cue.launcher.data.scenario.TapAction

enum class AppActionKind {

    SHORTCUT,

    CONFIGURABLE,

    LINK,

    CAPABILITY,

    ACTIVITY,
}

data class AppActionEntry(
    val kind: AppActionKind,
    val label: String,
    val detail: String?,

    val action: TapAction?,

    val configActivity: String? = null,
) {

    val key: String get() = "$kind:${action ?: configActivity}"
}

data class AppActionGroup(val kind: AppActionKind, val entries: List<AppActionEntry>)

fun appActionGroups(entries: List<AppActionEntry>): List<AppActionGroup> =
    AppActionKind.entries.mapNotNull { kind ->
        entries.filter { it.kind == kind }
            .takeIf { it.isNotEmpty() }
            ?.let { AppActionGroup(kind, it) }
    }

fun capabilityLabel(capability: String): String {
    val last = capability.substringAfterLast('.').ifEmpty { capability }
    val words = last.split('_').filter { it.isNotEmpty() }
    if (words.isEmpty()) return capability
    return words.joinToString(" ") { it.lowercase() }
        .replaceFirstChar { it.uppercaseChar() }
}

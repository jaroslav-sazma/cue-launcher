package app.cue.launcher.data.widgets

enum class WidgetPlacement(val storageKey: String) {

    PANEL("panel"),

    SCENARIO("scenario"),

    HOME("home"),
    ;

    companion object {

        fun fromStorageKey(key: String): WidgetPlacement =
            entries.firstOrNull { it.storageKey == key } ?: PANEL
    }
}

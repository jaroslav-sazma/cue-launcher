package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ScenarioFormat
import app.cue.launcher.system.InstalledApp

// A widget about to be bound comes back as the picker it was chosen from, to be picked again from
// what is installed by then, and as a new opening, so the bind sheet's saved state is not handed
// on.
internal fun savedOpening(sheet: BuilderSheet?, opening: Int): Int =
    if (sheet is BuilderSheet.WidgetBind) opening + 1 else opening

internal fun savedSheet(sheet: BuilderSheet?): List<Any> = when (sheet) {
    null -> emptyList()
    is BuilderSheet.Catalog -> listOf(SheetKind.CATALOG.name, sheet.replacing)
    is BuilderSheet.Settings -> listOf(
        SheetKind.SETTINGS.name,
        ScenarioFormat.encodeConditions(listOf(sheet.condition)),
        sheet.index ?: SAVED_ABSENT,
        sheet.diagnosis,
    )

    BuilderSheet.WidgetPicker, is BuilderSheet.WidgetBind -> listOf(SheetKind.WIDGET_PICKER.name)
    is BuilderSheet.ActionPicker -> listOf(SheetKind.ACTION_PICKER.name) + savedSlot(sheet.slot)
    is BuilderSheet.AppPicker -> savedRequest(sheet.request)
    is BuilderSheet.AppActions -> listOf(SheetKind.APP_ACTIONS.name) + savedSlot(sheet.slot) + savedApp(sheet.app)
    is BuilderSheet.SystemAction -> listOf(SheetKind.SYSTEM_ACTION.name) + savedSlot(sheet.slot)
    is BuilderSheet.HaAction -> listOf(SheetKind.HA_ACTION.name) + savedSlot(sheet.slot)
    is BuilderSheet.UriAction -> listOf(SheetKind.URI_ACTION.name) + savedSlot(sheet.slot)
    is BuilderSheet.IconPicker -> listOf(SheetKind.ICON_PICKER.name, sheet.item)
}

internal fun restoredSheet(saved: List<*>): BuilderSheet? {
    if (saved.isEmpty()) return null
    val fields = SavedFields(saved)
    return when (val kind = SheetKind.valueOf(fields.string())) {
        SheetKind.CATALOG -> BuilderSheet.Catalog(replacing = fields.boolean())
        SheetKind.SETTINGS -> restoredCondition(fields.string())?.let { condition ->
            BuilderSheet.Settings(condition, fields.index(), diagnosis = fields.boolean())
        }

        SheetKind.WIDGET_PICKER -> BuilderSheet.WidgetPicker
        SheetKind.ACTION_PICKER -> BuilderSheet.ActionPicker(fields.slot())
        SheetKind.APP_TO_OPEN, SheetKind.APP_IN_APP, SheetKind.APP_FOR_CONDITION ->
            restoredRequest(kind, fields)?.let(BuilderSheet::AppPicker)

        SheetKind.APP_ACTIONS -> BuilderSheet.AppActions(fields.slot(), fields.app())
        SheetKind.SYSTEM_ACTION -> BuilderSheet.SystemAction(fields.slot())
        SheetKind.HA_ACTION -> BuilderSheet.HaAction(fields.slot())
        SheetKind.URI_ACTION -> BuilderSheet.UriAction(fields.slot())
        SheetKind.ICON_PICKER -> BuilderSheet.IconPicker(item = fields.int())
    }
}

private enum class SheetKind {
    CATALOG,
    SETTINGS,
    WIDGET_PICKER,
    ACTION_PICKER,
    APP_TO_OPEN,
    APP_IN_APP,
    APP_FOR_CONDITION,
    APP_ACTIONS,
    SYSTEM_ACTION,
    HA_ACTION,
    URI_ACTION,
    ICON_PICKER,
}

private fun savedRequest(request: AppPickRequest): List<Any> = when (request) {
    is AppPickRequest.OpenApp -> listOf(SheetKind.APP_TO_OPEN.name) + savedSlot(request.slot)
    is AppPickRequest.InApp -> listOf(SheetKind.APP_IN_APP.name) + savedSlot(request.slot)
    is AppPickRequest.ForCondition -> listOf(
        SheetKind.APP_FOR_CONDITION.name,
        ScenarioFormat.encodeConditions(listOf(request.condition)),
        request.index ?: SAVED_ABSENT,
        request.target.name,
    )
}

private fun restoredRequest(kind: SheetKind, fields: SavedFields): AppPickRequest? = when (kind) {
    SheetKind.APP_TO_OPEN -> AppPickRequest.OpenApp(fields.slot())
    SheetKind.APP_IN_APP -> AppPickRequest.InApp(fields.slot())
    else -> (restoredCondition(fields.string()) as? Condition.InstantCondition)?.let { condition ->
        AppPickRequest.ForCondition(condition, fields.index(), AppTarget.valueOf(fields.string()))
    }
}

private fun savedSlot(slot: ActionSlot): List<Any> = listOf(slot.item, slot.action)

private fun savedApp(app: InstalledApp): List<Any> =
    listOf(app.component.packageName, app.component.className, app.component.userSerial, app.label)

private fun restoredCondition(json: String): Condition? = ScenarioFormat.decodeConditions(json).singleOrNull()

private class SavedFields(private val fields: List<*>) {

    private var next = 0

    fun string(): String = fields[next++] as String

    fun int(): Int = fields[next++] as Int

    fun boolean(): Boolean = fields[next++] as Boolean

    fun index(): Int? = int().takeIf { it != SAVED_ABSENT }

    fun slot(): ActionSlot = ActionSlot(item = int(), action = int())

    fun app(): InstalledApp = InstalledApp(
        component = ComponentRef(packageName = string(), className = string(), userSerial = fields[next++] as Long),
        label = string(),
    )
}

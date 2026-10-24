package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.ActionApp
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.hasPlaceholder
import app.cue.launcher.designsystem.glyph.mdiGlyphName
import app.cue.launcher.feature.home.templateGlyphName
import app.cue.launcher.feature.home.templateIconApp
import app.cue.launcher.feature.scenarios.reordered
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ContentKind {

    TEMPLATE,

    APP_WIDGET,

    LAUNCHER_WIDGET,
}

data class ActionSlot(val item: Int, val action: Int)

@Serializable
data class DraftAction(val action: TapAction, val label: String? = null)

@Serializable
data class TemplateItemDraft(
    val label: String = "",
    val icon: String? = null,

    val actions: List<DraftAction> = emptyList(),
) {

    val isFilled: Boolean get() = actions.isNotEmpty()

    val primaryAction: TapAction? get() = actions.firstOrNull()?.action

    fun withAction(index: Int, action: TapAction, actionLabel: String?): TemplateItemDraft {
        val next = DraftAction(action, actionLabel)
        return copy(
            actions = if (index in actions.indices) {
                actions.toMutableList().apply { set(index, next) }
            } else {
                actions + next
            },
        )
    }

    fun withoutAction(index: Int): TemplateItemDraft {
        if (index !in actions.indices) return this
        return copy(actions = actions.toMutableList().apply { removeAt(index) })
    }

    fun withActionMoved(from: Int, to: Int): TemplateItemDraft =
        copy(actions = actions.reordered(from, to))

    fun toItem(): TemplateItem? = actions
        .takeIf { it.isNotEmpty() }
        ?.let { chosen ->
            TemplateItem(
                label = label.trim().ifEmpty { null },
                icon = icon,
                actions = chosen.map(DraftAction::action),
            )
        }
}

@Serializable
data class BoundWidget(
    val provider: ComponentRef,
    val appWidgetId: Int,
    val label: String,
    val appLabel: String,

    val needsConfiguration: Boolean,
)

@Serializable
data class ContentDraft(
    val kind: ContentKind = ContentKind.TEMPLATE,
    val layout: TemplateLayout? = null,
    val text: String = "",
    val text2: String = "",
    val items: List<TemplateItemDraft> = emptyList(),
    val widget: BoundWidget? = null,
    val launcherWidget: LauncherWidgetType? = null,

    // The provider an unbound widget names, from an import or a pasted scenario; picking a widget
    // offers it first.
    val suggestedWidget: ComponentRef? = null,
) {

    fun withLayout(next: TemplateLayout): ContentDraft = copy(
        layout = next,
        items = List(next.itemCount) { index -> items.getOrElse(index) { TemplateItemDraft() } },
    )

    fun withItem(index: Int, item: TemplateItemDraft): ContentDraft {
        if (index !in items.indices) return this
        return copy(items = items.toMutableList().apply { set(index, item) })
    }
}

fun contentDraftOf(content: TileContent): ContentDraft = when (content) {
    is TileContent.Template -> ContentDraft(
        kind = ContentKind.TEMPLATE,
        layout = content.layout,
        text = content.text.orEmpty(),
        text2 = content.text2.orEmpty(),
        items = content.items.map { item ->
            TemplateItemDraft(
                label = item.label.orEmpty(),
                icon = item.icon,
                actions = item.actions.map { DraftAction(it) },
            )
        },
    )

    is TileContent.AppWidget -> ContentDraft(
        kind = ContentKind.APP_WIDGET,
        suggestedWidget = ComponentRef(content.packageName, content.provider, userSerial = 0L)
            .takeUnless { hasPlaceholder(content.packageName) || hasPlaceholder(content.provider) },

        widget = content.boundAppWidgetId?.let { appWidgetId ->
            BoundWidget(
                provider = ComponentRef(content.packageName, content.provider, userSerial = 0L),
                appWidgetId = appWidgetId,
                label = content.provider.substringAfterLast('.'),
                appLabel = content.packageName,
                needsConfiguration = false,
            )
        },
    )

    is TileContent.LauncherWidget ->
        ContentDraft(kind = ContentKind.LAUNCHER_WIDGET, launcherWidget = content.widget)
}

fun defaultContentDraft(size: TileSize): ContentDraft {
    val kind = contentKindsFor(size).firstOrNull() ?: ContentKind.TEMPLATE
    val layout = templateLayoutsFor(size).firstOrNull()
    return ContentDraft(kind = kind, layout = layout)
        .let { draft -> if (layout == null) draft else draft.withLayout(layout) }
}

fun tileContentOf(draft: ContentDraft): TileContent? = when (draft.kind) {
    ContentKind.TEMPLATE -> templateContentOf(draft)
    ContentKind.APP_WIDGET -> draft.widget?.let { widget ->
        TileContent.AppWidget(
            packageName = widget.provider.packageName,
            provider = widget.provider.className,
            appWidgetId = widget.appWidgetId.toString(),
        )
    }

    ContentKind.LAUNCHER_WIDGET -> draft.launcherWidget?.let(TileContent::LauncherWidget)
}

@Suppress("ReturnCount")
private fun templateContentOf(draft: ContentDraft): TileContent.Template? {
    val layout = draft.layout ?: return null
    val items = draft.items.map { it.toItem() ?: return null }
    if (items.size != layout.itemCount) return null
    return TileContent.Template(
        layout = layout,
        text = draft.text.trim().ifEmpty { null },
        text2 = draft.text2.trim().ifEmpty { null },
        items = items,
    )
}

enum class ContentFormError {

    NO_WIDGET,

    NO_LAUNCHER_WIDGET,

    NO_LAYOUT,

    MISSING_ACTIONS,

    MISSING_TEXT,
}

fun contentFormError(draft: ContentDraft): ContentFormError? = when (draft.kind) {
    ContentKind.APP_WIDGET -> if (draft.widget == null) ContentFormError.NO_WIDGET else null
    ContentKind.LAUNCHER_WIDGET ->
        if (draft.launcherWidget == null) ContentFormError.NO_LAUNCHER_WIDGET else null

    ContentKind.TEMPLATE -> templateFormError(draft)
}

@Suppress("ReturnCount")
private fun templateFormError(draft: ContentDraft): ContentFormError? {
    val layout = draft.layout ?: return ContentFormError.NO_LAYOUT
    if (layout.needsText && draft.text.isBlank()) return ContentFormError.MISSING_TEXT
    val filled = draft.items.count { it.isFilled }
    return if (filled < layout.itemCount) ContentFormError.MISSING_ACTIONS else null
}

private val TemplateLayout.needsText: Boolean
    get() = this == TemplateLayout.TEXT_ONLY || this == TemplateLayout.TEXT_TWO_ICONS

fun previewGlyphName(item: TemplateItemDraft): String? =
    item.toItem()?.let(::templateGlyphName) ?: mdiGlyphName(item.icon)

fun previewIconApp(item: TemplateItemDraft): ActionApp? = item.toItem()?.let(::templateIconApp)

fun encodeContentDraft(draft: ContentDraft): String = draftJson.encodeToString(draft)

fun decodeContentDraft(json: String): ContentDraft = runCatching {
    draftJson.decodeFromString<ContentDraft>(json)
}.getOrDefault(ContentDraft())

private val draftJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.scenario.ActionApp
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ContentDraftTest {

    private val openApp = TapAction.App(packageName = "com.spotify.music")

    @Test
    fun `default content matches the size`() {
        val small = defaultContentDraft(TileSize.GRID_1X1)
        assertEquals(ContentKind.TEMPLATE, small.kind)
        assertEquals(TemplateLayout.ICON_TEXT, small.layout)
        assertEquals(1, small.items.size)

        val medium = defaultContentDraft(TileSize.GRID_2X2)
        assertEquals(TemplateLayout.TEXT_ONLY, medium.layout)
        assertEquals(1, medium.items.size)

        val hero = defaultContentDraft(TileSize.HERO_4X2)
        assertEquals(ContentKind.APP_WIDGET, hero.kind)
        assertNull(hero.layout)
    }

    @Test
    fun `swapping the layout carries over filled items`() {
        val four = defaultContentDraft(TileSize.GRID_2X2)
            .withLayout(TemplateLayout.FOUR_ICONS)
            .withItem(0, TemplateItemDraft(label = "Lights", actions = listOf(DraftAction(openApp))))
            .withItem(1, TemplateItemDraft(label = "Heating", actions = listOf(DraftAction(openApp))))

        val two = four.withLayout(TemplateLayout.TEXT_TWO_ICONS)
        assertEquals(2, two.items.size)
        assertEquals(listOf("Lights", "Heating"), two.items.map { it.label })

        val back = two.withLayout(TemplateLayout.FOUR_ICONS)
        assertEquals(4, back.items.size)
        assertEquals(listOf("Lights", "Heating"), back.items.take(2).map { it.label })
        assertEquals(listOf(TemplateItemDraft(), TemplateItemDraft()), back.items.drop(2))
    }

    @Test
    fun `writing out of range does nothing`() {
        val draft = defaultContentDraft(TileSize.GRID_1X1)
        val item = TemplateItemDraft(actions = listOf(DraftAction(openApp)))
        assertEquals(draft, draft.withItem(index = 3, item = item))
    }

    @Test
    fun `a finished template translates into content`() {
        val draft = defaultContentDraft(TileSize.GRID_2X2)
            .copy(text = " Navigate home ", text2 = "  ")
            .withItem(
                0,
                TemplateItemDraft(label = "  ", icon = "mdi:navigation", actions = listOf(DraftAction(openApp))),
            )

        val content = tileContentOf(draft) as TileContent.Template
        assertEquals(TemplateLayout.TEXT_ONLY, content.layout)
        assertEquals("Navigate home", content.text)
        assertNull(content.text2)
        assertEquals(1, content.items.size)
        assertNull(content.items.single().label)
        assertEquals("mdi:navigation", content.items.single().icon)
    }

    @Test
    fun `a template without an action does not translate`() {
        assertNull(tileContentOf(defaultContentDraft(TileSize.GRID_1X1)))
    }

    @Test
    fun `a bound widget translates to app_widget`() {
        val draft = defaultContentDraft(TileSize.HERO_4X2).copy(widget = boundWidget(42))

        val content = tileContentOf(draft) as TileContent.AppWidget
        assertEquals("io.homeassistant", content.packageName)
        assertEquals("io.homeassistant.Dashboard", content.provider)
        assertEquals("42", content.appWidgetId)
        assertEquals(42, content.boundAppWidgetId)
    }

    @Test
    fun `a custom launcher widget translates to launcher_widget`() {
        val draft = defaultContentDraft(TileSize.HERO_4X2)
            .copy(kind = ContentKind.LAUNCHER_WIDGET, launcherWidget = LauncherWidgetType.CALENDAR)

        assertEquals(TileContent.LauncherWidget(LauncherWidgetType.CALENDAR), tileContentOf(draft))
    }

    @Test
    fun `missing things are reported in form order`() {
        val widgetDraft = defaultContentDraft(TileSize.GRID_2X2).copy(kind = ContentKind.APP_WIDGET)
        assertEquals(ContentFormError.NO_WIDGET, contentFormError(widgetDraft))
        assertNull(contentFormError(widgetDraft.copy(widget = boundWidget(1))))

        val textDraft = defaultContentDraft(TileSize.GRID_2X2)
        assertEquals(ContentFormError.MISSING_TEXT, contentFormError(textDraft))

        val withText = textDraft.copy(text = "Navigate home")
        assertEquals(ContentFormError.MISSING_ACTIONS, contentFormError(withText))
        val filled = withText.withItem(0, TemplateItemDraft(actions = listOf(DraftAction(openApp))))
        assertNull(contentFormError(filled))
    }

    @Test
    fun `four icons need four actions`() {
        val draft = defaultContentDraft(TileSize.GRID_2X2).withLayout(TemplateLayout.FOUR_ICONS)
        val filled = (0..2).fold(draft) { acc, index ->
            acc.withItem(index, TemplateItemDraft(actions = listOf(DraftAction(openApp))))
        }

        assertEquals(ContentFormError.MISSING_ACTIONS, contentFormError(filled))
        val all = filled.withItem(3, TemplateItemDraft(actions = listOf(DraftAction(openApp))))
        assertNull(contentFormError(all))
    }

    @Test
    fun `a small tile does not require text`() {
        val draft = defaultContentDraft(TileSize.GRID_1X1)
            .withItem(0, TemplateItemDraft(actions = listOf(DraftAction(openApp))))

        assertNull(contentFormError(draft))
    }

    @Test
    fun `the glyph is derived from the action`() {
        assertNull(previewGlyphName(TemplateItemDraft()))
        assertEquals(
            "home-assistant",
            previewGlyphName(
                TemplateItemDraft(actions = listOf(DraftAction(TapAction.HaAction(service = "light.turn_off")))),
            ),
        )
        assertEquals(
            "flashlight",
            previewGlyphName(
                TemplateItemDraft(
                    actions = listOf(DraftAction(TapAction.SystemAction(SystemActionType.FLASHLIGHT))),
                ),
            ),
        )
        assertEquals(
            "gate",
            previewGlyphName(TemplateItemDraft(icon = "mdi:gate", actions = listOf(DraftAction(openApp)))),
        )
    }

    @Test
    fun `an app action is drawn with its icon`() {
        val item = TemplateItemDraft(actions = listOf(DraftAction(openApp)))

        assertNull(previewGlyphName(item))
        assertEquals(ActionApp("com.spotify.music"), previewIconApp(item))
        assertNull(previewIconApp(item.copy(icon = "mdi:gate")))
        assertNull(previewIconApp(TemplateItemDraft()))
        assertNull(
            previewIconApp(
                TemplateItemDraft(
                    actions = listOf(DraftAction(TapAction.SystemAction(SystemActionType.FLASHLIGHT))),
                ),
            ),
        )
    }

    @Test
    fun `a chosen icon can be reverted back to the derived one`() {
        val chosen = TemplateItemDraft(actions = listOf(DraftAction(openApp))).copy(icon = "mdi:gate")

        assertEquals("gate", previewGlyphName(chosen))
        assertNull(previewGlyphName(chosen.copy(icon = null)))
        assertEquals(ActionApp("com.spotify.music"), previewIconApp(chosen.copy(icon = null)))
    }

    @Test
    fun `a chosen action carries its label from the sheet`() {
        val shortcut = TapAction.Shortcut(packageName = "com.spotify.music", shortcutId = "liked")

        val item = TemplateItemDraft().withAction(0, shortcut, actionLabel = "Favorite tracks")

        assertEquals(shortcut, item.primaryAction)
        assertEquals("Favorite tracks", item.actions.single().label)

        assertEquals(listOf(shortcut), item.toItem()?.actions)
        assertNull(item.toItem()?.label)
    }

    @Test
    fun `an index within the action list replaces it`() {
        val item = TemplateItemDraft()
            .withAction(0, TapAction.App("com.spotify.music"), actionLabel = "Spotify")
            .withAction(0, TapAction.SystemAction(SystemActionType.FLASHLIGHT), actionLabel = null)

        assertEquals(1, item.actions.size)
        assertNull(item.actions.single().label)
    }

    @Test
    fun `an item can hold multiple actions in order`() {
        val silent = TapAction.SystemAction(SystemActionType.RINGER_SILENT)
        val dnd = TapAction.SystemAction(SystemActionType.DND_ON)

        val item = TemplateItemDraft()
            .withAction(0, silent, actionLabel = null)
            .withAction(1, dnd, actionLabel = null)

        assertEquals(listOf(silent, dnd), item.toItem()?.actions)

        assertEquals(silent, item.primaryAction)
    }

    @Test
    fun `an index past the end appends an action`() {
        val item = TemplateItemDraft()
            .withAction(5, openApp, actionLabel = "Spotify")
            .withAction(9, openApp, actionLabel = "Spotify")

        assertEquals(2, item.actions.size)
    }

    @Test
    fun `removing an action shortens the list and out of range does nothing`() {
        val item = TemplateItemDraft()
            .withAction(0, TapAction.SystemAction(SystemActionType.RINGER_SILENT), actionLabel = null)
            .withAction(1, TapAction.SystemAction(SystemActionType.DND_ON), actionLabel = null)

        assertEquals(
            listOf(TapAction.SystemAction(SystemActionType.DND_ON)),
            item.withoutAction(0).toItem()?.actions,
        )
        assertEquals(item, item.withoutAction(7))

        assertNull(item.withoutAction(0).withoutAction(0).toItem())
    }

    @Test
    fun `unfinished content survives a rotation`() {
        val draft = defaultContentDraft(TileSize.GRID_2X2)
            .withLayout(TemplateLayout.TEXT_TWO_ICONS)
            .copy(text = "Evening at home", widget = boundWidget(7))
            .withItem(
                0,
                TemplateItemDraft(label = "Lights", icon = "mdi:lightbulb")
                    .withAction(0, openApp, actionLabel = "Spotify")
                    .withAction(1, TapAction.SystemAction(SystemActionType.FLASHLIGHT), actionLabel = null),
            )

        assertEquals(draft, decodeContentDraft(encodeContentDraft(draft)))
        assertEquals(ContentDraft(), decodeContentDraft("{this is not JSON"))
    }

    @Test
    fun `moving an action changes the order and the primary action`() {
        val flashlight = TapAction.SystemAction(SystemActionType.FLASHLIGHT)
        val item = TemplateItemDraft()
            .withAction(0, openApp, actionLabel = "Spotify")
            .withAction(1, flashlight, actionLabel = null)

        val moved = item.withActionMoved(from = 1, to = 0)

        assertEquals(listOf(flashlight, openApp), moved.actions.map(DraftAction::action))
        assertEquals(flashlight, moved.primaryAction)
        assertEquals(item, item.withActionMoved(from = 1, to = 1))
        assertEquals(item, item.withActionMoved(from = 0, to = 4))
    }

    @Test
    fun `finished content converted to a draft and back is the same`() {
        val template = TileContent.Template(
            layout = TemplateLayout.TEXT_TWO_ICONS,
            text = "Evening at home",
            text2 = "calm",
            items = listOf(
                TemplateItem(label = "Lights", icon = "mdi:lightbulb", actions = listOf(openApp)),
                TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.FLASHLIGHT))),
            ),
        )
        val calendar = TileContent.LauncherWidget(LauncherWidgetType.CALENDAR)
        val widget = TileContent.AppWidget(
            packageName = "io.homeassistant",
            provider = "io.homeassistant.Dashboard",
            appWidgetId = "7",
        )

        assertEquals(template, tileContentOf(contentDraftOf(template)))
        assertEquals(calendar, tileContentOf(contentDraftOf(calendar)))
        assertEquals(widget, tileContentOf(contentDraftOf(widget)))
    }

    @Test
    fun `an unbound widget id comes out as no widget selected`() {
        val unbound = TileContent.AppWidget(
            packageName = "io.homeassistant",
            provider = "io.homeassistant.Dashboard",
            appWidgetId = "{{PLACEHOLDER:widget_1}}",
        )

        val draft = contentDraftOf(unbound)

        assertNull(draft.widget)
        assertEquals(ContentFormError.NO_WIDGET, contentFormError(draft))
    }

    private fun boundWidget(appWidgetId: Int) = BoundWidget(
        provider = ComponentRef("io.homeassistant", "io.homeassistant.Dashboard", userSerial = 0),
        appWidgetId = appWidgetId,
        label = "Dashboard",
        appLabel = "Home Assistant",
        needsConfiguration = true,
    )
}

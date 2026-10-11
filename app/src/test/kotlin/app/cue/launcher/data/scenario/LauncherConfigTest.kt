package app.cue.launcher.data.scenario

import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.layout.ComponentRef
import app.cue.launcher.data.layout.LauncherItem
import app.cue.launcher.data.layout.LayoutContainer
import app.cue.launcher.data.layout.staticLayoutOf
import app.cue.launcher.data.layout.withId
import app.cue.launcher.data.settings.AppearanceSettings
import app.cue.launcher.data.settings.LauncherSettings
import app.cue.launcher.data.widgets.HostedWidget
import app.cue.launcher.data.widgets.PanelItem
import app.cue.launcher.system.launcherConfigOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LauncherConfigTest {

    private val chrome = ComponentRef("com.android.chrome", "com.google.android.apps.chrome.Main", 0)
    private val dialer = ComponentRef("com.google.android.dialer", "…DialtactsActivity", 0)
    private val calendarProvider = ComponentRef("com.google.android.calendar", "…MonthWidgetProvider", 0)

    private val layout = staticLayoutOf(
        listOf(
            LauncherItem.Shortcut(id = 1, container = LayoutContainer.GRID, position = 0, component = chrome),
            LauncherItem.Shortcut(id = 2, container = LayoutContainer.GRID, position = 5, component = dialer),
            LauncherItem.Shortcut(id = 3, container = LayoutContainer.DOCK, position = 0, component = dialer),
            LauncherItem.Shortcut(id = 4, container = LayoutContainer.PANEL_DOCK, position = 3, component = chrome),
        ),
    )

    private val hostedCalendar = HostedWidget(
        appWidgetId = 8,
        provider = calendarProvider,
        boundAt = 1_758_000_000_000,
    )

    private val panel = listOf(PanelItem.AppWidget(hostedCalendar))

    private val providers = mapOf(hostedCalendar.appWidgetId to calendarProvider)

    private fun config(items: List<LauncherItem> = emptyList()): StaticZoneConfig =
        staticZoneConfigOf(staticLayoutOf(items), panel, providers)

    @Test
    fun `a static zone lists only occupied positions`() {
        val config = staticZoneConfigOf(layout, panel, providers)

        assertEquals(listOf(0, 5), config.grid.map { it.position })
        assertEquals(listOf(0), config.dock.map { it.position })
        assertEquals(listOf(3), config.widgetPanel.dock.map { it.position })
        assertEquals(
            StaticZoneEntry.Shortcut(position = 0, packageName = chrome.packageName, component = chrome.className),
            config.grid.first(),
        )
    }

    @Test
    fun `a panel widget exports as a placeholder`() {
        val entry = staticZoneConfigOf(layout, panel, providers)
            .widgetPanel.widgets.single() as PanelEntry.AppWidget

        assertEquals("{{PLACEHOLDER:widget_8}}", entry.appWidgetId)
        assertEquals("com.google.android.calendar", entry.packageName)
        assertEquals(0, entry.position)
    }

    @Test
    fun `a panel column carries both kinds of widget`() {
        val column = listOf(
            PanelItem.LauncherWidget(id = 1, widget = LauncherWidgetType.CLOCK, position = 0),
            PanelItem.AppWidget(hostedCalendar),
        )

        val entries = staticZoneConfigOf(layout, column, providers).widgetPanel.widgets

        assertEquals(
            listOf<PanelEntry>(
                PanelEntry.LauncherWidget(position = 0, widget = LauncherWidgetType.CLOCK),
                PanelEntry.AppWidget(
                    position = 1,
                    packageName = calendarProvider.packageName,
                    provider = calendarProvider.className,
                    appWidgetId = "{{PLACEHOLDER:widget_8}}",
                ),
            ),
            entries,
        )
    }

    @Test
    fun `a folder exports along with its contents`() {
        val folder = LauncherItem.Folder(
            id = 7,
            container = LayoutContainer.GRID,
            position = 2,
            name = "Nástroje",
            items = listOf(chrome, dialer),
        )

        val entry = config(listOf(folder)).grid.single() as StaticZoneEntry.Folder

        assertEquals("Nástroje", entry.name)
        assertEquals(listOf("com.android.chrome", "com.google.android.dialer"), entry.items.map { it.packageName })
        assertEquals(listOf(folder), launcherItemsOf(config(listOf(folder)), userSerial = 0).map { it.withId(7) })
    }

    @Test
    fun `a searchbar exports once`() {
        val searchbar = LauncherItem.LauncherWidget(
            id = 9,
            container = LayoutContainer.GRID,
            position = 4,
            widget = LauncherWidgetType.SEARCHBAR,
        )

        val entries = config(listOf(searchbar)).grid

        assertEquals(
            listOf(StaticZoneEntry.LauncherWidget(position = 4, widget = LauncherWidgetType.SEARCHBAR)),
            entries,
        )
    }

    @Test
    fun `a grid widget carries the provider and its span`() {
        val widget = LauncherItem.AppWidget(
            id = 11,
            container = LayoutContainer.GRID,
            position = 4,
            appWidgetId = 8,
            columnSpan = 4,
            rowSpan = 2,
        )

        val entry = config(listOf(widget)).grid.single() as StaticZoneEntry.AppWidget

        assertEquals(calendarProvider.packageName, entry.packageName)
        assertEquals(calendarProvider.className, entry.provider)
        assertEquals("{{PLACEHOLDER:widget_8}}", entry.appWidgetId)
        assertEquals(4 to 2, entry.columnSpan to entry.rowSpan)
    }

    @Test
    fun `a widget with no registry entry is not exported`() {
        val widget = LauncherItem.AppWidget(
            id = 11,
            container = LayoutContainer.GRID,
            position = 4,
            appWidgetId = 99,
            columnSpan = 2,
            rowSpan = 1,
        )

        assertTrue(config(listOf(widget)).grid.isEmpty())
    }

    @Test
    fun `a backup lists widgets to be rebound`() {
        val widget = LauncherItem.AppWidget(
            id = 11,
            container = LayoutContainer.GRID,
            position = 4,
            appWidgetId = 8,
            columnSpan = 4,
            rowSpan = 2,
        )

        val widgets = appWidgetsIn(config(listOf(widget)))

        assertEquals(
            listOf(ConfigAppWidget("widget_8", calendarProvider.packageName, calendarProvider.className)),
            widgets,
        )
    }

    @Test
    fun `an unbound widget is not placed into the grid`() {
        val widget = LauncherItem.AppWidget(
            id = 11,
            container = LayoutContainer.GRID,
            position = 4,
            appWidgetId = 8,
            columnSpan = 4,
            rowSpan = 2,
        )
        val zone = config(listOf(widget))

        assertTrue(launcherItemsOf(zone, userSerial = 0).isEmpty())
        val restored = launcherItemsOf(zone, userSerial = 0, appWidgetIds = mapOf("widget_8" to 42))
        assertEquals(42, (restored.single() as LauncherItem.AppWidget).appWidgetId)
    }

    @Test
    fun `import returns shortcuts to the same positions`() {
        val config = staticZoneConfigOf(layout, panel, providers)

        val items = launcherItemsOf(config, userSerial = 0)

        assertEquals(
            setOf(
                LayoutContainer.GRID to 0,
                LayoutContainer.GRID to 5,
                LayoutContainer.DOCK to 0,
                LayoutContainer.PANEL_DOCK to 3,
            ),
            items.map { it.container to it.position }.toSet(),
        )
        assertTrue(items.all { it.id == 0L }) { "Room assigns the id" }
    }

    @Test
    fun `import places shortcuts into the given profile`() {
        val items = launcherItemsOf(staticZoneConfigOf(layout, panel, providers), userSerial = 11)

        assertTrue(items.all { (it as LauncherItem.Shortcut).component.userSerial == 11L })
    }

    @Test
    fun `a position outside the container is dropped`() {
        val config = StaticZoneConfig(
            grid = listOf(StaticZoneEntry.Shortcut(position = 99, packageName = "com.x", component = "com.x.M")),
        )

        assertTrue(launcherItemsOf(config, userSerial = 0).isEmpty())
    }

    @Test
    fun `a wide item outside its row is dropped`() {
        val config = StaticZoneConfig(
            grid = listOf(StaticZoneEntry.LauncherWidget(position = 3, widget = LauncherWidgetType.SEARCHBAR)),
        )

        assertTrue(launcherItemsOf(config, userSerial = 0).isEmpty())
    }

    @Test
    fun `the whole configuration survives a roundtrip`() {
        val config = LauncherConfig(
            staticZone = staticZoneConfigOf(layout, panel, providers),
            appearance = AppearanceConfig(
                iconMode = IconMode.TINTED,
                iconOverrides = listOf(ComponentEntry("com.spotify.music", "com.spotify.Main")),
                accent = AccentSource.CUSTOM,
                customAccent = "#3A86FF",
            ),
            drawer = DrawerConfig(
                contacts = true,
                aiRow = true,
                webRow = false,
                hiddenApps = listOf(ComponentEntry("com.android.chrome", "com.android.chrome.Main")),
            ),
            gestures = GesturesConfig(swipeLeft = TapAction.App("com.spotify.music")),
            widgets = WidgetsConfig(mediaApp = "com.spotify.music"),
        )
        val scenario = Scenario(
            id = "s1",
            name = "Scénář",
            size = TileSize.GRID_1X1,
            content = TileContent.Template(
                layout = TemplateLayout.ICON_TEXT,
                items = listOf(TemplateItem(actions = listOf(TapAction.App("com.x")))),
            ),
        )

        val read = ScenarioFormat.read(ScenarioFormat.encodeConfig(listOf(scenario), config))

        assertTrue(read is ScenarioRead.Ok) { read.toString() }
        val ok = read as ScenarioRead.Ok
        assertEquals(listOf(scenario), ok.accepted.map { it.scenario })
        assertEquals(config, ok.config)
        assertEquals(null, ok.setName) { "a backup is not a named set" }
    }

    @Test
    fun `missing configuration sections get defaults`() {
        val read = ScenarioFormat.read("""{"schemaVersion":1,"scenarios":[],"appearance":{}}""")

        val config = requireNotNull((read as ScenarioRead.Ok).config)
        assertEquals(IconMode.NATIVE, config.appearance.iconMode)
        assertEquals(DrawerConfig(), config.drawer)
        assertEquals(StaticZoneConfig(), config.staticZone)
        assertEquals(null, config.gestures.swipeLeft)
        assertTrue(config.drawer.hiddenApps.isEmpty())
        assertEquals(null, config.widgets.mediaApp)
        assertEquals(AccentSource.PRIMARY, config.appearance.effectiveAccent)
        assertEquals(null, config.appearance.customAccentRgb)
    }

    @Test
    fun `an accent this build does not know reads as the system one and keeps the configuration`() {
        val read = ScenarioFormat.read(
            """{"schemaVersion":1,"scenarios":[],"appearance":{"iconMode":"tinted","accent":"secondary"}}""",
        )

        val appearance = requireNotNull((read as ScenarioRead.Ok).config).appearance
        assertEquals(AccentSource.PRIMARY, appearance.accent)
        assertEquals(IconMode.TINTED, appearance.iconMode)
    }

    @Test
    fun `an accent that is not a name reads as the system one and keeps the configuration`() {
        listOf("null", "2", "true").forEach { value ->
            val read = ScenarioFormat.read(
                """{"schemaVersion":1,"scenarios":[],"appearance":{"iconMode":"tinted","accent":$value}}""",
            )

            val appearance = requireNotNull((read as? ScenarioRead.Ok)?.config) { value }.appearance
            assertEquals(AccentSource.PRIMARY, appearance.accent) { value }
            assertEquals(IconMode.TINTED, appearance.iconMode) { value }
        }
    }

    @Test
    fun `a custom colour that is not a string reads as none and keeps the configuration`() {
        listOf("3835647", "true", """{"hex":"#3A86FF"}""", """["#3A86FF"]""").forEach { value ->
            val read = ScenarioFormat.read(
                """{"schemaVersion":1,"scenarios":[],""" +
                    """"appearance":{"iconMode":"tinted","accent":"custom","customAccent":$value}}""",
            )

            val appearance = requireNotNull((read as? ScenarioRead.Ok)?.config) { value }.appearance
            assertEquals(null, appearance.customAccent) { value }
            assertEquals(AccentSource.PRIMARY, appearance.effectiveAccent) { value }
            assertEquals(IconMode.TINTED, appearance.iconMode) { value }
        }
    }

    @Test
    fun `a custom accent without a usable colour falls back to the system one`() {
        listOf(""""customAccent":"#12345"""", """"customAccent":"blue"""", """"customAccent":null""", "")
            .forEach { field ->
                val separator = if (field.isEmpty()) "" else ","
                val read = ScenarioFormat.read(
                    """{"schemaVersion":1,"scenarios":[],"appearance":{"accent":"custom"$separator$field}}""",
                )

                val appearance = requireNotNull((read as ScenarioRead.Ok).config) { field }.appearance
                assertEquals(AccentSource.PRIMARY, appearance.effectiveAccent) { field }
                assertEquals(null, appearance.customAccentRgb) { field }
            }
    }

    @Test
    fun `a custom accent is read whichever way its hex is written`() {
        val read = ScenarioFormat.read(
            """{"schemaVersion":1,"scenarios":[],"appearance":{"accent":"custom","customAccent":"3a86ff"}}""",
        )

        val appearance = requireNotNull((read as ScenarioRead.Ok).config).appearance
        assertEquals(AccentSource.CUSTOM, appearance.effectiveAccent)
        assertEquals(0x3A86FF, appearance.customAccentRgb)
    }

    @Test
    fun `the accent leaves the settings as a choice and a hex colour`() {
        val stored = LauncherSettings(
            appearance = AppearanceSettings(accent = AccentSource.TERTIARY, customAccent = 0x0A86FF),
        )

        val appearance = launcherConfigOf(stored, StaticZoneConfig(), emptyList(), emptyList()).appearance

        assertEquals(AccentSource.TERTIARY, appearance.accent)
        assertEquals("#0A86FF", appearance.customAccent) { "a colour kept for later travels along" }
        val exported = ScenarioFormat.encodeConfig(emptyList(), LauncherConfig(appearance = appearance))
        assertTrue(exported.contains(""""accent": "tertiary"""")) { exported }
    }

    @Test
    fun `the widget settings alone make a configuration`() {
        val read = ScenarioFormat.read(
            """{"schemaVersion":1,"scenarios":[],"widgets":{"mediaApp":"com.spotify.music"}}""",
        )

        assertEquals("com.spotify.music", requireNotNull((read as ScenarioRead.Ok).config).widgets.mediaApp)
    }

    @Test
    fun `an unknown item kind drops only that item`() {
        val json = """
            {"schemaVersion":1,"scenarios":[],"staticZone":{
              "grid":[{"position":0,"kind":"teleport","package":"com.x"},
                      {"position":1,"kind":"shortcut","package":"com.x","component":"com.x.M"}],
              "widgetPanel":{"widgets":[{"position":0,"kind":"hologram"}]}}}
        """.trimIndent()

        val read = ScenarioFormat.read(json)

        val zone = requireNotNull((read as ScenarioRead.Ok).config).staticZone
        assertEquals(listOf(1), zone.grid.map { it.position })
        assertTrue(zone.widgetPanel.widgets.isEmpty())
    }

    @Test
    fun `a named set is not a configuration`() {
        val read = ScenarioFormat.read("""{"schemaVersion":1,"name":"Sada","scenarios":[]}""")

        val ok = read as ScenarioRead.Ok
        assertEquals("Sada", ok.setName)
        assertEquals(null, ok.config)
    }

    @Test
    fun `the AI chat settings leave no trace in an exported configuration`() {
        val hidden = listOf(componentEntryOf(dialer))
        fun export(settings: LauncherSettings) = ScenarioFormat.encodeConfig(
            emptyList(),
            launcherConfigOf(settings, staticZoneConfigOf(layout, panel, providers), emptyList(), hidden),
        )
        val off = LauncherSettings(aiChatScenarios = false)
        val used = off.copy(
            aiChatScenarios = true,
            aiChatShare = AiChatShare(bluetooth = false, homeAssistant = false, shortcuts = false),
            aiChatPending = true,
        )

        assertEquals(export(off), export(used))
        assertFalse(export(used).contains("aiChat", ignoreCase = true))
    }
}

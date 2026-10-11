package app.cue.launcher.system.aichat

import app.cue.launcher.data.aichat.AiChatOffer
import app.cue.launcher.data.aichat.AiChatShare
import app.cue.launcher.data.aichat.CandidateCheck
import app.cue.launcher.data.aichat.InventoryApp
import app.cue.launcher.data.aichat.InventoryAppAction
import app.cue.launcher.data.aichat.InventoryDevice
import app.cue.launcher.data.aichat.InventorySnapshot
import app.cue.launcher.data.aichat.InventoryWidget
import app.cue.launcher.data.aichat.checkCandidate
import app.cue.launcher.data.aichat.groundingProblems
import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.scenario.ConditionType
import app.cue.launcher.data.scenario.ExtractKind
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.system.widget.WidgetBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Locale

class AiChatContextTest {

    private val english = Locale.forLanguageTag("en-US")

    private val maps = InventoryApp("Mapy", "com.google.android.apps.maps", englishLabel = "Maps")

    private val phone = InventorySnapshot(
        apps = listOf(maps, InventoryApp("Spotify", "com.spotify.music")),
        devices = listOf(InventoryDevice("Octavia", "00:1A:7D:DA:71:13")),
        haEntities = listOf(HaEntity("cover.gate", "Gate")),
        widgets = listOf(InventoryWidget("Weather", "Forecast", "com.forecast", "com.forecast.WeatherWidget")),
        appActions = mapOf(
            maps.packageName to listOf(InventoryAppAction("Home", TapAction.Shortcut(maps.packageName, "home"))),
        ),
    )

    @Test
    fun `every condition, content, layout, action and enum value of the format is named`() {
        val text = aiChatContext(phone, english, TEST_ROUTE)

        val names = ConditionType.entries.map { it.jsonName } + CONTENT_KINDS + ACTION_KINDS +
            TemplateLayout.entries.map { it.jsonName } + LauncherWidgetType.entries.map { it.jsonName } +
            SystemActionType.entries.map { it.jsonName } + ExtractKind.entries.map { it.jsonName }
        names.forEach { name -> assertTrue("- `$name`: " in text, name) }
    }

    @Test
    fun `a system action the phone cannot run is named in the inventory, and only then`() {
        val withoutGoogleTv = phone.copy(unavailableSystemActions = setOf(SystemActionType.TV_REMOTE))

        val missing = aiChatContext(withoutGoogleTv, english, TEST_ROUTE).substringAfter("## This phone")
        val present = aiChatContext(phone, english, TEST_ROUTE).substringAfter("## This phone")

        assertTrue("### System actions" in missing && "`tv_remote`" in missing, missing)
        assertFalse("### System actions" in present, present)
    }

    @Test
    fun `every example passes the import gate and holds against its own inventory lines`() {
        SCENARIO_EXAMPLES.forEach { example ->
            val check = checkCandidate(example.json)
            val valid = assertInstanceOf(CandidateCheck.Valid::class.java, check, example.request)

            val problems = groundingProblems(valid.scenario, snapshotOf(example.inventory))
            assertEquals(emptyList<Any>(), problems, example.request)
        }
    }

    @Test
    fun `every example travels whole, with its request and inventory lines`() {
        val text = aiChatContext(phone, english, TEST_ROUTE)

        SCENARIO_EXAMPLES.forEach { example ->
            assertTrue("```json\n${example.json}\n```" in text, example.request)
            assertTrue(example.request in text && example.inventory.all { it in text }, example.request)
        }
    }

    @Test
    fun `links are taught as the way past what the inventory lists`() {
        val text = aiChatContext(phone, english, TEST_ROUTE)
        val links = text.substringAfter("## Links and deep links").substringBefore("\n## ")

        assertTrue("- `deeplink`: " in text)
        listOf("https", "intent://", "browser_fallback_url", "Search the web", "could not verify").forEach {
            assertTrue(it in links, it)
        }
    }

    @Test
    fun `the inventory travels in full, each identifier verbatim`() {
        val text = aiChatContext(phone, english, TEST_ROUTE)

        inventoryLines(phone).forEach { line -> assertTrue("\n$line\n" in text, line) }
        assertTrue("Services: " in text)
    }

    @Test
    fun `the same snapshot always gives the same text`() {
        val reordered = phone.copy(appActions = phone.appActions.entries.reversed().associate { it.toPair() })

        assertEquals(aiChatContext(phone, english, TEST_ROUTE), aiChatContext(phone, english, TEST_ROUTE))
        assertEquals(aiChatContext(phone, english, TEST_ROUTE), aiChatContext(reordered, english, TEST_ROUTE))
    }

    @Test
    fun `what could not be read says so instead of an empty list`() {
        val text = aiChatContext(InventorySnapshot.EMPTY, english, TEST_ROUTE)

        listOf(
            "No apps could be read",
            "Cue: has no Bluetooth permission",
            "Home Assistant is not connected to Cue:",
            "The widget list could not be read",
        ).forEach { assertTrue(it in text, it) }
        assertFalse("```text\n```" in text)
    }

    @Test
    fun `an empty list says that nothing is there`() {
        val empty = phone.copy(devices = emptyList(), haEntities = emptyList(), widgets = emptyList())

        val text = aiChatContext(empty, english, TEST_ROUTE)

        listOf("No Bluetooth device is paired", "lists no entities", "No installed app offers a widget").forEach {
            assertTrue(it in text, it)
        }
    }

    @Test
    fun `what the user kept back says so, apart from what could not be read`() {
        val nothing = AiChatShare(bluetooth = false, homeAssistant = false, shortcuts = false)

        val unknown = phone.copy(devices = null, haEntities = null, appActions = emptyMap())

        val kept = aiChatContext(unknown, english, TEST_ROUTE, nothing)
        val unread = aiChatContext(unknown, english, TEST_ROUTE)

        val keptBack = listOf(
            "The user chose not to share their Bluetooth devices",
            "The user chose not to share their Home Assistant entities and services",
            "The user chose not to share the apps' shortcuts",
        )
        val cannotRead = listOf(
            "Cue: has no Bluetooth permission",
            "Home Assistant is not connected to Cue:",
            "No app shortcuts are listed",
        )
        keptBack.forEach { assertTrue(it in kept, it) }
        cannotRead.forEach { assertFalse(it in kept, it) }
        cannotRead.forEach { assertTrue(it in unread, it) }
        keptBack.forEach { assertFalse(it in unread, it) }
        assertTrue("Do not ask for a device's name or address" in kept && "`{{PLACEHOLDER:bt_<name>}}`" in kept)
        assertTrue("Do not ask for entity ids" in kept)
    }

    @Test
    fun `what the phone does not offer reads as unavailable, not as kept back, and is never listed`() {
        val nothing = AiChatOffer(bluetooth = false, homeAssistant = false)

        val text = phoneSection(aiChatContext(phone, english, TEST_ROUTE, AiChatShare.ALL, nothing))
        val declined = phoneSection(
            aiChatContext(phone, english, TEST_ROUTE, AiChatShare(bluetooth = false, homeAssistant = false), nothing),
        )

        listOf(text, declined).forEach { section ->
            assertTrue("Cue: has no Bluetooth permission" in section, section)
            assertTrue("Home Assistant is not connected to Cue:" in section, section)
            assertFalse("The user chose not to share" in section, section)
            listOf("00:1A:7D:DA:71:13", "cover.gate").forEach { assertFalse(it in section, it) }
        }
    }

    @Test
    fun `what the user kept back leaves no line of it behind`() {
        val nothing = AiChatShare(bluetooth = false, homeAssistant = false, shortcuts = false)

        val text = phoneSection(aiChatContext(phone, english, TEST_ROUTE, nothing))

        assertTrue("\napp Mapy (Maps) → com.google.android.apps.maps\n" in text, text)
        listOf("00:1A:7D:DA:71:13", "cover.gate", "shortcut Home").forEach { assertFalse(it in text, it) }
        assertTrue("widget Weather (Forecast) → com.forecast/com.forecast.WeatherWidget" in text)
    }

    @Test
    fun `shortcuts kept back take the capabilities with them, links and screens still go`() {
        val pkg = maps.packageName
        val actions = listOf(
            InventoryAppAction("Alice Novak", TapAction.Shortcut(pkg, "chat-alice")),
            InventoryAppAction("Directions", TapAction.DeepLink("google.navigation:q=home")),
            InventoryAppAction("Navigate", TapAction.Capability(pkg, "actions.intent.GET_DIRECTIONS")),
            InventoryAppAction("Offline maps", TapAction.ActivityLaunch(pkg, "$pkg.OfflineMapsActivity")),
        )
        val withActions = phone.copy(appActions = mapOf(pkg to actions))

        val text = phoneSection(aiChatContext(withActions, english, TEST_ROUTE, AiChatShare(shortcuts = false)))

        assertFalse("Alice" in text || "chat-alice" in text || "GET_DIRECTIONS" in text, text)
        assertFalse("\nshortcut " in text || "\ncapability " in text, text)
        listOf(
            "deeplink Directions → google.navigation:q=home",
            "activity Offline maps → $pkg/$pkg.OfflineMapsActivity",
            "The user chose not to share the apps' shortcuts",
        ).forEach { assertTrue(it in text, it) }
        assertFalse("No app shortcuts are listed" in text)
    }

    @Test
    fun `without any shortcut the brief says why and steers away from them`() {
        val without = aiChatContext(phone.copy(appActions = emptyMap()), english, TEST_ROUTE)

        assertTrue("No app shortcuts are listed" in without)
        assertFalse("No app shortcuts are listed" in aiChatContext(phone, english, TEST_ROUTE))
    }

    @Test
    fun `screens without a label of their own are left out and the rest capped`() {
        val screens = (1..10).map { TapAction.ActivityLaunch("com.app", "com.app.Screen${it}Activity") }
        val labels = listOf("Screen1Activity", "My app") + (3..10).map { "Screen $it" }
        val entries = listOf(InventoryAppAction("Home", TapAction.Shortcut("com.app", "home"))) +
            screens.zip(labels) { screen, label -> InventoryAppAction(label, screen) }

        val kept = chatActions(entries, appLabel = "My App")

        assertEquals(listOf("Home") + (3..6).map { "Screen $it" }, kept.map { it.label })
    }

    @Test
    fun `the first question goes out in the phone's language`() {
        val text = aiChatContext(phone, Locale.forLanguageTag("cs-CZ"), TEST_ROUTE)

        assertTrue("The phone is set to Czech (cs-CZ), so ask your first question in" in text, text)
        assertTrue("Start by asking what the scenario should do" in text)
    }

    @Test
    fun `the chat asks what changes the tile, in one message, and chooses the details itself`() {
        val role = aiChatContext(phone, english, TEST_ROUTE).substringAfter("## Your role").substringBefore("\n## ")

        listOf("Never guess these", "numbered list", "Choose them yourself", "into one message", "no summary").forEach {
            assertTrue(it in role, it)
        }
    }

    @Test
    fun `the brief lists the tile sizes each widget fits`() {
        val slots = mapOf(
            TileSize.GRID_2X2 to WidgetBox(widthPx = 400, heightPx = 400),
            TileSize.HERO_4X2 to WidgetBox(widthPx = 900, heightPx = 400),
        )
        val wide = phone.copy(widgets = phone.widgets.orEmpty().map { it.copy(smallestWidthPx = 800) })

        assertTrue("\nwidget Weather (Forecast) [4x2] → com.forecast/com.forecast.WeatherWidget\n" in
            aiChatContext(wide, english, TEST_ROUTE, slots = slots))
        val tooBig = wide.copy(widgets = wide.widgets.orEmpty().map { it.copy(smallestHeightPx = 800) })
        assertTrue("No installed app offers a widget that fits a tile" in
            aiChatContext(tooBig, english, TEST_ROUTE, slots = slots))
    }

    @Test
    fun `the answer is asked for as one json block holding one scenario`() {
        val text = aiChatContext(phone, english, TEST_ROUTE)
        val answer = text.substringAfter("## Your final answer")

        assertTrue(text.trimEnd().endsWith(answer.trimEnd()), "the answer's rules come last")
        assertTrue("exactly one ```json fenced block holding exactly one scenario" in answer)
        assertTrue("no comments" in answer && "One scenario per answer" in answer)
    }

    @Test
    fun `a full phone makes a brief of a size a chat can take`() {
        val text = aiChatContext(realisticInventory(), english, TEST_ROUTE)

        val sample = File(rootDir(), "build/aichat/context-sample.md")
        sample.parentFile?.mkdirs()
        sample.writeText(text)
        println("AI chat brief: ${text.length} characters, ${text.toByteArray().size} bytes UTF-8 → $sample")
        assertTrue(text.length < MAX_REALISTIC_LENGTH, "${text.length} characters")
    }

    private fun phoneSection(text: String): String =
        text.substringAfter("## This phone").substringBefore("## Your final answer")

    // Back from the lines to what they list, so grounding sees the example's own phone and nothing more.
    private fun snapshotOf(lines: List<String>): InventorySnapshot {
        fun identifiers(kind: String) = lines.filter { it.startsWith("$kind ") }.map { it.substringAfter("→ ") }
        val shortcuts = identifiers("shortcut").map { line ->
            TapAction.Shortcut(line.substringBefore('/'), line.substringAfter('/'))
        }
        return InventorySnapshot(
            apps = identifiers("app").map { InventoryApp(it, it) },
            devices = identifiers("bt").map { InventoryDevice(null, it) }.ifEmpty { null },
            haEntities = identifiers("ha").map { HaEntity(it, null) }.ifEmpty { null },
            widgets = identifiers("widget")
                .map { InventoryWidget("", "", it.substringBefore('/'), it.substringAfter('/')) }
                .ifEmpty { null },
            appActions = shortcuts.groupBy({ it.packageName }, { InventoryAppAction("", it) }),
        )
    }

    private fun rootDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private companion object {
        // A guard against runaway growth, not a limit any chat app is known to have.
        const val MAX_REALISTIC_LENGTH = 150_000
    }
}

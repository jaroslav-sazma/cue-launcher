package app.cue.launcher.data.scenario

import app.cue.launcher.engine.NOTIFICATION_VALUE_NAMESPACE
import app.cue.launcher.engine.PostedNotification
import app.cue.launcher.engine.notificationPayload
import app.cue.launcher.engine.notificationTrigger
import app.cue.launcher.engine.resolveTemplateValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

class ScenarioFixturesTest {

    @Test
    fun `fixtures exist`() {
        assertTrue(fixtures().isNotEmpty()) { "there are no scenarios in $FIXTURE_DIR" }
    }

    @TestFactory
    fun `fixtures read without a defect`(): List<DynamicTest> = fixtures().map { file ->
        DynamicTest.dynamicTest(file.name) {
            val read = ScenarioFormat.read(file.readText())
            assertTrue(read is ScenarioRead.Ok) { "${file.name}: $read" }

            val ok = read as ScenarioRead.Ok
            assertTrue(ok.rejected.isEmpty()) { "${file.name}: ${ok.rejected}" }
            assertTrue(ok.accepted.isNotEmpty()) { "${file.name}: no scenario" }
            ok.accepted.forEach { imported ->
                assertEquals(emptyList<ScenarioDefect>(), validateScenario(imported.scenario))
            }
        }
    }

    @TestFactory
    fun `fixtures survive a roundtrip`(): List<DynamicTest> = fixtures().map { file ->
        DynamicTest.dynamicTest(file.name) {
            val original = scenariosOf(file)
            val reencoded = if (original.size == 1) {
                ScenarioFormat.encodeScenario(original.single())
            } else {
                ScenarioFormat.encodeSet("Set", original)
            }

            assertEquals(original, scenariosOf(reencoded))
        }
    }

    @Test
    fun `a fixture with a placeholder is stored disabled`() {
        val imported = importedFixture(FixtureFile.HA_GATE).single()

        assertEquals(setOf("ha_gate"), imported.placeholders)
        assertTrue(imported.needsPlaceholders)
        assertEquals(false, imported.asStored().enabled)
    }

    @Test
    fun `the hero widget fixture has three placeholders`() {
        val imported = importedFixture(FixtureFile.HERO_WIDGET).single()

        assertEquals(
            setOf("widget_hero_pkg", "widget_hero_provider", "widget_hero"),
            imported.placeholders,
        )
    }

    @Test
    fun `a fixture without placeholders stays enabled`() {
        val imported = importedFixture(FixtureFile.HEADPHONES).single()

        assertTrue(imported.placeholders.isEmpty())
        assertEquals(imported.scenario, imported.asStored())
        assertTrue(imported.scenario.enabled)
    }

    @Test
    fun `the 1x1 fallback fixture is five scenarios without conditions`() {
        val scenarios = importedFixture(FixtureFile.FALLBACK_1X1).map { it.scenario }

        assertEquals(5, scenarios.size)
        assertTrue(scenarios.all { it.isFallback })
        assertTrue(scenarios.all { it.size == TileSize.GRID_1X1 })
        assertEquals(listOf(50, 40, 30, 20, 10), scenarios.map { it.priority })
    }

    @Test
    fun `the 2x2 fallback fixture is four scenarios without conditions`() {
        val scenarios = importedFixture(FixtureFile.FALLBACK_2X2_STACK).map { it.scenario }

        assertEquals(4, scenarios.size)
        assertTrue(scenarios.all { it.isFallback })
        assertTrue(scenarios.all { it.size == TileSize.GRID_2X2 })
        assertEquals(listOf(9, 8, 7, 6), scenarios.map { it.priority })
    }

    @Test
    fun `the paired fixture carries two state conditions`() {
        val scenarios = importedFixture(FixtureFile.DND_PAIR).map { it.scenario }

        assertEquals(listOf("h1-meeting-dnd", "h2-dnd-off"), scenarios.map { it.id })
        assertEquals(listOf(Condition.CalendarEvent()), scenarios[0].conditions)
        assertEquals(listOf(Condition.RingerMode(RingerModeState.DND)), scenarios[1].conditions)
        assertTrue(scenarios.all { it.instantCondition == null })
    }

    @Test
    fun `the TV fixture holds while the television plays or pauses`() {
        val scenario = importedFixture(FixtureFile.HA_STATE_TV).single().scenario

        assertEquals(
            listOf(Condition.HaState(entity = "media_player.living_room_tv", states = listOf("playing", "paused"))),
            scenario.conditions,
        )
    }

    @Test
    fun `the TV fixture opens the Google TV remote`() {
        val content = importedFixture(FixtureFile.HA_STATE_TV).single().scenario.content as TileContent.Template

        assertEquals(listOf(TapAction.SystemAction(SystemActionType.TV_REMOTE)), content.items[1].actions)
    }

    @Test
    fun `the doorbell fixture carries a duration on the condition`() {
        val scenario = importedFixture(FixtureFile.HA_GATE).single().scenario

        assertEquals(Condition.HaEvent(code = "doorbell", durationSec = 60), scenario.instantCondition)
    }

    @Test
    fun `the notification fixture carries both the package and the search text`() {
        val scenarios = importedFixture(FixtureFile.NOTIFICATION_PARCEL).map { it.scenario }

        assertEquals(
            listOf(
                Condition.NotificationFrom(packageName = "cz.zasilkovna.app", durationSec = 900),
                Condition.NotificationFrom(
                    packageName = "cz.zasilkovna.app",
                    contains = "pickup code",
                    durationSec = 900,
                ),
            ),
            scenarios.map { it.instantCondition },
        )
    }

    @Test
    fun `the ride fixture carries both 2x2 layouts`() {
        val scenarios = importedFixture(FixtureFile.NOTIFICATION_RIDE).map { it.scenario }

        assertEquals(
            listOf(TemplateLayout.TEXT_ONLY, TemplateLayout.FOUR_ICONS),
            scenarios.map { (it.content as TileContent.Template).layout },
        )
        assertTrue(scenarios.all { it.instantCondition is Condition.NotificationFrom })
        assertTrue(scenarios.all { it.size == TileSize.GRID_2X2 })
    }

    @Test
    fun `the message fixture is 2x2 text only`() {
        val scenario = importedFixture(FixtureFile.NOTIFICATION_MESSAGE).single().scenario
        val content = scenario.content as TileContent.Template

        assertEquals(TemplateLayout.TEXT_ONLY, content.layout)
        assertEquals(TileSize.GRID_2X2, scenario.size)
        assertEquals(
            Condition.NotificationFrom(packageName = "com.google.android.apps.messaging", durationSec = 900),
            scenario.instantCondition,
        )
    }

    @Test
    fun `every extract fixture value is used by the template and nothing else is`() {
        val scenarios = importedFixture(FixtureFile.NOTIFICATION_EXTRACT).map { it.scenario }

        scenarios.forEach { scenario ->
            val rules = (scenario.instantCondition as Condition.NotificationFrom).extract
            assertTrue(rules.isNotEmpty()) { "${scenario.id}: the fixture is about extract rules" }

            val content = scenario.content as TileContent.Template
            val used = listOfNotNull(content.text, content.text2) + content.items.mapNotNull { it.label }
            assertEquals(
                rules.map { "{$NOTIFICATION_VALUE_NAMESPACE:${it.name}}" }.toSet(),
                used.filter { it.startsWith("{$NOTIFICATION_VALUE_NAMESPACE:") }.toSet(),
            )
        }
    }

    @Test
    fun `the parcel fixture fills its tile from a real looking notification`() {
        val scenario = importedFixture(FixtureFile.NOTIFICATION_EXTRACT).first().scenario
        val notification = PostedNotification(
            key = "parcel",
            packageName = "cz.zasilkovna.app",
            title = "Packeta",
            text = "Your parcel is ready. Pickup code: 482913. Pickup point: 5 Main St, Springfield",
        )

        val tile = filledTile(scenario, notification)

        assertEquals("482913", tile.text)
        assertEquals("5 Main St, Springfield", tile.text2)
    }

    @Test
    fun `the card payment fixture fills its tile with the amount and its currency`() {
        val scenario = importedFixture(FixtureFile.NOTIFICATION_EXTRACT).last().scenario
        val notification = PostedNotification(
            key = "payment",
            packageName = "cz.csob.smartbanking",
            title = "CSOB Smart",
            text = "Card payment of €1,249.00 at Albert",
        )

        assertEquals("€1,249.00", filledTile(scenario, notification).items.single().label)
    }

    private fun filledTile(scenario: Scenario, notification: PostedNotification): TileContent.Template {
        val condition = notificationTrigger(scenario, notification)
        assertTrue(condition != null) { "${scenario.id} does not react to the notification" }
        val payload = notificationPayload(notification, condition!!.extract)
        return resolveTemplateValues(scenario.content, payload) as TileContent.Template
    }

    private fun fixtures(): List<File> =
        FIXTURE_DIR.listFiles { file -> file.extension == "json" }?.sorted().orEmpty()

    private fun scenariosOf(file: File): List<Scenario> = scenariosOf(file.readText())

    private fun scenariosOf(text: String): List<Scenario> = importedOf(text).map { it.scenario }

    private fun importedFixture(name: String): List<ImportedScenario> =
        importedOf(File(FIXTURE_DIR, name).readText())

    private fun importedOf(text: String): List<ImportedScenario> {
        val read = ScenarioFormat.read(text)
        assertTrue(read is ScenarioRead.Ok) { read.toString() }
        return (read as ScenarioRead.Ok).accepted
    }

    private object FixtureFile {
        const val DND_PAIR = "dnd-pair.json"
        const val FALLBACK_1X1 = "fallback-1x1-set.json"
        const val FALLBACK_2X2_STACK = "fallback-2x2-stack.json"
        const val HERO_WIDGET = "fallback-hero-widget.json"
        const val HA_GATE = "ha-gate-button.json"
        const val HA_STATE_TV = "ha-state-tv.json"
        const val HEADPHONES = "headphones-music.json"
        const val NOTIFICATION_EXTRACT = "notification-extract-code.json"
        const val NOTIFICATION_MESSAGE = "notification-message.json"
        const val NOTIFICATION_PARCEL = "notification-parcel.json"
        const val NOTIFICATION_RIDE = "notification-ride.json"
    }

    private companion object {

        val FIXTURE_DIR: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "fixtures/scenarios") }
            .first { it.isDirectory }
    }
}

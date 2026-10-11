package app.cue.launcher.feature.settings.ha

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.engine.HA_CODE_MAX_LENGTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaCodesTest {

    @Test
    fun `a code without an event in the log has never arrived`() {
        val codes = haPairingCodes(listOf(haScenario("a", "brana")), lastSeen = emptyMap())

        assertEquals(1, codes.size)
        assertTrue(codes.single().neverSeen)
        assertEquals("brana", codes.single().code)
        assertEquals(1, codes.single().scenarios)
    }

    @Test
    fun `a differently written code is one row`() {
        val codes = haPairingCodes(
            scenarios = listOf(haScenario("a", "Zvonek"), haScenario("b", " zvonek ")),
            lastSeen = mapOf("zvonek" to 1_700L),
        )

        assertEquals(1, codes.size)
        assertEquals(2, codes.single().scenarios)
        assertEquals(1_700L, codes.single().lastSeenAt)
    }

    @Test
    fun `codes that never arrived come first`() {
        val codes = haPairingCodes(
            scenarios = listOf(
                haScenario("a", "zvonek"),
                haScenario("b", "brana"),
                haScenario("c", "alarm"),
            ),
            lastSeen = mapOf("zvonek" to 1_700L, "alarm" to 1_500L),
        )

        assertEquals(listOf("brana", "alarm", "zvonek"), codes.map { it.code })
    }

    @Test
    fun `a disabled scenario does not lose its code`() {
        val codes = haPairingCodes(
            scenarios = listOf(haScenario("a", "brana").copy(enabled = false)),
            lastSeen = emptyMap(),
        )

        assertEquals(listOf("brana"), codes.map { it.code })
    }

    @Test
    fun `a scenario without an ha event does not count`() {
        val codes = haPairingCodes(
            scenarios = listOf(
                haScenario("a", "  "),
                haScenario("b", "zvonek").copy(conditions = listOf(Condition.Charging())),
            ),
            lastSeen = emptyMap(),
        )

        assertEquals(emptyList<HaPairingCode>(), codes)
    }

    // The seen table is keyed by the incoming code, which is cut to the cap; a longer scenario code
    // has to find its row there all the same.
    @Test
    fun `a code longer than the cap finds the event that arrived cut`() {
        val long = "x".repeat(HA_CODE_MAX_LENGTH + 10)
        val codes = haPairingCodes(
            scenarios = listOf(haScenario("a", long)),
            lastSeen = mapOf(long.take(HA_CODE_MAX_LENGTH) to 1_700L),
        )

        assertEquals(1_700L, codes.single().lastSeenAt)
        assertEquals(long.take(HA_CODE_MAX_LENGTH), codes.single().code)
    }

    private fun haScenario(id: String, code: String): Scenario = Scenario(
        id = id,
        name = "Scénář $id",
        conditions = listOf(Condition.HaEvent(code = code, durationSec = 60)),
        size = TileSize.GRID_1X1,
        content = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = listOf(TapAction.App(packageName = "com.example")))),
        ),
    )
}

package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenarioDefectDescriptionTest {

    private val samples: List<ScenarioDefect> = listOf(
        ScenarioDefect.UnsupportedSchemaVersion(found = 7),
        ScenarioDefect.Malformed.NotJsonObject,
        ScenarioDefect.Malformed.ScenariosNotList,
        ScenarioDefect.Malformed.ConfigUnreadable,
        ScenarioDefect.Malformed.ScenarioNotObject,
        ScenarioDefect.Malformed.UnknownVersion(9),
        ScenarioDefect.Malformed.Unreadable("Unexpected token at offset 12"),
        ScenarioDefect.Malformed.Unreadable(null),
        ScenarioDefect.UnknownEnum(field = "conditions[0].type", value = "moon_phase"),
        ScenarioDefect.MissingField("wifi.ssid"),
        ScenarioDefect.OverlayUnsupported,
        ScenarioDefect.BlankId("Evening"),
        ScenarioDefect.NegativePriority(-3),
        ScenarioDefect.PinnedPositionOutOfRange(position = 3, size = TileSize.GRID_1X1),
        ScenarioDefect.MultipleInstantConditions(2),
        ScenarioDefect.DurationOnStateCondition("wifi"),
        ScenarioDefect.InvalidDuration(0),
        ScenarioDefect.LayoutSizeMismatch(TemplateLayout.FOUR_ICONS, TileSize.GRID_1X1),
        ScenarioDefect.ItemCountMismatch(TemplateLayout.TEXT_TWO_ICONS, expected = 2, actual = 3),
        ScenarioDefect.WidgetSizeMismatch(LauncherWidgetType.CALENDAR, TileSize.GRID_2X2),
        ScenarioDefect.WidgetSizeMismatch(LauncherWidgetType.SEARCHBAR, TileSize.GRID_2X2),
        ScenarioDefect.InvalidTime(field = "time_window.from", value = "25:00"),
        ScenarioDefect.InvalidDays(listOf(0, 8)),
        ScenarioDefect.TooManyExtractRules(count = 5),
        ScenarioDefect.InvalidHaEntity("the TV"),
    ) + ExtractProblem.entries.map { ScenarioDefect.InvalidExtractRule(name = "parcel_code", problem = it) }

    @Test
    fun `every defect variant has a sample`() {
        val covered = samples.map { it::class.java }.toSet()

        assertEquals(leafVariants(ScenarioDefect::class.java), covered)
    }

    @Test
    fun `every description is a distinct one-liner`() {
        val descriptions = samples.map { it.description() }

        assertTrue(descriptions.all { it.isNotBlank() && '\n' !in it }, "blank or multi-line: $descriptions")
        assertEquals(descriptions.size, descriptions.toSet().size, "duplicates in $descriptions")
    }

    @Test
    fun `the item count names the layout and both counts`() {
        assertEquals(
            "Layout \"text_two_icons\" needs exactly 2 items, got 3.",
            ScenarioDefect.ItemCountMismatch(TemplateLayout.TEXT_TWO_ICONS, expected = 2, actual = 3).description(),
        )
    }

    @Test
    fun `an unknown enum quotes the field and the value`() {
        assertEquals(
            "Field \"conditions[0].type\" has unknown value \"moon_phase\".",
            ScenarioDefect.UnknownEnum(field = "conditions[0].type", value = "moon_phase").description(),
        )
    }

    @Test
    fun `an invalid time quotes the raw value`() {
        assertEquals(
            "\"time_window.from\" must be HH:mm (00:00-23:59), got \"25:00\".",
            ScenarioDefect.InvalidTime(field = "time_window.from", value = "25:00").description(),
        )
    }

    @Test
    fun `a layout size mismatch names the size the layout needs`() {
        assertEquals(
            "Layout \"four_icons\" needs size \"2x2\", got \"1x1\".",
            ScenarioDefect.LayoutSizeMismatch(TemplateLayout.FOUR_ICONS, TileSize.GRID_1X1).description(),
        )
    }

    @Test
    fun `the pinned position stays zero-based like the JSON field`() {
        assertEquals(
            "\"pinnedPosition\" 3 does not fit size \"1x1\".",
            ScenarioDefect.PinnedPositionOutOfRange(position = 3, size = TileSize.GRID_1X1).description(),
        )
    }

    private fun leafVariants(type: Class<*>): Set<Class<*>> {
        val permitted = type.permittedSubclasses ?: return setOf(type)
        return permitted.flatMap { leafVariants(it) }.toSet()
    }
}

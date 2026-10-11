package app.cue.launcher.system.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetBindStageTest {

    @Test
    fun `without permission, the system dialog is requested`() {
        assertEquals(
            WidgetBindStage.PERMISSION,
            widgetBindStage(bound = false, needsConfiguration = false),
        )
        assertEquals(
            WidgetBindStage.PERMISSION,
            widgetBindStage(bound = false, needsConfiguration = true),
        )
    }

    @Test
    fun `a bound widget with configuration goes to configuration`() {
        assertEquals(
            WidgetBindStage.CONFIGURE,
            widgetBindStage(bound = true, needsConfiguration = true),
        )
    }

    @Test
    fun `a bound widget without configuration is done`() {
        assertEquals(
            WidgetBindStage.DONE,
            widgetBindStage(bound = true, needsConfiguration = false),
        )
    }

    @Test
    fun `settings only offer a widget with both configuration and the flag`() {
        assertTrue(widgetReconfigurable(hasConfigureActivity = true, widgetFeatures = RECONFIGURABLE))
    }

    @Test
    fun `configuration without the flag is not offered again`() {
        assertFalse(widgetReconfigurable(hasConfigureActivity = true, widgetFeatures = 0))
        assertFalse(
            widgetReconfigurable(hasConfigureActivity = true, widgetFeatures = CONFIGURATION_OPTIONAL),
        )
    }

    @Test
    fun `a widget without configuration has no settings`() {
        assertFalse(widgetReconfigurable(hasConfigureActivity = false, widgetFeatures = RECONFIGURABLE))
        assertFalse(widgetReconfigurable(hasConfigureActivity = false, widgetFeatures = 0))
    }

    @Test
    fun `other flags do not change the decision`() {
        val features = RECONFIGURABLE or CONFIGURATION_OPTIONAL or HIDE_FROM_PICKER
        assertTrue(widgetReconfigurable(hasConfigureActivity = true, widgetFeatures = features))
    }

    private companion object {

        const val RECONFIGURABLE = 0x1
        const val HIDE_FROM_PICKER = 0x2
        const val CONFIGURATION_OPTIONAL = 0x4
    }
}

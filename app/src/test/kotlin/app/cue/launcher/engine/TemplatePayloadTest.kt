package app.cue.launcher.engine

import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TemplatePayloadTest {

    private val payload = TriggerPayload(
        icon = "mdi:washing-machine",
        fields = mapOf("ha:teplota" to "38", "ha:program" to "bavlna"),
    )

    private fun template(
        text: String? = null,
        text2: String? = null,
        items: List<TemplateItem> = listOf(item()),
    ) = TileContent.Template(layout = TemplateLayout.TEXT_TWO_ICONS, text = text, text2 = text2, items = items)

    private fun item(
        label: String? = null,
        icon: String? = null,
        action: TapAction = TapAction.HaAction(service = "cover.open_cover"),
    ) = TemplateItem(label = label, icon = icon, actions = listOf(action))

    @Test
    fun `values are substituted into both texts and into the item label`() {
        val content = template(
            text = "Prádlo {ha:teplota} °C",
            text2 = "Program {ha:program}, {ha:teplota} stupňů",
            items = listOf(item(label = "Zbývá {ha:teplota}")),
        )

        val resolved = resolveTemplateValues(content, payload) as TileContent.Template

        assertEquals("Prádlo 38 °C", resolved.text)
        assertEquals("Program bavlna, 38 stupňů", resolved.text2)
        assertEquals("Zbývá 38", resolved.items.single().label)
    }

    @Test
    fun `a missing value discards the content`() {
        assertNull(resolveTemplateValues(template(text = "{ha:vlhkost}"), payload))
        assertNull(resolveTemplateValues(template(text2 = "{ha:vlhkost}"), payload))
        assertNull(resolveTemplateValues(template(items = listOf(item(label = "{ha:vlhkost}"))), payload))

        assertNull(resolveTemplateValues(template(text = "{notif:title}"), payload))
    }

    @Test
    fun `without a payload the placeholder is not substituted`() {
        assertNull(resolveTemplateValues(template(text = "{ha:teplota}"), TriggerPayload.EMPTY))
    }

    @Test
    fun `content without a placeholder is returned unchanged`() {
        val content = template(
            text = "Dopráno",
            items = listOf(item(label = "Vypnout", icon = "mdi:gate")),
        )

        assertSame(content, resolveTemplateValues(content, TriggerPayload.EMPTY))
    }

    @Test
    fun `content other than a template is not substituted`() {
        val widget = TileContent.LauncherWidget(LauncherWidgetType.CLOCK)

        assertSame(widget, resolveTemplateValues(widget, payload))
    }

    @Test
    fun `an icon from the payload only applies to an unfilled HA action icon`() {
        val content = template(
            items = listOf(
                item(),
                item(icon = "mdi:gate"),
                item(action = TapAction.App(packageName = "com.spotify.music")),
            ),
        )

        val resolved = resolveTemplateValues(content, payload) as TileContent.Template

        assertEquals("mdi:washing-machine", resolved.items[0].icon)
        assertEquals("mdi:gate", resolved.items[1].icon)
        assertNull(resolved.items[2].icon)
    }

    @Test
    fun `the key is looked up case-insensitively`() {
        val resolved = resolveTemplateValues(template(text = "{HA:Teplota}"), payload) as TileContent.Template

        assertEquals("38", resolved.text)
    }

    @Test
    fun `a portability placeholder is not substituted from the payload`() {
        val content = template(
            text = "{{PLACEHOLDER:ha_url}}",
            items = listOf(item(action = TapAction.App(packageName = "com.example"))),
        )

        assertSame(content, resolveTemplateValues(content, payload))
    }

    @Test
    fun `text without a colon is not a placeholder`() {
        val content = template(
            text = "Nastav {teplotu} ručně",
            items = listOf(item(action = TapAction.App(packageName = "com.example"))),
        )

        assertSame(content, resolveTemplateValues(content, payload))
    }
}

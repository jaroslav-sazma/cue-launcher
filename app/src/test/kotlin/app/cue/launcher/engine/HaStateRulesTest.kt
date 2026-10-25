package app.cue.launcher.engine

import app.cue.launcher.data.ha.HaIconAttributes
import app.cue.launcher.data.ha.HaIconSet
import app.cue.launcher.data.ha.HaIconTable
import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.LauncherWidgetType
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize
import app.cue.launcher.data.scenario.TriggerPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HaStateRulesTest {

    private fun template(
        text: String? = null,
        text2: String? = null,
        labels: List<String?> = listOf(null),
    ) = TileContent.Template(
        layout = TemplateLayout.TEXT_TWO_ICONS,
        text = text,
        text2 = text2,
        items = labels.map { label ->
            TemplateItem(label = label, actions = listOf(TapAction.HaAction(service = "cover.open_cover")))
        },
    )

    private fun iconTemplate(entity: String?) = TileContent.Template(
        layout = TemplateLayout.ICON_TEXT,
        items = listOf(
            TemplateItem(actions = listOf(TapAction.HaAction(service = "cover.toggle", entity = entity))),
        ),
    )

    private fun fourIcons(vararg entities: String?) = TileContent.Template(
        layout = TemplateLayout.FOUR_ICONS,
        items = entities.map { entity ->
            TemplateItem(actions = listOf(TapAction.HaAction(service = "cover.toggle", entity = entity)))
        },
    )

    private fun doorTemplate(stateEntity: String?) = TileContent.Template(
        layout = TemplateLayout.ICON_TEXT,
        items = listOf(
            TemplateItem(
                actions = listOf(
                    TapAction.HaAction(service = "lock.open", entity = "lock.front_door", stateEntity = stateEntity),
                ),
            ),
        ),
    )

    private fun scenario2x2(id: String, content: TileContent, enabled: Boolean = true) = scenario(
        id = id,
        size = TileSize.GRID_2X2,
        content = content,
    ).copy(enabled = enabled)

    private fun activation(id: String, since: Long = 0L) =
        Activation(scenarioId = id, since = since, expiresAt = null)

    @Test
    fun `an entity is recognized by its dot, a payload field is not`() {
        assertEquals("sensor.teplota", haStateRefOf("ha:sensor.teplota"))
        assertNull(haStateRefOf("ha:teplota"))
        assertNull(haStateRefOf("notif:title"))
        assertNull(haStateRefOf("ha:"))
    }

    @Test
    fun `entities are collected from both texts and the label`() {
        val content = template(
            text = "Doma {ha:sensor.teplota} °C",
            text2 = "Venku {ha:sensor.venku} °C, prádlo {ha:zbyva}",
            labels = listOf("Vlhkost {ha:SENSOR.Vlhkost}"),
        )

        assertEquals(
            setOf("sensor.teplota", "sensor.venku", "sensor.vlhkost"),
            haStateRefs(content),
        )
    }

    @Test
    fun `a widget wants no state at all`() {
        assertEquals(emptySet<String>(), haStateRefs(TileContent.LauncherWidget(LauncherWidgetType.CALENDAR)))
    }

    @Test
    fun `only scenarios that can currently be drawn are read`() {
        val running = scenario2x2("bezi", template(text = "{ha:sensor.bezi}"))
        val idle = scenario2x2("nebezi", template(text = "{ha:sensor.nebezi}"))
        val off = scenario2x2("vypnuty", template(text = "{ha:sensor.vypnuty}"), enabled = false)
        val permanent = scenario2x2("trvaly", template(text = "{ha:sensor.trvaly}")).copy(conditions = emptyList())

        val refs = haStateRefs(
            scenarios = listOf(running, idle, off, permanent),
            activations = listOf(activation("bezi"), activation("vypnuty")),
            now = 0L,
        )

        assertEquals(setOf("sensor.bezi", "sensor.trvaly"), refs)
    }

    @Test
    fun `an expired activation no longer needs state`() {
        val expired = Activation(scenarioId = "bezi", since = 0L, expiresAt = 10L)
        val subject = scenario2x2("bezi", template(text = "{ha:sensor.bezi}"))

        assertEquals(emptySet<String>(), haStateRefs(listOf(subject), listOf(expired), now = 11L))
    }

    @Test
    fun `state is substituted under the namespace key`() {
        val content = template(text = "Doma {ha:sensor.teplota} °C")
        val states = haStateValues(mapOf("sensor.teplota" to "21.5"))

        val resolved = resolveTemplateValues(content, TriggerPayload.EMPTY, states) as TileContent.Template

        assertEquals("Doma 21.5 °C", resolved.text)
    }

    @Test
    fun `unread state discards the content`() {
        val content = template(text = "Doma {ha:sensor.teplota} °C")

        assertNull(resolveTemplateValues(content, TriggerPayload.EMPTY, emptyMap()))
    }

    @Test
    fun `the payload overrides the downloaded state`() {
        val content = template(text = "{ha:sensor.teplota}")
        val payload = TriggerPayload(fields = mapOf("ha:sensor.teplota" to "38"))

        val resolved = resolveTemplateValues(
            content,
            payload,
            haStateValues(mapOf("sensor.teplota" to "21.5")),
        ) as TileContent.Template

        assertEquals("38", resolved.text)
    }

    @Test
    fun `a queue without state keeps the persistent content`() {
        val waiting = scenario2x2("ceka", template(text = "{ha:sensor.teplota}"))
        val permanent = scenario2x2("trvaly", template(text = "Pevný text")).copy(conditions = emptyList())
        val activations = listOf(activation("ceka"))

        val without = sizeClassQueues(listOf(waiting, permanent), activations, now = 0L)
        val with = sizeClassQueues(
            listOf(waiting, permanent),
            activations,
            now = 0L,
            haStates = mapOf("sensor.teplota" to "21.5"),
        )

        assertEquals(listOf("trvaly"), without.medium.entries.map { it.scenario.id })
        assertEquals(listOf("ceka", "trvaly"), with.medium.entries.map { it.scenario.id })
    }

    // A payload field whose name contains a dot is indistinguishable from an entity id. The event
    // already brought the value, so nothing should be read over the network for it.
    @Test
    fun `a value the event already carried is not read again`() {
        val content = template(text = "{ha:sensor.washer_remaining}")
        val scenarios = listOf(scenario2x2("washer", content))
        val activations = listOf(
            activation("washer").copy(
                payload = TriggerPayload(fields = mapOf("ha:sensor.washer_remaining" to "12")),
            ),
        )

        assertEquals(emptySet<String>(), haStateRefs(scenarios, activations, now = 0L))
        assertEquals(setOf("sensor.washer_remaining"), haStateRefs(scenarios, listOf(activation("washer")), 0L))
    }

    // The icon's entity only decides a colour, so it must stay out of the set a tile cannot be
    // drawn without — otherwise the editor reports a perfectly renderable tile as broken.
    @Test
    fun `the entity behind an icon is not a value the tile needs`() {
        assertEquals(emptySet<String>(), haStateRefs(iconTemplate("cover.gate")))
        assertEquals(setOf("cover.gate"), haIconRefs(iconTemplate("cover.GATE")))
        assertEquals(emptySet<String>(), haIconRefs(iconTemplate(null)))
    }

    // Four icons on one tile are four actions, so each one is read and coloured on its own.
    @Test
    fun `every icon on a tile brings its own entity`() {
        val content = fourIcons("cover.gate", null, "light.hall", "light.HALL")

        assertEquals(setOf("cover.gate", "light.hall"), haIconRefs(content))
    }

    @Test
    fun `only the entities that are on get the accent`() {
        val content = fourIcons("cover.gate", "light.hall", "lock.front", "media_player.tv")
        val values = haStateValues(
            mapOf(
                "cover.gate" to "open",
                "light.hall" to "off",
                "lock.front" to "unavailable",
                "media_player.tv" to "playing",
            ),
        )

        assertEquals(setOf("cover.gate", "media_player.tv"), haAccentedEntities(content, values))
    }

    @Test
    fun `an icon that leads elsewhere is never accented`() {
        val app = TileContent.Template(
            layout = TemplateLayout.ICON_TEXT,
            items = listOf(TemplateItem(actions = listOf(TapAction.App("com.android.calculator2")))),
        )

        assertEquals(emptySet<String>(), haIconRefs(app))
        assertEquals(emptySet<String>(), haAccentedEntities(app, haStateValues(mapOf("cover.gate" to "open"))))
    }

    @Test
    fun `an unread entity is not accented`() {
        val content = iconTemplate("cover.gate")

        assertEquals(emptySet<String>(), haAccentedEntities(content, emptyMap()))
    }

    @Test
    fun `the accent travels with the queue entry`() {
        val gate = scenario2x2("brana", iconTemplate("cover.gate")).copy(conditions = emptyList())

        val open = sizeClassQueues(listOf(gate), emptyList(), now = 0L, mapOf("cover.gate" to "open"))
        val closed = sizeClassQueues(listOf(gate), emptyList(), now = 0L, mapOf("cover.gate" to "closed"))

        assertEquals(setOf("cover.gate"), open.medium.entries.single().accentedEntities)
        assertEquals(emptySet<String>(), closed.medium.entries.single().accentedEntities)
    }

    @Test
    fun `a state entity replaces the action's entity as the icon's source`() {
        val content = doorTemplate("binary_sensor.Front_Door_Contact")
        val item = content.items.single()

        assertEquals("binary_sensor.front_door_contact", haIconEntity(item))
        assertEquals(setOf("binary_sensor.front_door_contact"), haIconRefs(content))
        assertEquals(emptySet<String>(), haStateRefs(content))
    }

    @Test
    fun `only the state entity decides the accent`() {
        val content = doorTemplate("binary_sensor.front_door_contact")
        val open = haStateValues(mapOf("binary_sensor.front_door_contact" to "on", "lock.front_door" to "locked"))
        val closed = haStateValues(mapOf("binary_sensor.front_door_contact" to "off", "lock.front_door" to "unlocked"))

        assertEquals(setOf("binary_sensor.front_door_contact"), haAccentedEntities(content, open))
        assertEquals(emptySet<String>(), haAccentedEntities(content, closed))
    }

    @Test
    fun `without a state entity the action's entity still colours the icon`() {
        assertEquals(setOf("lock.front_door"), haIconRefs(doorTemplate(null)))
        assertEquals(setOf("lock.front_door"), haIconRefs(doorTemplate("  ")))
    }

    // Falling back to the action's entity would light the icon by the very state the scenario
    // asked to be ignored.
    @Test
    fun `a state entity that is not an entity id leaves the icon unaccented`() {
        val content = doorTemplate("{{PLACEHOLDER:door_contact}}")

        assertNull(haIconEntity(content.items.single()))
        assertEquals(emptySet<String>(), haIconRefs(content))
        assertEquals(
            emptySet<String>(),
            haAccentedEntities(content, haStateValues(mapOf("lock.front_door" to "unlocked"))),
        )
    }

    @Test
    fun `the subscription set follows the state entity`() {
        val tile = scenario2x2("door", doorTemplate("binary_sensor.front_door_contact")).copy(conditions = emptyList())

        assertEquals(setOf("binary_sensor.front_door_contact"), haStateRefs(listOf(tile), emptyList(), now = 0L))
    }

    @Test
    fun `the subscription set carries both the texts and the icons`() {
        val tile = scenario2x2(
            "brana",
            TileContent.Template(
                layout = TemplateLayout.TEXT_TWO_ICONS,
                text = "{ha:sensor.teplota}",
                items = listOf(
                    TemplateItem(actions = listOf(TapAction.HaAction("cover.toggle", entity = "cover.gate"))),
                    TemplateItem(actions = listOf(TapAction.HaAction("light.toggle", entity = "light.hall"))),
                ),
            ),
        ).copy(conditions = emptyList())

        assertEquals(
            setOf("sensor.teplota", "cover.gate", "light.hall"),
            haStateRefs(listOf(tile), emptyList(), now = 0L),
        )
    }

    @Test
    fun `only an icon nobody chose follows Home Assistant`() {
        val content = TileContent.Template(
            layout = TemplateLayout.FOUR_ICONS,
            items = listOf(
                TemplateItem(actions = listOf(TapAction.HaAction("cover.toggle", entity = "cover.gate"))),
                TemplateItem(
                    icon = "mdi:lightbulb-group",
                    actions = listOf(TapAction.HaAction("light.toggle", entity = "light.hall")),
                ),
                TemplateItem(
                    actions = listOf(
                        TapAction.HaAction("lock.open", entity = "lock.front_door", stateEntity = "binary_sensor.door"),
                    ),
                ),
                TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.FLASHLIGHT))),
            ),
        )
        val tile = scenario2x2("ikony", content).copy(conditions = emptyList())

        assertEquals(setOf("cover.gate", "binary_sensor.door"), haGlyphEntities(content))
        assertEquals(setOf("cover.gate", "binary_sensor.door"), haGlyphRefs(listOf(tile), emptyList(), now = 0L))
    }

    // The event's glyph goes on every Home Assistant icon left empty, so there is nothing to look up.
    @Test
    fun `an event that brings a glyph needs no icons from Home Assistant`() {
        val tile = scenario2x2("zvonek", iconTemplate("cover.gate"))
        val withGlyph = activation("zvonek").copy(payload = TriggerPayload(icon = "mdi:bell-ring"))

        assertEquals(emptySet<String>(), haGlyphRefs(listOf(tile), listOf(withGlyph), now = 0L))
        assertEquals(setOf("cover.gate"), haGlyphRefs(listOf(tile), listOf(activation("zvonek")), now = 0L))
    }

    @Test
    fun `the icon follows the state and the device class of its entity`() {
        val content = iconTemplate("cover.gate")
        val table = HaIconTable(
            instance = "home",
            version = "2026.10.1",
            domains = mapOf(
                "cover" to mapOf(
                    "_" to HaIconSet("mdi:window-open", mapOf("closed" to "mdi:window-closed")),
                    "gate" to HaIconSet("mdi:gate-open", mapOf("closed" to "mdi:gate")),
                ),
            ),
        )
        val icons = HaIconContext(mapOf("cover.gate" to HaIconAttributes(deviceClass = "gate")), table)

        val closed = haTileGlyphs(content, haStateValues(mapOf("cover.gate" to "closed")), icons)
        val open = haTileGlyphs(content, haStateValues(mapOf("cover.gate" to "open")), icons)

        assertEquals("mdi:gate", closed.getValue("cover.gate").first())
        assertEquals("mdi:gate-open", open.getValue("cover.gate").first())
        assertEquals(emptyMap<String, List<String>>(), haTileGlyphs(content, emptyMap(), HaIconContext()))
    }

    @Test
    fun `a queue entry carries the icons of its entities`() {
        val tile = scenario2x2("brana", iconTemplate("cover.gate")).copy(conditions = emptyList())
        val table = HaIconTable(
            instance = "home",
            version = "2026.10.1",
            domains = mapOf("cover" to mapOf("_" to HaIconSet("mdi:window-open"))),
        )

        val queues = sizeClassQueues(
            scenarios = listOf(tile),
            activations = emptyList(),
            now = 0L,
            haStates = mapOf("cover.gate" to "open"),
            haIcons = HaIconContext(table = table),
        )

        assertEquals(mapOf("cover.gate" to listOf("mdi:window-open")), queues.medium.entries.single().haGlyphs)
    }
}

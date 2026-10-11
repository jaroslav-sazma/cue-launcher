package app.cue.launcher.system.aichat

import app.cue.launcher.data.ha.HaEntity
import app.cue.launcher.data.ha.HaEntityFilter
import app.cue.launcher.data.ha.HaService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaServicesLineTest {

    private val gate = HaEntity("cover.gate", "Gate", features = OPEN or CLOSE)
    private val garage = HaEntity("cover.garage", "Garage", features = OPEN or CLOSE or STOP)
    private val desk = HaEntity("light.desk", "Desk")

    private val generic = listOf("toggle", "turn_off", "turn_on").map { service("homeassistant.$it") }

    private val services = listOf(
        service("cover.close_cover", "cover", CLOSE),
        service("cover.open_cover", "cover", OPEN),
        service("cover.set_cover_position", "cover", SET_POSITION),
        service("cover.stop_cover", "cover", STOP),
        service("cover.toggle", "cover", OPEN, CLOSE),
        service("light.toggle", "light"),
        service("light.turn_off", "light"),
        service("light.turn_on", "light"),
        service("switch.toggle", "switch"),
        HaService("script.night"),
    ) + generic

    @Test
    fun `without services or entities the static line stands word for word`() {
        assertEquals(HA_SERVICES_FALLBACK, haServicesLine(listOf(gate), null))
        assertEquals(HA_SERVICES_FALLBACK, haServicesLine(listOf(gate), emptyList()))
        assertEquals(HA_SERVICES_FALLBACK, haServicesLine(emptyList(), services))
    }

    @Test
    fun `the line lists the listed domains' services and the generic ones`() {
        assertEquals(
            "ha actions run a Home Assistant service on an entity of its domain (this home offers " +
                "light.toggle, light.turn_on, light.turn_off, cover.open_cover, cover.close_cover, cover.toggle, " +
                "cover.stop_cover) or homeassistant.toggle/turn_on/turn_off on any.",
            haServicesLine(listOf(desk, gate, garage), services),
        )
    }

    @Test
    fun `a domain lists what fits the most of its entities first, and nothing that fits none`() {
        val line = haServicesLine(listOf(gate, garage), services - generic.toSet())

        assertTrue(
            "(this home offers cover.open_cover, cover.close_cover, cover.toggle, cover.stop_cover)" in line,
            line,
        )
        assertFalse("set_cover_position" in line || "homeassistant" in line, line)
        assertFalse("stop_cover" in haServicesLine(listOf(gate), services))
    }

    @Test
    fun `domains without a listed entity stay out`() {
        val line = haServicesLine(listOf(desk), services)

        assertFalse("switch." in line || "cover." in line || "script." in line, line)
    }

    @Test
    fun `only the generic services the instance offers are named`() {
        val line = haServicesLine(listOf(desk), services - generic.first())

        val none = haServicesLine(listOf(desk), services - generic.toSet())

        assertTrue(line.endsWith(" or homeassistant.turn_on/turn_off on any."), line)
        assertTrue(none.endsWith("light.turn_off)."), none)
    }

    @Test
    fun `entities with no fitting service and no generic ones leave the static line`() {
        val sensor = HaEntity("sensor.temperature", "Temperature")

        assertEquals(HA_SERVICES_FALLBACK, haServicesLine(listOf(sensor), services - generic.toSet()))
    }

    @Test
    fun `every domain and every fitting service is listed`() {
        val domains = listOf("alpha", "beta", "gamma", "delta", "epsilon")
        val entities = domains.map { HaEntity("$it.one", null) }
        val many = domains.flatMap { domain -> (1..5).map { service("$domain.do_$it", domain) } }

        val offered = haServicesLine(entities, many).substringAfter("offers ").substringBefore(')').split(", ")

        assertEquals(many.map { it.id }, offered)
    }

    private fun service(id: String, domain: String? = null, vararg features: Int) = HaService(
        id = id,
        targets = if (domain == null) {
            emptyList()
        } else {
            listOf(HaEntityFilter(setOf(domain), features = features.toList().takeIf { it.isNotEmpty() }))
        },
    )

    private companion object {
        const val OPEN = 1
        const val CLOSE = 2
        const val SET_POSITION = 4
        const val STOP = 8
    }
}

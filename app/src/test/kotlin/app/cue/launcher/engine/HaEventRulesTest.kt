package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.ConditionMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaEventRulesTest {

    @Test
    fun `the payload carries the code, icon and template fields`() {
        val event = haEventOf(
            mapOf(
                HA_EXTRA_CODE to "zvonek",
                HA_EXTRA_ICON to "mdi:doorbell",
                "teplota" to 38,
                "kdo" to "Pošta",
            ),
        )

        assertEquals("zvonek", event?.code)
        assertEquals(HaEventOp.START, event?.op)

        assertEquals("mdi:doorbell", event?.icon)
        assertEquals(mapOf("teplota" to "38", "kdo" to "Pošta"), event?.fields)
    }

    @Test
    fun `reserved keys are not template fields`() {
        val event = haEventOf(
            mapOf(HA_EXTRA_CODE to "zvonek", HA_EXTRA_OP to "end", HA_EXTRA_ICON to "mdi:gate"),
        )

        assertEquals(emptyMap<String, String>(), event?.fields)
    }

    @Test
    fun `op end is a stop, anything else is a start`() {
        assertEquals(HaEventOp.END, haEventOf(mapOf(HA_EXTRA_CODE to "z", HA_EXTRA_OP to "END"))?.op)
        assertEquals(HaEventOp.END, haEventOf(mapOf(HA_EXTRA_CODE to "z", HA_EXTRA_OP to " end "))?.op)

        assertEquals(HaEventOp.START, haEventOf(mapOf(HA_EXTRA_CODE to "z", HA_EXTRA_OP to "stop"))?.op)
        assertEquals(HaEventOp.START, haEventOf(mapOf(HA_EXTRA_CODE to "z"))?.op)
    }

    @Test
    fun `a payload without a code is not an event`() {
        assertNull(haEventOf(emptyMap()))
        assertNull(haEventOf(mapOf(HA_EXTRA_ICON to "mdi:gate")))
        assertNull(haEventOf(mapOf(HA_EXTRA_CODE to "   ")))
    }

    @Test
    fun `the code is read without surrounding spaces`() {
        assertEquals("zvonek", haEventOf(mapOf(HA_EXTRA_CODE to " zvonek "))?.code)
    }

    @Test
    fun `values of other types are translated to text`() {
        assertEquals("38", haExtraText(38))
        assertEquals("21.5", haExtraText(21.5))
        assertEquals("true", haExtraText(true))
        assertEquals("x", haExtraText('x'))
        assertNull(haExtraText(null))
    }

    @Test
    fun `arrays are joined with a semicolon`() {
        assertEquals("1;2;3", haExtraText(intArrayOf(1, 2, 3)))
        assertEquals("a;b", haExtraText(arrayOf("a", "b")))
        assertEquals("a;b", haExtraText(listOf("a", "b")))
    }

    @Test
    fun `an unconvertible value is dropped`() {
        val event = haEventOf(mapOf(HA_EXTRA_CODE to "zvonek", "objekt" to Any()))

        assertEquals(emptyMap<String, String>(), event?.fields)
    }

    @Test
    fun `the activation payload carries fields with the ha namespace`() {
        val event = HaEvent(
            code = "pracka",
            icon = "mdi:washing-machine",
            fields = mapOf(" Teplota " to "38", "program" to "bavlna"),
        )

        val payload = haPayload(event)

        assertEquals("mdi:washing-machine", payload.icon)
        assertEquals(mapOf("ha:teplota" to "38", "ha:program" to "bavlna"), payload.fields)
    }

    @Test
    fun `an event without a payload has an empty payload`() {
        assertTrue(haPayload(HaEvent(code = "zvonek")).isEmpty)
    }

    @Test
    fun `the code matches regardless of case and spaces`() {
        val condition = Condition.HaEvent(code = " Zvonek ")

        assertTrue(haEventMatches(condition, "zvonek"))
        assertFalse(haEventMatches(condition, "pracka"))
    }

    @Test
    fun `listeners are only enabled scenarios with a matching code`() {
        val zvonek = scenario("zvonek", conditions = listOf(Condition.HaEvent(code = "zvonek")))
        val vypnuty = scenario("vypnuty", conditions = listOf(Condition.HaEvent(code = "zvonek")))
            .copy(enabled = false)
        val jiny = scenario("jiny", conditions = listOf(Condition.HaEvent(code = "pracka")))
        val stavovy = scenario("stavovy", conditions = listOf(Condition.Charging()))

        val listeners = haEventListeners(listOf(zvonek, vypnuty, jiny, stavovy), "zvonek")

        assertEquals(listOf("zvonek"), listeners.map { it.id })
    }

    @Test
    fun `under any every event condition is a listener with its own duration`() {
        val zvonek = Condition.HaEvent(code = "zvonek", durationSec = 30)
        val brana = Condition.HaEvent(code = "brana", durationSec = 300)
        val scenario = scenario("dum", conditions = listOf(zvonek, brana))
            .copy(match = ConditionMatch.ANY)
        val scenarios = listOf(scenario)

        assertEquals(listOf("dum"), haEventListeners(scenarios, "zvonek").map { it.id })
        assertEquals(listOf("dum"), haEventListeners(scenarios, "brana").map { it.id })
        assertEquals(zvonek, haEventTrigger(scenario, "zvonek"))
        assertEquals(brana, haEventTrigger(scenario, "brana"))
        assertNull(haEventTrigger(scenario, "pracka"))
    }

    @Test
    fun `an event respects the state gate`() {
        val scenarios = listOf(
            scenario(
                "zvonek",
                conditions = listOf(Condition.HaEvent(code = "zvonek"), Condition.Charging()),
            ),
        )

        assertTrue(dueHaEvents(scenarios, SystemState(charging = true), "zvonek").isNotEmpty())
        assertTrue(dueHaEvents(scenarios, SystemState(charging = false), "zvonek").isEmpty())
        assertTrue(dueHaEvents(scenarios, SystemState(), "zvonek").isEmpty())
    }

    @Test
    fun `an event is accepted only with the matching secret`() {
        assertTrue(haEventSecretAccepted("ABCD2345EFGH6789", "ABCD2345EFGH6789"))
        assertTrue(haEventSecretAccepted("ABCD2345EFGH6789", " ABCD2345EFGH6789 "))

        assertFalse(haEventSecretAccepted("ABCD2345EFGH6789", "abcd2345efgh6789"))
        assertFalse(haEventSecretAccepted("ABCD2345EFGH6789", "ABCD2345EFGH678"))
        assertFalse(haEventSecretAccepted("ABCD2345EFGH6789", ""))
        assertFalse(haEventSecretAccepted("ABCD2345EFGH6789", null))
    }

    // Without a stored secret nothing may pass: a fresh install must not accept a foreign event in
    // the window before the user has opened the Home Assistant screen.
    @Test
    fun `an event is rejected while no secret is stored`() {
        assertFalse(haEventSecretAccepted("", "ABCD2345EFGH6789"))
        assertFalse(haEventSecretAccepted("  ", ""))
        assertFalse(haEventSecretAccepted("", null))
    }

    @Test
    fun `the secret never reaches the template fields`() {
        val event = haEventOf(
            mapOf(
                HA_EXTRA_CODE to "zvonek",
                HA_EXTRA_SECRET to "ABCD2345EFGH6789",
                "kdo" to "Pošta",
            ),
        )

        assertEquals(mapOf("kdo" to "Pošta"), event?.fields)
        assertEquals("ABCD2345EFGH6789", haEventSecretOf(mapOf(HA_EXTRA_SECRET to "ABCD2345EFGH6789")))
    }

    @Test
    fun `an event may repeat even with a running activation`() {
        val scenarios = listOf(scenario("zvonek", conditions = listOf(Condition.HaEvent(code = "zvonek"))))

        assertEquals(listOf("zvonek"), dueHaEvents(scenarios, SystemState(), "zvonek").map { it.id })
    }

    // The blueprint stamps the contract version on every event; an older copy sends none, and that has
    // to stay visible instead of looking like a normal event.
    @Test
    fun `the version rides along and never becomes a template field`() {
        val event = haEventOf(
            mapOf(
                HA_EXTRA_CODE to "zvonek",
                HA_EXTRA_VERSION to 1,
                "kdo" to "Pošta",
            ),
        )

        assertEquals(HA_EVENT_VERSION, event?.version)
        assertEquals(mapOf("kdo" to "Pošta"), event?.fields)
        assertFalse(event?.legacy == true)
    }

    @Test
    fun `an event without a version comes from an older blueprint`() {
        val event = haEventOf(mapOf(HA_EXTRA_CODE to "zvonek"))

        assertEquals(0, event?.version)
        assertTrue(event?.legacy == true)
    }

    // The code is the one thing a rejected broadcast still gets to write — into the log and the
    // database — so its length is not the sender's to choose.
    @Test
    fun `an overlong code is cut to the cap`() {
        val event = haEventOf(mapOf(HA_EXTRA_CODE to "x".repeat(10_000)))

        assertEquals(HA_CODE_MAX_LENGTH, event?.code?.length)
        assertEquals(HA_CODE_MAX_LENGTH, haEventCode(" " + "y".repeat(500))?.length)
        assertNull(haEventCode("   "))
        assertNull(haEventCode(null))
    }

    // The incoming code is cut to the cap, so a scenario's longer code has to be cut the same way —
    // otherwise it could never match, nor be marked as seen.
    @Test
    fun `a scenario code longer than the cap still matches the cut incoming code`() {
        val long = "Dvere_" + "x".repeat(100)
        val condition = Condition.HaEvent(code = " $long ", durationSec = 60)
        val event = requireNotNull(haEventOf(mapOf(HA_EXTRA_CODE to long.lowercase())))

        assertTrue(haEventMatches(condition, event.code))
        assertEquals(haCodeKey(condition.code), haCodeKey(event.code))
        assertEquals(HA_CODE_MAX_LENGTH, haCodeKey(condition.code).length)
        assertFalse(haEventMatches(condition, long.take(HA_CODE_MAX_LENGTH - 1)))
        assertFalse(haEventMatches(Condition.HaEvent(code = "  ", durationSec = 60), " "))
    }

    @Test
    fun `a sender with too many fields keeps the same first ones every time`() {
        val extras = buildMap<String, Any?> {
            put(HA_EXTRA_CODE, "zvonek")
            (0 until 50).forEach { put("f%02d".format(it), it) }
        }

        val event = haEventOf(extras)

        assertEquals(HA_FIELDS_MAX, event?.fields?.size)
        assertEquals((0 until HA_FIELDS_MAX).map { "f%02d".format(it) }, event?.fields?.keys?.sorted())
    }

    @Test
    fun `a field value is cut to the cap and an overlong key is not a field`() {
        val event = haEventOf(
            mapOf(
                HA_EXTRA_CODE to "zvonek",
                "kdo" to "p".repeat(5_000),
                "k".repeat(65) to "x",
            ),
        )

        assertEquals(setOf("kdo"), event?.fields?.keys)
        assertEquals(HA_VALUE_MAX_LENGTH, event?.fields?.get("kdo")?.length)
    }

    @Test
    fun `an overlong icon is dropped rather than cut into another glyph`() {
        assertNull(haEventOf(mapOf(HA_EXTRA_CODE to "z", HA_EXTRA_ICON to "a".repeat(65)))?.icon)
        assertEquals("doorbell", haEventOf(mapOf(HA_EXTRA_CODE to "z", HA_EXTRA_ICON to "doorbell"))?.icon)
    }

    // The receiver reads the secret on its own before anything else; what it reads afterwards is
    // bounded here, whatever number of extras the broadcast carried.
    @Test
    fun `the keys read after the secret skip the secret and are capped`() {
        val keys = listOf(HA_EXTRA_SECRET, HA_EXTRA_CODE, HA_EXTRA_VERSION, " ", "k".repeat(65)) +
            (0 until 100).map { "f%03d".format(it) }

        val read = haEventKeys(keys)

        assertFalse(HA_EXTRA_SECRET in read)
        assertTrue(read.containsAll(listOf(HA_EXTRA_CODE, HA_EXTRA_VERSION)))
        assertEquals(2 + HA_FIELDS_MAX, read.size)
        assertEquals("f000", read[2])
    }
}

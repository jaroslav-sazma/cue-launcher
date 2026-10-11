package app.cue.launcher.data.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccentColorTest {

    @Test
    fun `a hex colour parses with or without the hash and in either case`() {
        assertEquals(0x3A86FF, parseAccentHex("#3A86FF"))
        assertEquals(0x3A86FF, parseAccentHex("3a86ff"))
        assertEquals(0x3A86FF, parseAccentHex("#3a86FF"))
        assertEquals(0x3A86FF, parseAccentHex(" #3A86FF "))
    }

    @Test
    fun `anything but six hex digits is rejected`() {
        listOf("", "#", "#3A86F", "#3A86FFF", "#FF3A86FF", "##3A86FF", "#3A86FG", "rgb(1,2,3)", "#3A 86FF")
            .forEach { assertNull(parseAccentHex(it)) { "\"$it\" should not parse" } }
    }

    @Test
    fun `a colour is written as an uppercase hash and six digits`() {
        assertEquals("#3A86FF", accentHexOf(0x3A86FF))
        assertEquals("#00000A", accentHexOf(0x00000A))
        assertEquals("#3A86FF", accentHexOf(0xFF3A86FF.toInt())) { "the alpha channel is dropped" }
    }

    @Test
    fun `formatting and parsing are each other's inverse`() {
        listOf(0x000000, 0xFFFFFF, 0x123456, 0x0A0B0C).forEach { rgb ->
            assertEquals(rgb, parseAccentHex(accentHexOf(rgb)))
        }
    }
}

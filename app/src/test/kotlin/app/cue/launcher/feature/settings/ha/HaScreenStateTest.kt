package app.cue.launcher.feature.settings.ha

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cue.launcher.R
import app.cue.launcher.data.ha.HaConfig
import app.cue.launcher.data.ha.HaEndpointKind
import app.cue.launcher.data.ha.HaInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// The connection fields reach the repository only through Save, so `dirty` decides whether the save
// bar shows and whether leaving warns. Getting it wrong either nags or silently drops an edit.
class HaScreenStateTest {

    private val stored = HaConfig(
        urlInternal = "http://homeassistant.local:8123",
        urlExternal = "https://example.ui.nabu.casa",
        token = "stored-token",
        homeNetworks = listOf("Doma"),
    )

    private fun loaded(): HaScreenState = HaScreenState().apply { fill(stored) }

    @Test
    fun `a state just filled from the repository is clean`() {
        assertFalse(loaded().dirty)
    }

    @Test
    fun `editing an address makes it dirty`() {
        val screen = loaded()

        screen.urlInternal = "http://10.0.0.5:8123"

        assertTrue(screen.dirty)
    }

    @Test
    fun `editing the home networks makes it dirty`() {
        val screen = loaded()

        screen.homeNetworksText = "Doma, Chata"

        assertTrue(screen.dirty)
    }

    @Test
    fun `a typed token alone makes it dirty`() {
        val screen = loaded()
        screen.chooseCredential(HaCredentialMode.TOKEN)

        screen.token.setTextAndPlaceCursorAtEnd("fresh-token")

        assertTrue(screen.dirty)
    }

    @Test
    fun `markSaved takes the current fields as the saved ones`() {
        val screen = loaded()
        screen.urlInternal = "http://10.0.0.5:8123"

        screen.markSaved()

        assertFalse(screen.dirty)
    }

    @Test
    fun `a complete config has nothing blocking the save`() {
        assertNull(loaded().saveBlocker)
    }

    @Test
    fun `without any address the blocker asks for one`() {
        val screen = loaded()
        screen.urlInternal = ""
        screen.urlExternal = ""

        assertEquals(R.string.ha_url_error_none, screen.saveBlocker)
        assertFalse(screen.canSave)
    }

    @Test
    fun `a malformed address is reported before the network`() {
        val screen = loaded()
        screen.urlInternal = "homeassistant.local"

        assertEquals(R.string.ha_url_error_scheme, screen.saveBlocker)
    }

    @Test
    fun `a plain http address at home needs a network name`() {
        val screen = loaded()
        screen.homeNetworksText = ""

        assertEquals(R.string.ha_save_needs_wifi, screen.saveBlocker)
        assertFalse(screen.canSave)
    }

    @Test
    fun `an https address at home needs no network name`() {
        val screen = loaded()
        screen.urlInternal = "https://home.example.com"
        screen.homeNetworksText = ""

        assertNull(screen.saveBlocker)
        assertTrue(screen.canSave)
    }

    @Test
    fun `an insecure away address is reported`() {
        val screen = loaded()
        screen.urlExternal = "http://example.com"

        assertEquals(R.string.ha_url_error_insecure, screen.saveBlocker)
    }

    @Test
    fun `testing needs a credential on top of what saving needs`() {
        val screen = HaScreenState()
        screen.urlInternal = "https://home.example.com"
        screen.chooseCredential(HaCredentialMode.TOKEN)

        assertTrue(screen.canSave)
        assertFalse(screen.canTest)

        screen.token.setTextAndPlaceCursorAtEnd("t")

        assertTrue(screen.canTest)
    }

    @Test
    fun `no test starts while a save or another test is running`() {
        val screen = HaScreenState().apply { fill(stored.copy(refreshToken = "r", oauthClientId = "c")) }

        assertTrue(screen.canStartTest)

        screen.saving = true
        assertFalse(screen.canStartTest)

        screen.saving = false
        screen.testing = true
        assertFalse(screen.canStartTest)
    }

    // The screen offers one way in at a time, so whatever is typed in the other one must not be what
    // gets saved — nor make the save bar appear for an edit the user cannot see.
    @Test
    fun `a token typed before switching to the sign-in does not count`() {
        val screen = loaded()
        screen.chooseCredential(HaCredentialMode.TOKEN)
        screen.token.setTextAndPlaceCursorAtEnd("fresh-token")

        assertTrue(screen.dirty)
        assertEquals("fresh-token", screen.typedToken)

        screen.chooseCredential(HaCredentialMode.SIGN_IN)

        assertEquals("", screen.typedToken)
        assertFalse(screen.dirty)
    }

    // Signing in stores the address together with the session, so the door has to be as narrow as the
    // Save button: a cleartext home address with no home network cannot get in through it either.
    @Test
    fun `signing in is blocked by whatever blocks saving`() {
        val screen = HaScreenState()
        screen.urlInternal = "http://homeassistant.local:8123"

        assertFalse(screen.canSave)
        assertFalse(screen.canSignIn)

        screen.homeNetworksText = "Doma"

        assertTrue(screen.canSave)
        assertTrue(screen.canSignIn)
    }

    @Test
    fun `a stored long-lived token opens the screen on the token`() {
        assertEquals(HaCredentialMode.TOKEN, loaded().credentialMode)
    }

    @Test
    fun `a signed-in session opens the screen on the sign-in`() {
        val screen = HaScreenState().apply { fill(stored.copy(refreshToken = "r", oauthClientId = "c")) }

        assertEquals(HaCredentialMode.SIGN_IN, screen.credentialMode)
        assertTrue(screen.canTest)
    }

    // An mDNS record is anyone's to publish, so the picker overwriting an address must show.
    @Test
    fun `the picker replacing an address leaves a notice with the old one`() {
        val screen = loaded()

        screen.apply(HaInstance(uuid = "u", externalUrl = "https://rogue.example.com"))

        assertEquals("https://rogue.example.com", screen.urlExternal)
        assertEquals(mapOf(HaEndpointKind.AWAY to stored.urlExternal), screen.replaced)
    }

    @Test
    fun `the picker filling an empty field or repeating the same address leaves no notice`() {
        val screen = HaScreenState().apply { fill(stored.copy(urlExternal = "")) }

        screen.apply(
            HaInstance(
                uuid = "u",
                internalUrl = "${stored.urlInternal}/",
                externalUrl = "https://example.ui.nabu.casa",
            ),
        )

        assertEquals("https://example.ui.nabu.casa", screen.urlExternal)
        assertTrue(screen.replaced.isEmpty())
    }

    @Test
    fun `a second pick still names the address the user had`() {
        val screen = loaded()

        screen.apply(HaInstance(uuid = "u", externalUrl = "https://one.example.com"))
        screen.apply(HaInstance(uuid = "u", externalUrl = "https://two.example.com"))

        assertEquals(mapOf(HaEndpointKind.AWAY to stored.urlExternal), screen.replaced)
    }

    @Test
    fun `the notice goes once the field is edited or saved`() {
        val screen = loaded()
        val rogue = HaInstance(
            uuid = "u",
            internalUrl = "https://rogue.local:8123",
            externalUrl = "https://rogue.example.com",
        )
        screen.apply(rogue)
        assertEquals(setOf(HaEndpointKind.HOME, HaEndpointKind.AWAY), screen.replaced.keys)

        screen.urlExternal = "https://example.ui.nabu.casa"
        assertEquals(setOf(HaEndpointKind.HOME), screen.replaced.keys)

        screen.markSaved()
        assertTrue(screen.replaced.isEmpty())
    }
}

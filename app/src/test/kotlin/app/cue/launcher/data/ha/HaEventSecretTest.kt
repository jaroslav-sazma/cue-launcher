package app.cue.launcher.data.ha

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HaEventSecretTest {

    @Test
    fun `a generated secret has the agreed length`() {
        assertEquals(HA_EVENT_SECRET_LENGTH, haGenerateEventSecret().length)
    }

    // The companion app cuts intent_extras on both characters, so a secret carrying either of them
    // would arrive truncated and every event would be rejected.
    @Test
    fun `a generated secret never carries a separator of the companion app`() {
        repeat(REPEATS) {
            val secret = haGenerateEventSecret()

            assertTrue(secret.none { it == ',' || it == ':' }) { "the secret $secret has a separator" }
            assertTrue(secret.all { it.isLetterOrDigit() }) { "the secret $secret is not alphanumeric" }
        }
    }

    @Test
    fun `two secrets in a row differ`() {
        val secrets = List(REPEATS) { haGenerateEventSecret() }

        assertTrue(secrets.toSet().size > REPEATS / 2) { "the generator repeats itself" }
    }

    // A secret carried over from an earlier install has to pass validation, otherwise moving to a
    // new phone would still mean rewriting every automation.
    @Test
    fun `a generated secret is accepted as typed-in input`() {
        assertNull(haEventSecretError(haGenerateEventSecret()))
        assertNull(haEventSecretError(" ABCD2345EFGH6789 "))
    }

    @Test
    fun `a secret with a separator of the companion app is refused`() {
        assertEquals(HaSecretError.SEPARATOR, haEventSecretError("ABCD,2345EFGH"))
        assertEquals(HaSecretError.SEPARATOR, haEventSecretError("ABCD:2345EFGH"))
        assertEquals(HaSecretError.SEPARATOR, haEventSecretError("ABCD 2345 EFGH"))
    }

    // The secret is uploaded inside the blueprint's YAML, where a control character fails to parse and
    // U+0085 is folded into a space, so the default would stop matching without a word.
    @Test
    fun `a secret outside printable ASCII is refused`() {
        assertEquals(HaSecretError.CHARACTERS, haEventSecretError("ABCD\u00002345EFGH"))
        assertEquals(HaSecretError.CHARACTERS, haEventSecretError("ABCD\u00852345EFGH"))
        assertEquals(HaSecretError.CHARACTERS, haEventSecretError("ŽluťoučkýKůň1"))
        assertNull(haEventSecretError("Ab3!#%&*+-./=?@_~"))
    }

    @Test
    fun `a secret too short to guard anything is refused`() {
        assertEquals(HaSecretError.SHORT, haEventSecretError(""))
        assertEquals(HaSecretError.SHORT, haEventSecretError("ABC123"))
        assertNull(haEventSecretError("ABCD1234"))
    }

    // The companion app guesses the type of a value without one: digits only become a number, and one
    // that does not fit in an Int throws there — the whole broadcast is dropped and not even the code
    // arrives. A single letter keeps the secret a string the whole way.
    @Test
    fun `a secret of digits only is refused`() {
        assertEquals(HaSecretError.DIGITS, haEventSecretError("1234567890123456"))
        assertEquals(HaSecretError.DIGITS, haEventSecretError("12345678"))
        assertNull(haEventSecretError("1234567A"))
    }

    @Test
    fun `a generated secret always carries a letter`() {
        repeat(REPEATS) {
            val secret = haGenerateEventSecret()

            assertTrue(secret.any(Char::isLetter)) { "the secret $secret is digits only" }
            assertNull(haEventSecretError(secret))
        }
    }

    private companion object {
        const val REPEATS = 50
    }
}

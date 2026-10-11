package app.cue.launcher.data.notifications

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationListenerStateTest {

    @Test
    fun `the default state is not usable`() {
        val state = NotificationListenerState()

        assertFalse(state.usable)
        assertFalse(shouldRequestRebind(state)) { "without granted access there is nothing to ask for" }
    }

    @Test
    fun `an enabled but unbound listener is not usable`() {
        val state = NotificationListenerState(enabled = true, connected = false)

        assertFalse(state.usable)
        assertTrue(shouldRequestRebind(state)) { "this is exactly the state requestRebind is for" }
    }

    @Test
    fun `an enabled and bound listener is usable`() {
        val state = NotificationListenerState(enabled = true, connected = true)

        assertTrue(state.usable)
        assertFalse(shouldRequestRebind(state)) { "there is no point unbinding a running service" }
    }

    @Test
    fun `revoked access does not request a rebind even when the service appears bound`() {
        val state = NotificationListenerState(enabled = false, connected = true)

        assertFalse(state.usable)
        assertFalse(shouldRequestRebind(state))
    }
}

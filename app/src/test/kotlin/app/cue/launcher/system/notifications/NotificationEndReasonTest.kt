package app.cue.launcher.system.notifications

import android.service.notification.NotificationListenerService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationEndReasonTest {

    @Test
    fun `what the app or the user took down has ended`() {
        assertTrue(endedByItsOwner(NotificationListenerService.REASON_APP_CANCEL))
        assertTrue(endedByItsOwner(NotificationListenerService.REASON_APP_CANCEL_ALL))
        assertTrue(endedByItsOwner(NotificationListenerService.REASON_CANCEL))
        assertTrue(endedByItsOwner(NotificationListenerService.REASON_TIMEOUT))
    }

    @Test
    fun `the system tidying up has not ended anything`() {
        assertFalse(endedByItsOwner(NotificationListenerService.REASON_PACKAGE_CHANGED))
        assertFalse(endedByItsOwner(NotificationListenerService.REASON_SNOOZED))
        assertFalse(endedByItsOwner(NotificationListenerService.REASON_CHANNEL_BANNED))
        assertFalse(endedByItsOwner(NotificationListenerService.REASON_LISTENER_CANCEL))
        assertFalse(endedByItsOwner(NotificationListenerService.REASON_USER_STOPPED))
    }
}

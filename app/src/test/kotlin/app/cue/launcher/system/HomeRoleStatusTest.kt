package app.cue.launcher.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeRoleStatusTest {

    @Test
    fun `a held role is HELD`() {
        assertEquals(
            HomeRoleStatus.HELD,
            homeRoleStatus(isRoleAvailable = true, isRoleHeld = true),
        )
    }

    @Test
    fun `an available unheld role is REQUESTABLE`() {
        assertEquals(
            HomeRoleStatus.REQUESTABLE,
            homeRoleStatus(isRoleAvailable = true, isRoleHeld = false),
        )
    }

    @Test
    fun `an unavailable role is UNAVAILABLE`() {
        assertEquals(
            HomeRoleStatus.UNAVAILABLE,
            homeRoleStatus(isRoleAvailable = false, isRoleHeld = false),
        )
    }

    @Test
    fun `a held role is offered neither by onboarding nor the home menu`() {
        assertEquals(false, homeRoleOffered(HomeRoleStatus.HELD))
    }

    @Test
    fun `an unavailable role is also offered - it leads to system settings`() {
        assertEquals(true, homeRoleOffered(HomeRoleStatus.REQUESTABLE))
        assertEquals(true, homeRoleOffered(HomeRoleStatus.UNAVAILABLE))
    }

    @Test
    fun `a held role takes priority over reported unavailability`() {

        assertEquals(
            HomeRoleStatus.HELD,
            homeRoleStatus(isRoleAvailable = false, isRoleHeld = true),
        )
    }
}

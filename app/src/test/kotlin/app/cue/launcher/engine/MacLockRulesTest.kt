package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.SystemActionType
import app.cue.launcher.data.scenario.TapAction
import app.cue.launcher.data.scenario.TemplateItem
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MacLockRulesTest {

    private val lock = TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.MAC_LOCK)))

    private val torch = TemplateItem(actions = listOf(TapAction.SystemAction(SystemActionType.FLASHLIGHT)))

    private val lockAndQuiet = TemplateItem(
        actions = listOf(
            TapAction.SystemAction(SystemActionType.MAC_LOCK),
            TapAction.SystemAction(SystemActionType.DND_OFF),
        ),
    )

    @Test
    fun `only a present Mac's own word is a lock state`() {
        assertEquals(MacLockState.LOCKED, macLockStateOf(present = true, locked = true))
        assertEquals(MacLockState.UNLOCKED, macLockStateOf(present = true, locked = false))
        assertEquals(MacLockState.UNREPORTED, macLockStateOf(present = true, locked = null))
        assertEquals(MacLockState.UNREPORTED, macLockStateOf(present = false, locked = true))
        assertEquals(MacLockState.UNREPORTED, macLockStateOf(present = false, locked = false))
    }

    @Test
    fun `a lock state reads back as the tile's locked flag`() {
        assertEquals(true, MacLockState.LOCKED.reportedLocked())
        assertEquals(false, MacLockState.UNLOCKED.reportedLocked())
        assertNull(MacLockState.UNREPORTED.reportedLocked())
        assertNull((null as MacLockState?).reportedLocked())
    }

    @Test
    fun `Lock Mac is enabled only while the Mac is known to be unlocked`() {
        assertTrue(templateItemEnabled(lock, macLocked = false))
        assertFalse(templateItemEnabled(lock, macLocked = true))
        assertFalse(templateItemEnabled(lock, macLocked = null))
    }

    @Test
    fun `any other action does not care about the Mac`() {
        assertTrue(templateItemEnabled(torch, macLocked = true))
        assertTrue(templateItemEnabled(torch, macLocked = null))
    }

    @Test
    fun `a button that also does something else stays enabled whatever the Mac says`() {
        assertFalse(onlyLocksMac(lockAndQuiet))
        assertTrue(templateItemEnabled(lockAndQuiet, macLocked = true))
        assertTrue(templateItemEnabled(lockAndQuiet, macLocked = null))
        val lockFirstOfTwo = TemplateItem(actions = lockAndQuiet.actions.reversed())
        assertTrue(templateItemEnabled(lockFirstOfTwo, macLocked = null))
    }

    @Test
    fun `the lock state is followed only while a Lock Mac tile could show`() {
        val lockTile = fallback("lock").copy(content = template(lock))
        val shown = scenario("shown", conditions = emptyList()).copy(content = template(torch, lock))
        val waiting = scenario("waiting").copy(content = template(lock))

        assertTrue(macLockShown(listOf(lockTile), emptyList(), NOW))
        assertTrue(macLockShown(listOf(shown), emptyList(), NOW))
        assertFalse(macLockShown(listOf(waiting), emptyList(), NOW))
        assertTrue(macLockShown(listOf(waiting), listOf(Activation("waiting", since = NOW)), NOW))
        assertFalse(macLockShown(listOf(fallback("torch")), emptyList(), NOW))
    }

    @Test
    fun `a button that locks the Mac among other actions does not follow the lock state`() {
        val mixed = fallback("mixed").copy(content = template(lockAndQuiet))

        assertFalse(macLockShown(listOf(mixed), emptyList(), NOW))
    }

    private fun template(vararg items: TemplateItem) = TileContent.Template(
        layout = if (items.size == 1) TemplateLayout.ICON_TEXT else TemplateLayout.TEXT_TWO_ICONS,
        items = items.toList(),
    )

    private companion object {
        const val NOW = 1_000L
    }
}

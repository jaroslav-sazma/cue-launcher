package app.cue.launcher.system

import app.cue.launcher.data.layout.StoredPackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PackageLedgerTest {

    @Test
    fun `a fresh process knows nothing and asks for a full check`() {
        val catchUp = packageCatchUp(previous = null) { ChangedSince(sequence = 40, packageNames = listOf("a")) }

        assertNull(catchUp.changed)
        assertEquals(40, catchUp.mark)
    }

    @Test
    fun `a known mark reports what changed since it`() {
        var asked = -1
        val catchUp = packageCatchUp(previous = 40) { sequence ->
            asked = sequence
            ChangedSince(sequence = 42, packageNames = listOf("com.mail", "com.maps"))
        }

        assertEquals(40, asked)
        assertEquals(listOf("com.mail", "com.maps"), catchUp.changed)
        assertEquals(42, catchUp.mark)
    }

    @Test
    fun `nothing changed keeps the mark and reports an empty list`() {
        val catchUp = packageCatchUp(previous = 40) { null }

        assertEquals(emptyList<String>(), catchUp.changed)
        assertEquals(40, catchUp.mark)
    }

    @Test
    fun `a fresh process with no changes since boot starts from zero`() {
        val catchUp = packageCatchUp(previous = null) { null }

        assertNull(catchUp.changed)
        assertEquals(0, catchUp.mark)
    }

    @Test
    fun `without a mark every stored package is checked once`() {
        val stored = listOf(own("com.mail"), own("com.mail"), work("com.mail"), own("com.maps"))

        assertEquals(listOf(own("com.mail"), work("com.mail"), own("com.maps")), packagesToCheck(stored, null, OWN))
    }

    @Test
    fun `with a mark only the changed own packages are checked, other profiles always`() {
        val stored = listOf(own("com.mail"), own("com.maps"), work("com.chat"))

        assertEquals(
            listOf(own("com.maps"), work("com.chat")),
            packagesToCheck(stored, changed = listOf("com.maps"), ownSerial = OWN),
        )
    }

    @Test
    fun `nothing changed still checks the other profiles`() {
        val stored = listOf(own("com.mail"), work("com.chat"))

        assertEquals(listOf(work("com.chat")), packagesToCheck(stored, changed = emptyList(), ownSerial = OWN))
    }

    private fun own(name: String) = StoredPackage(name, OWN)

    private fun work(name: String) = StoredPackage(name, WORK)

    private companion object {
        const val OWN = 0L
        const val WORK = 10L
    }
}

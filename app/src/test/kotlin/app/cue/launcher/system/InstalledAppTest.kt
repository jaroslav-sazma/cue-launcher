package app.cue.launcher.system

import app.cue.launcher.data.layout.ComponentRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.text.Collator
import java.util.Locale

class InstalledAppTest {

    private val order = installedAppOrder(Collator.getInstance(Locale.forLanguageTag("cs")))

    @Test
    fun `a diacritic label sorts by its base letter, not after Z`() {
        val apps = listOf(app("Zprávy"), app("Úkoly"), app("Auto"))

        val sorted = apps.sortedWith(order).map(InstalledApp::label)

        assertEquals(listOf("Auto", "Úkoly", "Zprávy"), sorted)

        assertEquals(listOf("Auto", "Zprávy", "Úkoly"), apps.sortedBy(InstalledApp::label).map(InstalledApp::label))
    }

    @Test
    fun `case does not decide the order`() {
        val apps = listOf(app("beta"), app("Alfa"), app("alfa2"))

        assertEquals(listOf("Alfa", "alfa2", "beta"), apps.sortedWith(order).map(InstalledApp::label))
    }

    @Test
    fun `equal labels are sorted by component, to keep order stable`() {
        val second = app(label = "Nastavení", packageName = "com.b.app")
        val first = app(label = "Nastavení", packageName = "com.a.app")

        assertEquals(listOf(first, second), listOf(second, first).sortedWith(order))
    }

    @Test
    fun `the same app in two profiles stands side by side, personal first`() {
        val personal = app(label = "Gmail", packageName = "com.google.android.gm", userSerial = 0)
        val work = app(label = "Gmail", packageName = "com.google.android.gm", userSerial = 10)
        val other = app(label = "Fotky", packageName = "com.google.android.apps.photos")

        val sorted = listOf(work, other, personal).sortedWith(order)

        assertEquals(listOf(other, personal, work), sorted)
    }

    @Test
    fun `ch sorts after h, not after c`() {
        val apps = listOf(app("Chrome"), app("Hodiny"), app("Ikony"), app("Cena"))

        assertEquals(
            listOf("Cena", "Hodiny", "Chrome", "Ikony"),
            apps.sortedWith(order).map(InstalledApp::label),
        )
    }

    private fun app(
        label: String,
        packageName: String = "app.test.${label.hashCode()}",
        className: String = "$packageName.Main",
        userSerial: Long = 0,
    ) = InstalledApp(component = ComponentRef(packageName, className, userSerial), label = label)
}

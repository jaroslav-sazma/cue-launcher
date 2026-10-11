package app.cue.launcher.system.widget

import app.cue.launcher.data.layout.ComponentRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AvailableWidgetTest {

    @Test
    fun `sorts by app, then by label`() {
        val widgets = listOf(
            widget("com.b", label = "Počasí", appLabel = "Meteo"),
            widget("com.a", label = "Úkoly", appLabel = "Kalendář"),
            widget("com.a", label = "Měsíc", appLabel = "Kalendář", className = "Month"),
        )

        val ordered = widgets.sortedWith(availableWidgetOrder()).map(AvailableWidget::label)

        assertEquals(listOf("Měsíc", "Úkoly", "Počasí"), ordered)
    }

    @Test
    fun `diacritics sort by their base letter`() {
        val widgets = listOf(
            widget("com.c", label = "Zprávy", appLabel = "Zprávy"),
            widget("com.a", label = "Čas", appLabel = "Čas"),
            widget("com.b", label = "Domů", appLabel = "Domů"),
        )

        val ordered = widgets.sortedWith(availableWidgetOrder()).map(AvailableWidget::appLabel)

        assertEquals(listOf("Čas", "Domů", "Zprávy"), ordered)
    }

    @Test
    fun `the same widget in two profiles keeps a stable order`() {
        val personal = widget("com.a", label = "Kalendář", appLabel = "Kalendář", userSerial = 0)
        val work = widget("com.a", label = "Kalendář", appLabel = "Kalendář", userSerial = 11)

        val ordered = listOf(work, personal).sortedWith(availableWidgetOrder())

        assertEquals(listOf(personal, work), ordered)
    }

    private fun widget(
        packageName: String,
        label: String,
        appLabel: String,
        className: String = "Provider",
        userSerial: Long = 0,
    ) = AvailableWidget(
        provider = ComponentRef(packageName, "$packageName.$className", userSerial),
        label = label,
        appLabel = appLabel,
        needsConfiguration = false,
        size = WidgetSizeSpec(minWidthPx = 100, minHeightPx = 100, horizontallyResizable = false),
    )
}

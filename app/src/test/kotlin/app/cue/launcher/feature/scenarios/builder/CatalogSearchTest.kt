package app.cue.launcher.feature.scenarios.builder

import app.cue.launcher.data.scenario.ConditionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogSearchTest {

    @Test
    fun `an empty query returns the whole catalog in order`() {
        assertEquals(ENTRIES.map(CatalogEntry::type), searchCatalog(ENTRIES, ""))
        assertEquals(ENTRIES.map(CatalogEntry::type), searchCatalog(ENTRIES, "   "))
    }

    @Test
    fun `auto finds bluetooth through the examples`() {
        val found = searchCatalog(ENTRIES, "auto")

        assertEquals(ConditionType.BT_CONNECTED, found.first())
        assertEquals(setOf(ConditionType.BT_CONNECTED, ConditionType.BT_DISCONNECTED), found.toSet())
    }

    @Test
    fun `a query without diacritics matches a name with diacritics`() {
        assertEquals(ConditionType.TIME_WINDOW, searchCatalog(ENTRIES, "casove").first())
    }

    @Test
    fun `the name wins over the examples`() {
        val found = searchCatalog(ENTRIES, "cas")

        assertEquals(ConditionType.TIME_WINDOW, found.first())
        assertTrue(found.contains(ConditionType.TIME_INSTANT))
    }

    @Test
    fun `a typo in the name is forgiven`() {
        assertTrue(searchCatalog(ENTRIES, "nabijeni").contains(ConditionType.CHARGING))
        assertTrue(searchCatalog(ENTRIES, "nabjieni").contains(ConditionType.CHARGING))
    }

    @Test
    fun `a nonsensical query returns nothing`() {
        assertTrue(searchCatalog(ENTRIES, "xyzzy").isEmpty())
    }

    @Test
    fun `results are further split into sections`() {
        val sections = catalogSections(searchCatalog(ENTRIES, "blue"))

        assertEquals(
            listOf(ConditionSection.STATE, ConditionSection.INSTANT),
            sections.map { it.section },
        )
    }

    private companion object {

        val ENTRIES = listOf(
            CatalogEntry(ConditionType.TIME_WINDOW, "Časové okno", "18–23, po–ne, večer, ráno"),
            CatalogEntry(ConditionType.BT_CONNECTED, "Bluetooth připojeno", "auto, sluchátka, handsfree"),
            CatalogEntry(ConditionType.WIFI, "Wi-Fi", "doma, práce, síť"),
            CatalogEntry(ConditionType.CHARGING, "Nabíjení", "nabíječka, kabel, baterie"),
            CatalogEntry(ConditionType.TIME_INSTANT, "Okamžik v čase", "budík, připomínka, v 17:00"),
            CatalogEntry(ConditionType.BT_DISCONNECTED, "Bluetooth odpojeno", "kde parkuju, odchod z auta"),
        )
    }
}

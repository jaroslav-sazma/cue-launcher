package app.cue.launcher.feature.drawer.vault

import app.cue.launcher.feature.drawer.search.normalizeForSearch
import app.cue.launcher.system.InstalledApp
import app.cue.launcher.system.installedAppOrder
import java.text.Collator

data class VaultSection(val letter: String, val apps: List<InstalledApp>)

class Vault(val sections: List<VaultSection>) {

    val letters: List<String> = sections.map(VaultSection::letter)

    private val headerIndices: List<Int> = buildList {
        var index = 0
        sections.forEach { section ->
            add(index)
            index += section.apps.size + 1
        }
    }

    fun headerItemIndex(sectionIndex: Int): Int = headerIndices.getOrElse(sectionIndex) { 0 }

    companion object {
        val EMPTY = Vault(emptyList())
    }
}

fun vaultOf(apps: List<InstalledApp>, collator: Collator = Collator.getInstance()): Vault {
    val order = installedAppOrder(collator)
    val letterOrder = Comparator<String> { first, second ->
        val byBucket = otherLast(first).compareTo(otherLast(second))
        if (byBucket != 0) byBucket else collator.compare(first, second)
    }
    val sections = apps.groupBy { vaultLetterOf(it.label) }
        .entries
        .sortedWith(compareBy(letterOrder) { it.key })
        .map { (letter, group) -> VaultSection(letter, group.sortedWith(order)) }
    return Vault(sections)
}

fun vaultLetterOf(label: String): String {
    val first = normalizeForSearch(label).firstOrNull() ?: return OTHER_LETTER
    return if (first.isLetter()) first.uppercaseChar().toString() else OTHER_LETTER
}

private fun otherLast(letter: String): Int = if (letter == OTHER_LETTER) 1 else 0

const val OTHER_LETTER = "#"

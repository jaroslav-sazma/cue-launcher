package app.cue.launcher.system

import app.cue.launcher.data.layout.StoredPackage

data class ChangedSince(val sequence: Int, val packageNames: List<String>)

// changed is null in a fresh process: without a previous mark nothing can be said about what changed
// while it was not running, so everything stored is checked.
data class PackageCatchUp(val mark: Int, val changed: List<String>?)

fun packageCatchUp(previous: Int?, changedSince: (sequence: Int) -> ChangedSince?): PackageCatchUp {
    val changes = changedSince(previous ?: 0)
    return PackageCatchUp(
        mark = changes?.sequence ?: previous ?: 0,
        changed = previous?.let { changes?.packageNames.orEmpty() },
    )
}

// getChangedPackages only reports the launcher's own profile, so a work profile or a private space
// is checked in full every time.
fun packagesToCheck(stored: List<StoredPackage>, changed: List<String>?, ownSerial: Long): List<StoredPackage> {
    if (changed == null) return stored.distinct()
    val names = changed.toSet()
    return stored.distinct().filter { it.userSerial != ownSerial || it.packageName in names }
}

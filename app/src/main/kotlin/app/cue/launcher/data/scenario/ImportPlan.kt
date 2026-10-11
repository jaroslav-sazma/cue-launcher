package app.cue.launcher.data.scenario

data class ImportPlan(

    val setName: String? = null,
    val entries: List<ImportEntry> = emptyList(),

    val rejected: List<RejectedScenario> = emptyList(),

    val config: LauncherConfig? = null,

    val skipped: Int = 0,
) {

    val hasConfig: Boolean get() = config != null

    val collisions: List<ImportEntry> get() = entries.filter(ImportEntry::isCollision)

    val incomplete: List<ImportEntry> get() = entries.filter(ImportEntry::needsPlaceholders)

    val isEmpty: Boolean get() = entries.isEmpty() && !hasConfig

    val scenarios: List<Scenario> get() = entries.map(ImportEntry::scenario)
}

data class ImportEntry(
    val scenario: Scenario,
    val sourceId: String,
    val placeholders: Set<String> = emptySet(),
) {
    val isCollision: Boolean get() = scenario.id != sourceId

    val needsPlaceholders: Boolean get() = placeholders.isNotEmpty()
}

fun importPlanOf(read: ScenarioRead.Ok, existingIds: Set<String>): ImportPlan {
    val taken = existingIds.toMutableSet()
    val entries = read.accepted.map { imported ->
        val id = freeScenarioId(imported.scenario.id, taken)
        taken += id
        ImportEntry(
            scenario = imported.asStored().copy(id = id),
            sourceId = imported.scenario.id,
            placeholders = imported.placeholders,
        )
    }
    return ImportPlan(
        setName = read.setName,
        entries = entries,
        rejected = read.rejected,
        config = read.config,
        skipped = read.skipped,
    )
}

fun freeScenarioId(id: String, taken: Set<String>): String {
    if (id !in taken) return id
    var suffix = 2
    while ("$id-$suffix" in taken) suffix++
    return "$id-$suffix"
}

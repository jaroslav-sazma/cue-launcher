package app.cue.launcher.system.widget

data class WidgetReconciliation(

    val releaseFromHost: List<Int>,

    val forgetRows: List<Int>,
) {
    val isEmpty: Boolean get() = releaseFromHost.isEmpty() && forgetRows.isEmpty()
}

fun reconcileWidgets(
    hostIds: Set<Int>,
    knownIds: Set<Int>,
    isBound: (Int) -> Boolean,
): WidgetReconciliation {
    val unbound = hostIds.intersect(knownIds).filterNot(isBound)
    return WidgetReconciliation(
        releaseFromHost = ((hostIds - knownIds) + unbound).sorted(),
        forgetRows = ((knownIds - hostIds) + unbound).sorted(),
    )
}

fun orphanScenarioWidgets(scenarioBoundIds: Set<Int>, referencedIds: Set<Int>): List<Int> =
    (scenarioBoundIds - referencedIds).sorted()

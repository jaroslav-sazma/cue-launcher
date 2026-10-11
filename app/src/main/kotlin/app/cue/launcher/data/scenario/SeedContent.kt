package app.cue.launcher.data.scenario

const val CLOCK_SEED_SCENARIO_ID: String = "cue-seed-clock"

fun clockSeedScenario(name: String): Scenario = Scenario(
    id = CLOCK_SEED_SCENARIO_ID,
    name = name,
    size = TileSize.HERO_4X2,
    content = TileContent.LauncherWidget(LauncherWidgetType.CLOCK),
)

// The seed name is stored, not resolved at render time, so it has to be rewritten when the device
// language changes — but only while it still is one of the seeded names, never over a rename.
fun relabeledSeedScenario(seed: Scenario, name: String, seedNames: Set<String>): Scenario? =
    seed.takeIf { it.name != name && it.name in seedNames }?.copy(name = name)

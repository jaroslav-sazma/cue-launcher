package app.cue.launcher.feature.scenarios

import app.cue.launcher.data.scenario.Condition
import app.cue.launcher.data.scenario.Scenario
import app.cue.launcher.data.scenario.TemplateLayout
import app.cue.launcher.data.scenario.TileContent
import app.cue.launcher.data.scenario.TileSize

fun scenario(
    id: String = "s1",
    name: String = "Scénář",
    size: TileSize = TileSize.GRID_2X2,
    priority: Int = 0,
    conditions: List<Condition> = emptyList(),
): Scenario = Scenario(
    id = id,
    name = name,
    priority = priority,
    size = size,
    conditions = conditions,
    content = TileContent.Template(layout = TemplateLayout.TEXT_ONLY),
)

package app.cue.launcher.data.scenario

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenario")
data class ScenarioEntity(
    @PrimaryKey val id: String,

    val name: String,
    val enabled: Boolean,
    val priority: Int,

    val size: String,

    val json: String,
)

fun Scenario.toEntity(): ScenarioEntity = ScenarioEntity(
    id = id,
    name = name,
    enabled = enabled,
    priority = priority,
    size = size.jsonName,
    json = ScenarioFormat.canonicalJson(this),
)

fun ScenarioEntity.toScenario(): Scenario? = ScenarioFormat.decodeCanonical(json)

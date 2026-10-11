package app.cue.launcher.data.scenario

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "scenario_activation",
    foreignKeys = [
        ForeignKey(
            entity = ScenarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActivationEntity(
    @PrimaryKey val scenarioId: String,
    val since: Long,
    val expiresAt: Long?,
    val payload: String? = null,
)

fun Activation.toEntity(): ActivationEntity = ActivationEntity(
    scenarioId = scenarioId,
    since = since,
    expiresAt = expiresAt,
    payload = payload.encodeOrNull(),
)

fun ActivationEntity.toActivation(): Activation = Activation(
    scenarioId = scenarioId,
    since = since,
    expiresAt = expiresAt,
    payload = triggerPayloadOf(payload),
)

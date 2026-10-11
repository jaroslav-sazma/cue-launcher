package app.cue.launcher.data.scenario

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TileProgress(
    val value: Int = 0,
    val max: Int = 0,
    val indeterminate: Boolean = false,
    val segments: List<TileProgressSegment> = emptyList(),
    val points: List<TileProgressPoint> = emptyList(),
)

@Serializable
data class TileProgressSegment(
    val length: Int,
    val style: SemanticStyle = SemanticStyle.UNSPECIFIED,
)

@Serializable
data class TileProgressPoint(
    val position: Int,
    val style: SemanticStyle = SemanticStyle.UNSPECIFIED,
)

@Serializable
enum class SemanticStyle {
    @SerialName("unspecified")
    UNSPECIFIED,

    @SerialName("info")
    INFO,

    @SerialName("safe")
    SAFE,

    @SerialName("caution")
    CAUTION,

    @SerialName("danger")
    DANGER,
}

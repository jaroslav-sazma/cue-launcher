package app.cue.launcher.engine

import app.cue.launcher.data.scenario.Activation
import app.cue.launcher.data.scenario.TriggerPayload
import java.util.Locale

const val PLACE_VALUE_NAMESPACE = "place"

private const val FIELD_LATITUDE = "$PLACE_VALUE_NAMESPACE:lat"
private const val FIELD_LONGITUDE = "$PLACE_VALUE_NAMESPACE:lng"
// No namespace, so no {namespace:key} in a tile's text can reach it and print a raw timestamp.
private const val FIELD_SAVED_AT = "place_saved_at"

private const val LATITUDE_LIMIT = 90.0
private const val LONGITUDE_LIMIT = 180.0

data class SavedPlace(val latitude: Double, val longitude: Double, val savedAt: Long)

fun placePayload(place: SavedPlace): TriggerPayload = TriggerPayload(
    fields = mapOf(
        FIELD_LATITUDE to coordinate(place.latitude),
        FIELD_LONGITUDE to coordinate(place.longitude),
        FIELD_SAVED_AT to place.savedAt.toString(),
    ),
)

fun savedPlaceOf(payload: TriggerPayload): SavedPlace? {
    val latitude = payload[FIELD_LATITUDE]?.toDoubleOrNull()?.takeIf { it in -LATITUDE_LIMIT..LATITUDE_LIMIT }
    val longitude = payload[FIELD_LONGITUDE]?.toDoubleOrNull()?.takeIf { it in -LONGITUDE_LIMIT..LONGITUDE_LIMIT }
    if (latitude == null || longitude == null) return null
    return SavedPlace(latitude, longitude, payload[FIELD_SAVED_AT]?.toLongOrNull() ?: 0L)
}

// The place lives in the activation that the save fired, so it is forgotten together with the tile.
fun latestSavedPlace(activations: List<Activation>, now: Long): SavedPlace? = activations
    .filterNot { it.isExpired(now) }
    .mapNotNull { savedPlaceOf(it.payload) }
    .maxByOrNull(SavedPlace::savedAt)

fun placeUri(place: SavedPlace): String {
    val point = "${coordinate(place.latitude)},${coordinate(place.longitude)}"
    return "geo:$point?q=$point"
}

private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

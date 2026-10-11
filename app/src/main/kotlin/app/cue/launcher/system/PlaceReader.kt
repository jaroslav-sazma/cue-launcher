package app.cue.launcher.system

import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.CancellationSignal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PlaceReading {

    data class Found(val latitude: Double, val longitude: Double) : PlaceReading

    data object PermissionMissing : PlaceReading

    data object LocationOff : PlaceReading

    data object NotFound : PlaceReading
}

// One fix for one tap: no updates are subscribed to, and the tile that asked cancels the request
// when the home screen stops. Approximate location is accepted — the place is then only roughly
// right, which is the user's choice to make.
@SingleIn(AppScope::class)
@Inject
class PlaceReader(private val application: Application) {

    suspend fun read(): PlaceReading {
        val manager = application.getSystemService(LocationManager::class.java)
        return when {
            manager == null -> PlaceReading.NotFound
            !approximateLocationGranted(application) -> PlaceReading.PermissionMissing
            !manager.isLocationEnabled -> PlaceReading.LocationOff
            else -> fix(manager)
        }
    }

    private suspend fun fix(manager: LocationManager): PlaceReading = try {
        withTimeoutOrNull(FIX_TIMEOUT_MS + FIX_GRACE_MS) { currentLocation(manager) }
            ?.let { PlaceReading.Found(it.latitude, it.longitude) }
            ?: PlaceReading.NotFound
    } catch (expected: SecurityException) {
        PlaceReading.PermissionMissing
    }

    private suspend fun currentLocation(manager: LocationManager): Location? {
        val provider = if (manager.hasProvider(LocationManager.FUSED_PROVIDER)) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.GPS_PROVIDER
        }
        val request = LocationRequest.Builder(0L)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setDurationMillis(FIX_TIMEOUT_MS)
            .build()
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(provider, request, signal, application.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (revoked: SecurityException) {
                continuation.resumeWithException(revoked)
            }
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 20_000L

        const val FIX_GRACE_MS = 1_000L
    }
}

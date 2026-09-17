package org.traccar.client

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@SuppressLint("MissingPermission")
class FusedLocationSource(
    scope: ComponentCoroutineScope,
    context: Context,
    profileController: TrackingProfileController,
    state: StateFlow<State>,
) : LocationSource {

    private val locationConfig = profileController.locationConfig
    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    override val positions = MutableSharedFlow<Position>(extraBufferCapacity = 8)

    private var callback: LocationCallback? = null
    private var activeConfig: LocationConfig? = null
    private var currentLocationToken: CancellationTokenSource? = null

    init {
        scope.launch {
            combine(state, locationConfig) { trackerState, config ->
                trackerState.locationMode() to config.effective
            }
                .distinctUntilChanged()
                .collect { (mode, config) ->
                    try {
                        when (mode) {
                            LocationMode.Active -> ensureStarted(config)
                            LocationMode.Stationary -> ensureStopped(awaitFinalFix = true)
                            LocationMode.Off -> ensureStopped(awaitFinalFix = false)
                        }
                    } catch (e: SecurityException) {
                        Log.log("Location permission missing: $e")
                    }
                }
        }
    }

    private fun ensureStarted(config: LocationConfig) {
        if (callback != null && activeConfig == config) return
        if (callback != null) stopUpdates()
        startUpdates(config)
    }

    private suspend fun ensureStopped(awaitFinalFix: Boolean) {
        if (callback == null) return
        if (awaitFinalFix) {
            val finalFix = awaitCurrentLocation(locationConfig.value.effective)
            finalFix?.let { positions.emit(it.toPosition()) }
        }
        stopUpdates()
    }

    override suspend fun fetchOnce(): Position? = try {
        val config = locationConfig.value.effective
        (awaitCurrentLocation(config) ?: awaitLastLocation())?.toPosition()
    } catch (e: SecurityException) {
        Log.log("Location permission missing: $e")
        null
    }

    private fun startUpdates(config: LocationConfig) {
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                positions.tryEmit(location.toPosition())
            }
        }
        val request = LocationRequest.Builder(
            config.accuracy.toFusedPriority(),
            config.intervalSeconds * 1000L,
        )
            .setMinUpdateDistanceMeters(config.distanceMeters.toFloat())
            .build()
        client.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
        callback = newCallback
        activeConfig = config
        Log.log("Location updates started profile=${config.accuracy}/${config.distanceMeters}m/${config.intervalSeconds}s")
    }

    private fun stopUpdates() {
        callback?.let {
            client.removeLocationUpdates(it)
            Log.log("Location updates stopped")
        }
        callback = null
        activeConfig = null
    }

    private suspend fun awaitCurrentLocation(config: LocationConfig): Location? {
        currentLocationToken?.cancel()
        val token = CancellationTokenSource()
        currentLocationToken = token
        val request = CurrentLocationRequest.Builder()
            .setPriority(config.accuracy.toFusedPriority())
            .setDurationMillis(LOCATION_FETCH_TIMEOUT.inWholeMilliseconds)
            .build()
        return try {
            suspendCancellableCoroutine { continuation ->
                client.getCurrentLocation(request, token.token)
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(it)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                continuation.invokeOnCancellation { token.cancel() }
            }
        } finally {
            if (currentLocationToken === token) currentLocationToken = null
        }
    }

    private suspend fun awaitLastLocation(): Location? = suspendCancellableCoroutine { continuation ->
        client.lastLocation
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
    }

    private fun Location.toPosition(): Position = Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy.toDouble().takeIf { hasAccuracy() && it.isFinite() },
        time = time,
        altitude = altitude.takeIf { hasAltitude() && it.isFinite() },
        speed = speed.toDouble().takeIf { hasSpeed() && it.isFinite() },
        bearing = bearing.toDouble().takeIf { hasBearing() && it.isFinite() },
    )
}

private fun Accuracy.toFusedPriority(): Int = when (this) {
    Accuracy.HIGHEST, Accuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
    Accuracy.MEDIUM -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    Accuracy.LOW -> Priority.PRIORITY_LOW_POWER
}

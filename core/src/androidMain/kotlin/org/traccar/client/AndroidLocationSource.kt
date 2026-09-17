package org.traccar.client

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
class AndroidLocationSource(
    scope: ComponentCoroutineScope,
    context: Context,
    profileController: TrackingProfileController,
    state: StateFlow<State>,
) : LocationSource {

    private val locationConfig = profileController.locationConfig
    private val appContext = context.applicationContext
    private val locationManager: LocationManager = checkNotNull(appContext.getSystemService())

    override val positions = MutableSharedFlow<Position>(extraBufferCapacity = 8)

    private var listener: LocationListenerCompat? = null
    private var activeConfig: LocationConfig? = null
    private var currentLocationCancellation: CancellationSignal? = null

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
        if (listener != null && activeConfig == config) return
        if (listener != null) stopUpdates()
        startUpdates(config)
    }

    private suspend fun ensureStopped(awaitFinalFix: Boolean) {
        if (listener == null) return
        if (awaitFinalFix) {
            val finalFix = awaitCurrentLocation(locationConfig.value.effective)
            finalFix?.let { positions.emit(it.toPosition()) }
        }
        stopUpdates()
    }

    override suspend fun fetchOnce(): Position? = try {
        val config = locationConfig.value.effective
        val fresh = withTimeoutOrNull(LOCATION_FETCH_TIMEOUT) { awaitCurrentLocation(config) }
        (fresh ?: locationManager.getLastKnownLocation(config.accuracy.toAndroidProvider()))
            ?.toPosition()
    } catch (e: SecurityException) {
        Log.log("Location permission missing: $e")
        null
    }

    private fun startUpdates(config: LocationConfig) {
        val newListener = LocationListenerCompat { location ->
            positions.tryEmit(location.toPosition())
        }
        locationManager.requestLocationUpdates(
            config.accuracy.toAndroidProvider(),
            config.intervalSeconds * 1000L,
            config.distanceMeters.toFloat(),
            newListener,
            Looper.getMainLooper(),
        )
        listener = newListener
        activeConfig = config
        Log.log("Location updates started profile=${config.accuracy}/${config.distanceMeters}m/${config.intervalSeconds}s")
    }

    private fun stopUpdates() {
        listener?.let {
            locationManager.removeUpdates(it)
            Log.log("Location updates stopped")
        }
        listener = null
        activeConfig = null
    }

    private suspend fun awaitCurrentLocation(config: LocationConfig): Location? {
        currentLocationCancellation?.cancel()
        val signal = CancellationSignal()
        currentLocationCancellation = signal
        return try {
            suspendCancellableCoroutine { continuation ->
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    config.accuracy.toAndroidProvider(),
                    signal,
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                continuation.invokeOnCancellation { signal.cancel() }
            }
        } finally {
            if (currentLocationCancellation === signal) currentLocationCancellation = null
        }
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

    private fun Accuracy.toAndroidProvider(): String {
        val preferred = when (this) {
            Accuracy.HIGHEST, Accuracy.HIGH -> LocationManager.GPS_PROVIDER
            Accuracy.MEDIUM -> LocationManager.NETWORK_PROVIDER
            Accuracy.LOW -> LocationManager.PASSIVE_PROVIDER
        }
        val available = locationManager.allProviders
        return preferred.takeIf { it in available } ?: available.firstOrNull()
            ?: throw IllegalStateException("No location provider available")
    }
}

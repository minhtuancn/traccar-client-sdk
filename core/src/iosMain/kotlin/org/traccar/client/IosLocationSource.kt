package org.traccar.client

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

class IosLocationSource(
    scope: ComponentCoroutineScope,
    profileController: TrackingProfileController,
    state: StateFlow<State>,
) : LocationSource {

    private val locationConfig = profileController.locationConfig
    private val mainScope = scope + Dispatchers.Main

    override val positions = MutableSharedFlow<Position>(extraBufferCapacity = 8)

    private var manager: CLLocationManager? = null
    private var delegate: CLLocationManagerDelegateProtocol? = null
    private var activeConfig: LocationConfig? = null
    private var lastLocation: CLLocation? = null
    private var pendingLocation: CompletableDeferred<CLLocation?>? = null

    init {
        mainScope.launch {
            combine(state, locationConfig) { trackerState, config ->
                trackerState.locationMode() to config.effective
            }
                .distinctUntilChanged()
                .collect { (mode, config) ->
                    when (mode) {
                        LocationMode.Active -> ensureStarted(config)
                        LocationMode.Stationary -> ensureStopped(awaitFinalFix = true)
                        LocationMode.Off -> ensureStopped(awaitFinalFix = false)
                    }
                }
        }
    }

    private suspend fun ensureStarted(config: LocationConfig) {
        if (manager != null && activeConfig == config) return
        if (manager != null) ensureStopped(awaitFinalFix = false)

        val authStatus = CompletableDeferred<Boolean>()
        val newDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.forEach { value ->
                    val location = value as CLLocation
                    lastLocation = location
                    positions.tryEmit(location.toPosition())
                    pendingLocation?.complete(location)
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                pendingLocation?.complete(null)
            }

            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                if (authStatus.isCompleted) return
                when (manager.authorizationStatus) {
                    kCLAuthorizationStatusAuthorizedAlways,
                    kCLAuthorizationStatusAuthorizedWhenInUse -> authStatus.complete(true)
                    kCLAuthorizationStatusDenied,
                    kCLAuthorizationStatusRestricted -> authStatus.complete(false)
                }
            }
        }
        delegate = newDelegate

        val newManager = CLLocationManager().apply {
            this.delegate = newDelegate
            desiredAccuracy = config.accuracy.toIosAccuracy()
            distanceFilter = if (config.distanceMeters > 0) {
                config.distanceMeters.toDouble()
            } else {
                kCLDistanceFilterNone
            }
            allowsBackgroundLocationUpdates = true
            pausesLocationUpdatesAutomatically = false
        }

        val authorized = when (newManager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> true
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted -> false
            else -> {
                newManager.requestAlwaysAuthorization()
                authStatus.await()
            }
        }

        if (!authorized) {
            Log.log("Location permission denied")
            newManager.delegate = null
            delegate = null
            return
        }

        newManager.startMonitoringSignificantLocationChanges()
        newManager.startUpdatingLocation()
        manager = newManager
        activeConfig = config
        Log.log("Location updates started profile=${config.accuracy}/${config.distanceMeters}m/${config.intervalSeconds}s")
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun ensureStopped(awaitFinalFix: Boolean) {
        val current = manager ?: return
        if (awaitFinalFix) {
            val deferred = CompletableDeferred<CLLocation?>()
            pendingLocation = deferred
            current.requestLocation()
            val finalFix = try {
                withTimeoutOrNull(10.seconds) { deferred.await() }
            } finally {
                pendingLocation = null
            }
            finalFix?.let { positions.tryEmit(it.toPosition()) }
        }
        current.stopUpdatingLocation()
        current.stopMonitoringSignificantLocationChanges()
        current.delegate = null
        Log.log("Location updates stopped")
        manager = null
        delegate = null
        activeConfig = null
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun fetchOnce(): Position? = withContext(Dispatchers.Main) {
        manager?.let { return@withContext fetchOnce(it) }

        val config = locationConfig.value.effective
        val newDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.forEach { value ->
                    pendingLocation?.complete(value as CLLocation)
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                pendingLocation?.complete(null)
            }
        }
        val transient = CLLocationManager().apply {
            delegate = newDelegate
            desiredAccuracy = config.accuracy.toIosAccuracy()
            allowsBackgroundLocationUpdates = true
        }
        try {
            fetchOnce(transient)
        } finally {
            transient.delegate = null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun fetchOnce(active: CLLocationManager): Position? {
        val deferred = CompletableDeferred<CLLocation?>()
        pendingLocation = deferred
        active.requestLocation()
        val fresh = try {
            withTimeoutOrNull(LOCATION_FETCH_TIMEOUT) { deferred.await() }
        } finally {
            pendingLocation = null
        }
        return (fresh ?: active.location ?: lastLocation)?.toPosition()
    }
}

private fun Accuracy.toIosAccuracy(): Double = when (this) {
    Accuracy.HIGHEST -> kCLLocationAccuracyBestForNavigation
    Accuracy.HIGH -> kCLLocationAccuracyBest
    Accuracy.MEDIUM -> kCLLocationAccuracyHundredMeters
    Accuracy.LOW -> kCLLocationAccuracyKilometer
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toPosition(): Position {
    val (latitude, longitude) = coordinate.useContents { latitude to longitude }
    val timestamp: NSDate = timestamp
    return Position(
        latitude = latitude,
        longitude = longitude,
        accuracy = horizontalAccuracy,
        time = (timestamp.timeIntervalSince1970 * 1000).toLong(),
        altitude = if (verticalAccuracy >= 0) altitude else null,
        speed = if (speed >= 0) speed else null,
        bearing = if (course >= 0) course else null,
    )
}

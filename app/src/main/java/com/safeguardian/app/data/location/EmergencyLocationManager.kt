package com.safeguardian.app.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.safeguardian.app.domain.response.GeoLocation
import com.safeguardian.app.domain.response.LocationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * On-demand emergency location manager.
 *
 * Strict Privacy & Battery Rules:
 * - GPS / Location tracking is COMPLETELY OFF during normal monitoring.
 * - Activates only when entering VERIFICATION_COUNTDOWN or ACTIVE_EMERGENCY.
 * - Periodically updates while ACTIVE_EMERGENCY is ongoing.
 * - Stops immediately when emergency is RESOLVED or CANCELLED.
 */
class EmergencyLocationManager(
    private val context: Context
) {
    private val tag = "EmergencyLocationMgr"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _locationState = MutableStateFlow(LocationState.STOPPED)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoLocation?>(null)
    val currentLocation: StateFlow<GeoLocation?> = _currentLocation.asStateFlow()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val geo = GeoLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                timestamp = location.time
            )
            _currentLocation.value = geo
            _locationState.value = LocationState.AVAILABLE
            Log.d(tag, "Acquired emergency location (accuracy: ±${location.accuracy}m)")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            _locationState.value = LocationState.UNAVAILABLE
        }
    }

    /**
     * Start acquiring location for active emergency response.
     */
    @SuppressLint("MissingPermission")
    fun startEmergencyTracking() {
        if (_locationState.value == LocationState.UPDATING || _locationState.value == LocationState.ACQUIRING) return

        val mgr = locationManager
        if (mgr == null) {
            _locationState.value = LocationState.UNAVAILABLE
            return
        }

        _locationState.value = LocationState.ACQUIRING

        try {
            // First check last known location for instant availability
            val lastGps = mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = mgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = listOfNotNull(lastGps, lastNet).maxByOrNull { it.time }

            if (best != null && (System.currentTimeMillis() - best.time) < 120_000L) {
                _currentLocation.value = GeoLocation(
                    latitude = best.latitude,
                    longitude = best.longitude,
                    accuracyMeters = best.accuracy,
                    timestamp = best.time
                )
                _locationState.value = LocationState.AVAILABLE
            }

            // Register for regular periodic updates (e.g., every 10 seconds or 10 meters)
            if (mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mgr.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10_000L,
                    10.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                _locationState.value = if (_currentLocation.value != null) LocationState.UPDATING else LocationState.ACQUIRING
            } else if (mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mgr.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10_000L,
                    10.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                _locationState.value = if (_currentLocation.value != null) LocationState.UPDATING else LocationState.ACQUIRING
            } else {
                _locationState.value = LocationState.UNAVAILABLE
            }
        } catch (e: SecurityException) {
            Log.w(tag, "Location permission missing: ${e.message}")
            _locationState.value = LocationState.UNAVAILABLE
        } catch (e: Exception) {
            Log.w(tag, "Failed to start location updates: ${e.message}")
            _locationState.value = LocationState.UNAVAILABLE
        }
    }

    /**
     * Stop location updates and release GPS hardware immediately.
     */
    fun stopEmergencyTracking() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.w(tag, "Error stopping location updates: ${e.message}")
        }
        _locationState.value = LocationState.STOPPED
    }

    /**
     * For unit tests and simulations: inject simulated location coordinates.
     */
    fun injectSimulatedLocation(lat: Double, lng: Double, accuracy: Float = 5.0f) {
        _currentLocation.value = GeoLocation(lat, lng, accuracy, System.currentTimeMillis())
        _locationState.value = LocationState.AVAILABLE
    }

    fun setLocationUnavailable() {
        _currentLocation.value = null
        _locationState.value = LocationState.UNAVAILABLE
    }
}

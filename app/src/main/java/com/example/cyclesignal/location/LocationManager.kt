package com.example.cyclesignal.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.cyclesignal.data.SpeedUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class CycleLocationManager(private val context: Context) : LocationListener {
    
    companion object {
        private const val TAG = "CycleLocationManager"
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val MIN_DISTANCE_M = 1f
    }
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    private val _speedText = MutableStateFlow("0 km/h")
    val speedText: StateFlow<String> = _speedText.asStateFlow()
    
    fun startLocationUpdates(speedUnit: String) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            _speedText.value = "GPS Off"
            return
        }
        
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "GPS provider is not enabled")
            _speedText.value = "GPS Disabled"
            return
        }
        
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                MIN_DISTANCE_M,
                this
            )
        } catch (ex: SecurityException) {
            Log.e(TAG, "Location permission denied: ${ex.message}")
            _speedText.value = "GPS Error"
        }
    }
    
    fun stopLocationUpdates() {
        if (hasLocationPermission()) {
            locationManager.removeUpdates(this)
        }
    }
    
    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            val speedMps = location.speed
            val speedText = if (_currentSpeedUnit == SpeedUnit.MPH) {
                val speedMph = (speedMps * 2.23694).roundToInt()
                "$speedMph mph"
            } else {
                val speedKmh = (speedMps * 3.6).roundToInt()
                "$speedKmh km/h"
            }
            _speedText.value = speedText
        } else {
            _speedText.value = "0 $_currentSpeedUnit"
        }
    }
    
    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "Location provider status changed: $provider, status: $status")
    }
    
    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "Location provider enabled: $provider")
    }
    
    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "Location provider disabled: $provider")
        _speedText.value = "GPS Disabled"
    }
    
    private var _currentSpeedUnit = SpeedUnit.KMH
    
    fun updateSpeedUnit(speedUnit: String) {
        _currentSpeedUnit = speedUnit
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
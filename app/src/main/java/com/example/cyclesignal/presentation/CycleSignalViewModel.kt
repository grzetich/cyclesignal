package com.example.cyclesignal.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cyclesignal.data.CycleSignalState
import com.example.cyclesignal.data.PreferenceKeys
import com.example.cyclesignal.data.SpeedUnit
import com.example.cyclesignal.location.CycleLocationManager
import com.example.cyclesignal.sensors.CycleSensorManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CycleSignalViewModel(
    private val context: Context,
    private val sensorManager: CycleSensorManager,
    private val locationManager: CycleLocationManager
) : ViewModel() {
    
    private val sharedPreferences = context.getSharedPreferences(
        PreferenceKeys.PREF_FILE_NAME,
        Context.MODE_PRIVATE
    )
    
    private val _uiState = MutableStateFlow(CycleSignalState())
    val uiState: StateFlow<CycleSignalState> = _uiState.asStateFlow()
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val calendar = Calendar.getInstance()
    
    init {
        loadPreferences()
        startSensorUpdates()
        startLocationUpdates()
        startTimeUpdates()
    }
    
    private fun loadPreferences() {
        val isRightWrist = sharedPreferences.getBoolean(PreferenceKeys.PREF_WRIST_ORIENTATION, false)
        val speedUnit = sharedPreferences.getString(PreferenceKeys.PREF_SPEED_UNIT, SpeedUnit.KMH) ?: SpeedUnit.KMH
        
        _uiState.update { currentState ->
            currentState.copy(
                isRightWrist = isRightWrist,
                speedUnit = speedUnit
            )
        }
        
        locationManager.updateSpeedUnit(speedUnit)
    }
    
    private fun startSensorUpdates() {
        sensorManager.startListening()
        
        // Observe sensor data changes
        viewModelScope.launch {
            sensorManager.sensorData.collect { sensorData ->
                _uiState.update { currentState ->
                    currentState.copy(
                        sensorData = sensorData,
                        cardinalDirection = sensorManager.getCardinalDirection()
                    )
                }
                
                // Update display state based on sensor data
                sensorManager.updateDisplayState(uiState.value.isRightWrist)
            }
        }
        
        // Observe display state changes
        viewModelScope.launch {
            sensorManager.displayState.collect { displayState ->
                _uiState.update { currentState ->
                    currentState.copy(displayState = displayState)
                }
            }
        }
    }
    
    private fun startLocationUpdates() {
        locationManager.startLocationUpdates(uiState.value.speedUnit)
        
        viewModelScope.launch {
            locationManager.speedText.collect { speedText ->
                _uiState.update { currentState ->
                    currentState.copy(speed = speedText)
                }
            }
        }
    }
    
    private fun startTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                calendar.timeInMillis = System.currentTimeMillis()
                val timeString = timeFormat.format(calendar.time)
                
                _uiState.update { currentState ->
                    currentState.copy(currentTime = timeString)
                }
                
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }
    
    fun onSettingsClick() {
        // TODO: Navigate to settings screen
    }
    
    override fun onCleared() {
        super.onCleared()
        sensorManager.stopListening()
        locationManager.stopLocationUpdates()
    }
}

class CycleSignalViewModelFactory(
    private val context: Context,
    private val sensorManager: CycleSensorManager,
    private val locationManager: CycleLocationManager
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CycleSignalViewModel::class.java)) {
            return CycleSignalViewModel(context, sensorManager, locationManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
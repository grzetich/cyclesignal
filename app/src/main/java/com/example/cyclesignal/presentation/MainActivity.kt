package com.example.cyclesignal.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.TimeText
import com.example.cyclesignal.data.CycleSignalState
import com.example.cyclesignal.location.CycleLocationManager
import com.example.cyclesignal.presentation.components.CycleSignalScreen
import com.example.cyclesignal.presentation.theme.CycleSignalTheme
import com.example.cyclesignal.sensors.CycleSensorManager

class MainActivity : ComponentActivity() {
    
    private lateinit var sensorManager: CycleSensorManager
    private lateinit var locationManager: CycleLocationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        // Initialize managers
        sensorManager = CycleSensorManager(this)
        locationManager = CycleLocationManager(this)

        setContent {
            CycleSignalTheme {
                val viewModel: CycleSignalViewModel = viewModel(
                    factory = CycleSignalViewModelFactory(
                        context = this@MainActivity,
                        sensorManager = sensorManager,
                        locationManager = locationManager
                    )
                )
                
                val uiState by viewModel.uiState.collectAsState()
                
                CycleSignalApp(
                    uiState = uiState,
                    onSettingsClick = viewModel::onSettingsClick
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::sensorManager.isInitialized) {
            sensorManager.stopListening()
        }
        if (::locationManager.isInitialized) {
            locationManager.stopLocationUpdates()
        }
    }
}

@Composable
fun CycleSignalApp(
    uiState: CycleSignalState,
    onSettingsClick: () -> Unit
) {
    CycleSignalScreen(
        displayState = uiState.displayState,
        currentTime = uiState.currentTime,
        cardinalDirection = uiState.cardinalDirection,
        speed = uiState.speed,
        onSettingsClick = onSettingsClick
    )
}
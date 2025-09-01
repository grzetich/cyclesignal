package com.example.cyclesignal.data

// Enum to define the current display state of the watch
enum class DisplayState {
    NEUTRAL_INFO,
    ARROW_LEFT,
    ARROW_RIGHT,
    RED_FACE
}

// Data class to hold sensor readings
data class SensorData(
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val azimuthDegrees: Float = 0f,
    val accelerationMagnitude: Float = 0f
)

// Data class to hold app state
data class CycleSignalState(
    val displayState: DisplayState = DisplayState.NEUTRAL_INFO,
    val currentTime: String = "",
    val cardinalDirection: String = "N",
    val speed: String = "0 km/h",
    val isRightWrist: Boolean = false,
    val speedUnit: String = "kmh",
    val sensorData: SensorData = SensorData()
)

// Speed unit constants
object SpeedUnit {
    const val KMH = "kmh"
    const val MPH = "mph"
}

// Wrist orientation constants
object WristOrientation {
    const val LEFT = "left"
    const val RIGHT = "right"
}

// Shared preferences constants
object PreferenceKeys {
    const val PREF_FILE_NAME = "watch_face_prefs"
    const val PREF_WRIST_ORIENTATION = "wrist_orientation"
    const val PREF_SPEED_UNIT = "speed_unit"
}
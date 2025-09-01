package com.example.cyclesignal.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.cyclesignal.data.DisplayState
import com.example.cyclesignal.data.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class CycleSensorManager(context: Context) : SensorEventListener {
    
    // --- Thresholds for gesture detection ---
    companion object {
        // Neutral position thresholds
        private const val PITCH_THRESHOLD_NEUTRAL_ABS = 20f
        private const val ROLL_THRESHOLD_NEUTRAL_ABS = 20f
        private const val NEUTRAL_ACCEL_THRESHOLD = 0.8f
        
        // Straight side gesture thresholds
        private const val ROLL_THRESHOLD_STRAIGHT_SIDE_MIN_ABS = 60f
        private const val PITCH_THRESHOLD_STRAIGHT_SIDE_MAX_ABS = 30f
        
        // Elbow up gesture thresholds
        private const val PITCH_THRESHOLD_ELBOW_UP_MIN = -70f
        private const val PITCH_THRESHOLD_ELBOW_UP_MAX = -30f
        private const val ROLL_THRESHOLD_ELBOW_UP_MAX_ABS = 30f
        
        // Hand down gesture thresholds
        private const val PITCH_THRESHOLD_HAND_DOWN_MIN = 50f
        private const val PITCH_THRESHOLD_HAND_DOWN_MAX = 80f
        private const val ROLL_THRESHOLD_HAND_DOWN_MAX_ABS = 30f
        
        // Active gesture threshold
        private const val ACTIVE_GESTURE_ACCEL_THRESHOLD = 1.5f
        
        // Gesture hold duration
        private const val GESTURE_HOLD_DURATION_MS = 1000L
        
        // Azimuth thresholds for "behind them"
        private const val AZIMUTH_THRESHOLD_BEHIND_MIN = 135f
        private const val AZIMUTH_THRESHOLD_BEHIND_MAX = 225f
    }
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    
    private var accelerationMagnitude: Float = 0f
    private var lastGestureTime: Long = 0L
    private var currentDisplayState: DisplayState = DisplayState.NEUTRAL_INFO
    
    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()
    
    private val _displayState = MutableStateFlow(DisplayState.NEUTRAL_INFO)
    val displayState: StateFlow<DisplayState> = _displayState.asStateFlow()
    
    fun startListening() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)
    }
    
    fun stopListening() {
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                val linearAccelerationX = event.values[0] - gravity[0]
                val linearAccelerationY = event.values[1] - gravity[1]
                val linearAccelerationZ = event.values[2] - gravity[2]
                accelerationMagnitude = sqrt(
                    linearAccelerationX * linearAccelerationX +
                            linearAccelerationY * linearAccelerationY +
                            linearAccelerationZ * linearAccelerationZ
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                val pitchDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                
                val newSensorData = SensorData(
                    pitchDegrees = pitchDegrees,
                    rollDegrees = rollDegrees,
                    azimuthDegrees = azimuthDegrees,
                    accelerationMagnitude = accelerationMagnitude
                )
                
                _sensorData.value = newSensorData
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used for this application
    }
    
    fun updateDisplayState(isRightWrist: Boolean) {
        val newDisplayState = determineDisplayState(isRightWrist)
        
        // Check for gesture timeout
        val shouldRevertToNeutral = currentDisplayState != DisplayState.NEUTRAL_INFO &&
                accelerationMagnitude < NEUTRAL_ACCEL_THRESHOLD &&
                System.currentTimeMillis() - lastGestureTime > GESTURE_HOLD_DURATION_MS
        
        if (shouldRevertToNeutral) {
            currentDisplayState = DisplayState.NEUTRAL_INFO
            _displayState.value = DisplayState.NEUTRAL_INFO
        } else if (newDisplayState != currentDisplayState) {
            currentDisplayState = newDisplayState
            _displayState.value = newDisplayState
        }
    }
    
    private fun determineDisplayState(isRightWrist: Boolean): DisplayState {
        val sensorData = _sensorData.value
        val pitchDegrees = sensorData.pitchDegrees
        val rollDegrees = sensorData.rollDegrees
        val azimuthDegrees = sensorData.azimuthDegrees
        
        val isActiveGesture = accelerationMagnitude > ACTIVE_GESTURE_ACCEL_THRESHOLD
        val isWristFacingBehind = azimuthDegrees >= AZIMUTH_THRESHOLD_BEHIND_MIN &&
                azimuthDegrees <= AZIMUTH_THRESHOLD_BEHIND_MAX
        
        // Determine gesture based on wrist orientation
        if (!isRightWrist) { // Watch on LEFT wrist
            // ARROW_LEFT: Left arm straight out to side
            if (isActiveGesture && isWristFacingBehind &&
                rollDegrees > ROLL_THRESHOLD_STRAIGHT_SIDE_MIN_ABS &&
                abs(pitchDegrees) < PITCH_THRESHOLD_STRAIGHT_SIDE_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.ARROW_LEFT
            }
            // ARROW_RIGHT: Left arm out at shoulder, elbow 90° up
            else if (isActiveGesture && isWristFacingBehind &&
                pitchDegrees < PITCH_THRESHOLD_ELBOW_UP_MAX &&
                pitchDegrees > PITCH_THRESHOLD_ELBOW_UP_MIN &&
                abs(rollDegrees) < ROLL_THRESHOLD_ELBOW_UP_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.ARROW_RIGHT
            }
            // RED_FACE: Left arm to side, hand down
            else if (!isActiveGesture && isWristFacingBehind &&
                pitchDegrees > PITCH_THRESHOLD_HAND_DOWN_MIN &&
                pitchDegrees < PITCH_THRESHOLD_HAND_DOWN_MAX &&
                abs(rollDegrees) < ROLL_THRESHOLD_HAND_DOWN_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.RED_FACE
            }
        } else { // Watch on RIGHT wrist
            // ARROW_RIGHT: Right arm straight out to side
            if (isActiveGesture && isWristFacingBehind &&
                rollDegrees < -ROLL_THRESHOLD_STRAIGHT_SIDE_MIN_ABS &&
                abs(pitchDegrees) < PITCH_THRESHOLD_STRAIGHT_SIDE_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.ARROW_RIGHT
            }
        }
        
        // Neutral position
        if (accelerationMagnitude < NEUTRAL_ACCEL_THRESHOLD &&
            abs(pitchDegrees) < PITCH_THRESHOLD_NEUTRAL_ABS &&
            abs(rollDegrees) < ROLL_THRESHOLD_NEUTRAL_ABS) {
            return DisplayState.NEUTRAL_INFO
        }
        
        return currentDisplayState
    }
    
    fun getCardinalDirection(): String {
        val direction = (_sensorData.value.azimuthDegrees + 360) % 360
        return when {
            direction >= 337.5 || direction < 22.5 -> "N"
            direction >= 22.5 && direction < 67.5 -> "NE"
            direction >= 67.5 && direction < 112.5 -> "E"
            direction >= 112.5 && direction < 157.5 -> "SE"
            direction >= 157.5 && direction < 202.5 -> "S"
            direction >= 202.5 && direction < 247.5 -> "SW"
            direction >= 247.5 && direction < 292.5 -> "W"
            direction >= 292.5 && direction < 337.5 -> "NW"
            else -> "N/A"
        }
    }
}
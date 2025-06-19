// This file defines the main activity for the Wear OS app.
// It handles sensor data (accelerometer, rotation vector), location updates,
// and dynamically updates the UI based on arm gestures and user preferences
// for wrist orientation and speed units.

package com.example.wearosapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Tag for logging messages
private const val TAG = "WearOSMainActivity"

// Delay between UI updates (for sensor-driven changes)
private const val UI_UPDATE_DELAY_MS = 100L // 10 updates per second

// Delay for GPS updates (less frequent to save
battery)
private const val GPS_UPDATE_INTERVAL_MS = 5000L // Update every 5 seconds

// --- Thresholds for gesture detection (tuned for standard wrist wearing, may need real-world adjustment) ---
// These are in degrees for pitch and roll.
// Pitch: Rotation around X-axis (forward/backward tilt of the watch). Positive pitch means watch top points down.
// Roll: Rotation around Y-axis (side-to-side tilt of the watch). Positive roll means watch screen tilted to the left.

// Neutral position: Watch screen relatively flat (facing up), arm mostly still.
private const val PITCH_THRESHOLD_NEUTRAL_ABS = 20f // max absolute pitch for neutral
private const val ROLL_THRESHOLD_NEUTRAL_ABS = 20f // max absolute roll for neutral
private const val NEUTRAL_ACCEL_THRESHOLD = 0.8f // Max acceleration for "neutral" stillness (m/s^2)

// ARROW_LEFT (Left arm straight out to side): Watch rolled significantly outwards (left wrist)
// ARROW_RIGHT (Right arm straight out to side): Watch rolled significantly inwards (right wrist)
private const val ROLL_THRESHOLD_STRAIGHT_SIDE_MIN_ABS = 60f // min absolute roll for straight side
private const val PITCH_THRESHOLD_STRAIGHT_SIDE_MAX_ABS = 30f // max absolute pitch for straight side

// ARROW_RIGHT (Left arm out at shoulder, elbow 90° up): Watch pitched heavily forward (left wrist)
private const val PITCH_THRESHOLD_ELBOW_UP_MIN = -70f // min pitch (watch top points up, screen down)
private const val PITCH_THRESHOLD_ELBOW_UP_MAX = -30f // max pitch (watch top points up, screen down)
private const val ROLL_THRESHOLD_ELBOW_UP_MAX_ABS = 30f // max absolute roll for elbow up

// RED_FACE (Left arm to side, hand down): Watch vertical, pitched upwards (left wrist)
private const val PITCH_THRESHOLD_HAND_DOWN_MIN = 50f // min pitch (watch top points down, screen up)
private const val PITCH_THRESHOLD_HAND_DOWN_MAX = 80f // max pitch (watch top points down, screen up)
private const val ROLL_THRESHOLD_HAND_DOWN_MAX_ABS = 30f // max absolute roll for hand down (relatively straight on arm)

// Minimum acceleration to consider it an active gesture, preventing accidental triggers from slight movements
private const val ACTIVE_GESTURE_ACCEL_THRESHOLD = 1.5f // m/s^2

// Time (in milliseconds) to hold a gesture before reverting to neutral if no active movement is detected
private const val GESTURE_HOLD_DURATION_MS = 1000L // 1 second

// --- Azimuth threshold for "wrist faces behind them" ---
// Azimuth (yaw) values in degrees. 0 is North, 90 East, 180 South, 270 West.
// "Behind them" is roughly facing South. The range is symmetrical around 180 degrees.
private const val AZIMUTH_THRESHOLD_BEHIND_MIN = 135f // Minimum azimuth for "behind"
private const val AZIMUTH_THRESHOLD_BEHIND_MAX = 225f // Maximum azimuth for "behind"

// Shared Preferences keys for wrist orientation and speed units
private const val PREF_FILE_NAME = "watch_face_prefs"
private const val PREF_WRIST_ORIENTATION = "wrist_orientation"
private const val PREF_SPEED_UNIT = "speed_unit"

private const val WRIST_LEFT_VALUE = "left"
private const val WRIST_RIGHT_VALUE = "right"
private const val SPEED_UNIT_KMH = "kmh"
private const val SPEED_UNIT_MPH = "mph"


// Enum to define the current display state of the watch
enum class DisplayState {
    NEUTRAL_INFO,
    ARROW_LEFT,
    ARROW_RIGHT,
    RED_FACE
}

/**
 * Main Activity for the Wear OS application.
 * Handles UI display, sensor data processing for gestures, and location updates.
 */
class WearOSMainActivity : AppCompatActivity(),
    SensorEventListener, LocationListener, SharedPreferences.OnSharedPreferenceChangeListener {

    // --- UI Elements ---
    private lateinit var mainLayout: LinearLayout
    private lateinit var timeTextView: TextView
    private lateinit var cardinalDirectionTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var arrowImageView: ImageView
    private lateinit var settingsButton: TextView

    // --- State Variables ---
    private lateinit var calendar: Calendar
    private lateinit var handler: Handler

    private var currentDisplayState: DisplayState = DisplayState.NEUTRAL_INFO
    private var lastGestureTime: Long = 0L

    // --- Wrist Orientation and Speed Unit Preferences ---
    private lateinit var sharedPreferences: SharedPreferences
    private var isRightWrist: Boolean = false // True if watch is worn on right wrist
    private var speedUnit: String = SPEED_UNIT_KMH // Default speed unit

    // --- Sensor Variables ---
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magneticField: Sensor? = null
    private var rotationVector: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var accelerationMagnitude: Float = 0f

    // --- Location Variables ---
    private lateinit var locationManager: LocationManager
    private val locationUpdateCallback = object : Runnable {
        override fun run() {
            requestLocationUpdates()
            handler.postDelayed(this, GPS_UPDATE_INTERVAL_MS)
        }
    }

    // --- UI Update Runnable ---
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            updateUI() // Update time and check gesture timeout
            handler.postDelayed(this, UI_UPDATE_DELAY_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Set the layout for the main activity

        // Initialize UI elements
        mainLayout = findViewById(R.id.main_layout)
        timeTextView = findViewById(R.id.time_text_view)
        cardinalDirectionTextView = findViewById(R.id.cardinal_direction_text_view)
        speedTextView = findViewById(R.id.speed_text_view)
        arrowImageView = findViewById(R.id.arrow_image_view)
        settingsButton = findViewById(R.id.settings_button)

        // Set click listener for the settings button
        settingsButton.setOnClickListener {
            Log.d(TAG, "Settings button tapped, launching settings activity.")
            val settingsIntent = Intent(this, WearOSSettingsActivity::class.java)
            startActivity(settingsIntent)
        }

        // Initialize calendar
        calendar = Calendar.getInstance()
        handler = Handler(Looper.getMainLooper())

        // Initialize Shared Preferences and load initial settings
        sharedPreferences = getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this) // Listen for changes
        loadPreferences() // Load initial preferences

        // Initialize sensor manager and sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Initialize location manager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        updateUI(forceUpdate = true) // Initial UI update
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        handler.post(locationUpdateCallback) // Start requesting location updates
        handler.post(uiUpdateRunnable) // Start UI updates
        loadPreferences() // Reload preferences on resume in case they changed
    }

    override fun onPause() {
        super.onPause()
        unregisterSensors()
        handler.removeCallbacks(locationUpdateCallback)
        handler.removeCallbacks(uiUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this) // Unregister listener
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(this)
        }
    }

    // --- Shared Preferences Listener ---
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // React to preference changes from settings activity
        if (key == PREF_WRIST_ORIENTATION || key == PREF_SPEED_UNIT) {
            loadPreferences() // Reload preferences
            updateUI(forceUpdate = true) // Force UI update to apply changes
            Log.d(TAG, "Preferences reloaded. Right Wrist: $isRightWrist, Speed Unit: $speedUnit")
        }
    }

    private fun loadPreferences() {
        isRightWrist = sharedPreferences.getBoolean(PREF_WRIST_ORIENTATION, false)
        speedUnit = sharedPreferences.getString(PREF_SPEED_UNIT, SPEED_UNIT_KMH) ?: SPEED_UNIT_KMH
    }

    /**
     * Updates the UI elements based on the current display state.
     * @param forceUpdate Forces an update regardless of state changes.
     */
    private fun updateUI(forceUpdate: Boolean = false) {
        val newDisplayState = determineDisplayState()

        // Only update UI if state changes or forced
        if (newDisplayState != currentDisplayState || forceUpdate) {
            currentDisplayState = newDisplayState
            Log.d(TAG, "Display State Changed To: $currentDisplayState")

            // Update time string (always done as it's a live update)
            calendar.timeInMillis = System.currentTimeMillis()
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeTextView.text = timeFormat.format(calendar.time)

            when (currentDisplayState) {
                DisplayState.NEUTRAL_INFO -> {
                    mainLayout.setBackgroundColor(Color.BLACK)
                    timeTextView.visibility = View.VISIBLE
                    cardinalDirectionTextView.visibility = View.VISIBLE
                    speedTextView.visibility = View.VISIBLE
                    settingsButton.visibility = View.VISIBLE
                    arrowImageView.visibility = View.GONE

                    cardinalDirectionTextView.text = currentCardinalDirection
                    speedTextView.text = currentSpeed
                }
                DisplayState.ARROW_LEFT -> {
                    mainLayout.setBackgroundColor(Color.BLACK)
                    timeTextView.visibility = View.GONE
                    cardinalDirectionTextView.visibility = View.GONE
                    speedTextView.visibility = View.GONE
                    settingsButton.visibility = View.GONE
                    arrowImageView.visibility = View.VISIBLE
                    arrowImageView.setImageResource(R.drawable.ic_arrow_left_white) // Use your left arrow drawable
                }
                DisplayState.ARROW_RIGHT -> {
                    mainLayout.setBackgroundColor(Color.BLACK)
                    timeTextView.visibility = View.GONE
                    cardinalDirectionTextView.visibility = View.GONE
                    speedTextView.visibility = View.GONE
                    settingsButton.visibility = View.GONE
                    arrowImageView.visibility = View.VISIBLE
                    arrowImageView.setImageResource(R.drawable.ic_arrow_right_white) // Use your right arrow drawable
                }
                DisplayState.RED_FACE -> {
                    mainLayout.setBackgroundColor(Color.RED)
                    timeTextView.visibility = View.GONE
                    cardinalDirectionTextView.visibility = View.GONE
                    speedTextView.visibility = View.GONE
                    settingsButton.visibility = View.GONE
                    arrowImageView.visibility = View.GONE
                }
            }
        }

        // Revert to neutral if gesture hold duration is exceeded and no active movement
        if (currentDisplayState != DisplayState.NEUTRAL_INFO &&
            accelerationMagnitude < NEUTRAL_ACCEL_THRESHOLD &&
            System.currentTimeMillis() - lastGestureTime > GESTURE_HOLD_DURATION_MS) {
            currentDisplayState = DisplayState.NEUTRAL_INFO
            updateUI(forceUpdate = true) // Force update to switch back
            Log.d(TAG, "Reverting to Neutral (Gesture Timeout)")
        }
    }


    // --- Sensor Handling ---

    private fun registerSensors() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterSensors() {
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

                updateCardinalDirection(azimuthDegrees)

                // Log for debugging sensor values if needed
                // Log.d(TAG, "Pitch: ${String.format("%.1f", pitchDegrees)}, Roll: ${String.format("%.1f", rollDegrees)}, Azimuth: ${String.format("%.1f", azimuthDegrees)}, Accel: ${String.format("%.1f", accelerationMagnitude)}")

                // updateUI() is called periodically by uiUpdateRunnable, so we don't call invalidate here.
                // However, we update gesture time if an active gesture is detected.
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used for this application
    }

    /**
     * Determines the current DisplayState based on sensor data and wrist orientation.
     */
    private fun determineDisplayState(): DisplayState {
        val pitchDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

        val isActiveGesture = accelerationMagnitude > ACTIVE_GESTURE_ACCEL_THRESHOLD
        val isWristFacingBehind = azimuthDegrees >= AZIMUTH_THRESHOLD_BEHIND_MIN &&
                azimuthDegrees <= AZIMUTH_THRESHOLD_BEHIND_MAX

        // Determine gesture based on wrist orientation
        if (!isRightWrist) { // Watch on LEFT wrist
            // ARROW_LEFT: Left arm straight out to side (watch rolled significantly outwards)
            if (isActiveGesture && isWristFacingBehind &&
                rollDegrees > ROLL_THRESHOLD_STRAIGHT_SIDE_MIN_ABS &&
                abs(pitchDegrees) < PITCH_THRESHOLD_STRAIGHT_SIDE_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.ARROW_LEFT
            }
            // ARROW_RIGHT: Left arm out at shoulder, elbow 90° up (watch pitched forward/down)
            else if (isActiveGesture && isWristFacingBehind &&
                pitchDegrees < PITCH_THRESHOLD_ELBOW_UP_MAX &&
                pitchDegrees > PITCH_THRESHOLD_ELBOW_UP_MIN &&
                abs(rollDegrees) < ROLL_THRESHOLD_ELBOW_UP_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.ARROW_RIGHT
            }
            // RED_FACE: Left arm to side, hand down (watch vertical, pitched upwards)
            else if (!isActiveGesture && isWristFacingBehind && // Gesture for red face is assumed to be still
                pitchDegrees > PITCH_THRESHOLD_HAND_DOWN_MIN &&
                pitchDegrees < PITCH_THRESHOLD_HAND_DOWN_MAX &&
                abs(rollDegrees) < ROLL_THRESHOLD_HAND_DOWN_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.RED_FACE
            }
        } else { // Watch on RIGHT wrist
            // ARROW_RIGHT: Right arm straight out to side (watch rolled significantly inwards)
            if (isActiveGesture && isWristFacingBehind &&
                rollDegrees < -ROLL_THRESHOLD_STRAIGHT_SIDE_MIN_ABS && // Negative roll for right wrist
                abs(pitchDegrees) < PITCH_THRESHOLD_STRAIGHT_SIDE_MAX_ABS) {
                lastGestureTime = System.currentTimeMillis()
                return DisplayState.ARROW_RIGHT
            }
        }

        // If no specific gesture is detected or azimuth is not "behind them" for gesture states
        // And the arm is relatively still (to prevent flickers from minor movements)
        if (accelerationMagnitude < NEUTRAL_ACCEL_THRESHOLD &&
            abs(pitchDegrees) < PITCH_THRESHOLD_NEUTRAL_ABS &&
            abs(rollDegrees) < ROLL_THRESHOLD_NEUTRAL_ABS) {
            return DisplayState.NEUTRAL_INFO
        }

        // Maintain current state if no specific new gesture is triggered
        // and it's not time to revert to neutral from a previous gesture.
        return currentDisplayState
    }

    /**
     * Updates the cardinal direction string based on the device's azimuth.
     * @param azimuthDegrees The azimuth angle in degrees (0-360).
     */
    private fun updateCardinalDirection(azimuthDegrees: Float) {
        val direction = (azimuthDegrees + 360) % 360 // Ensure positive angle
        cardinalDirectionTextView.text = when {
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

    // --- Location Handling ---

    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted. Cannot get speed.")
            speedTextView.text = "GPS Off"
            return
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "GPS provider is not enabled.")
            speedTextView.text = "GPS Disabled"
            return
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // minTime: 1 second
                1f,   // minDistance: 1 meter
                this // LocationListener
            )
        } catch (ex: SecurityException) {
            Log.e(TAG, "Location permission denied: ${ex.message}")
            speedTextView.text = "GPS Error"
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            val speedMps = location.speed
            val speedText = if (speedUnit == SPEED_UNIT_MPH) {
                val speedMph = (speedMps * 2.23694).roundToInt()
                "$speedMph mph"
            } else {
                val speedKmh = (speedMps * 3.6).roundToInt()
                "$speedKmh km/h"
            }
            speedTextView.text = speedText
        } else {
            speedTextView.text = "0 ${speedUnit}"
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
        speedTextView.text = "GPS Disabled"
    }
}

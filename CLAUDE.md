# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CycleSignal is a Wear OS application for cyclists that uses device sensors to detect arm gestures and display appropriate cycling signals. The app shows different UI states based on wrist orientation and movement patterns detected through accelerometer, magnetometer, and rotation vector sensors.

## Architecture

The project contains two implementations:
1. **WearOSMainActivity.kt** - Traditional Android View-based implementation with comprehensive sensor handling
2. **MainActivity.kt** - Jetpack Compose-based template (minimal implementation)

### Core Components

**WearOSMainActivity** (main implementation):
- **Sensor Management**: Accelerometer, magnetometer, rotation vector sensors for gesture detection
- **Location Services**: GPS for speed tracking with unit conversion (km/h ↔ mph)
- **Display States**: NEUTRAL_INFO, ARROW_LEFT, ARROW_RIGHT, RED_FACE
- **Gesture Detection**: Complex logic for detecting cycling signal gestures based on pitch, roll, azimuth thresholds
- **Settings**: Wrist orientation (left/right) and speed unit preferences via SharedPreferences

**Key Thresholds** (in WearOSMainActivity.kt lines 47-92):
- Gesture detection based on pitch/roll angles and acceleration magnitude
- Azimuth-based "behind them" detection for signal validity
- Configurable timeouts for gesture hold duration

## Commands

### Build & Development
```bash
./gradlew build                    # Build the project
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK
./gradlew installDebug            # Install debug APK to connected device
```

### Testing
```bash
./gradlew test                    # Run unit tests
./gradlew connectedAndroidTest    # Run instrumented tests
```

### Linting & Code Quality
```bash
./gradlew lint                    # Run Android lint
./gradlew ktlintCheck            # Check Kotlin code style (if configured)
```

## Key Configuration Files

- **build.gradle.kts** (root): Top-level build configuration
- **app/build.gradle**: App module configuration with Wear OS specific dependencies
- **gradle/libs.versions.toml**: Version catalog for dependencies
- **app/src/main/AndroidManifest.xml**: Wear OS permissions and activity declarations

## Development Notes

### Sensor Values & Gesture Logic
The gesture detection in `WearOSMainActivity.kt` uses specific threshold values that may need adjustment based on real-world testing. Key areas:
- Lines 47-92: Threshold constants for different gestures
- Lines 361-416: `determineDisplayState()` - core gesture detection logic
- Wrist orientation (left vs right) significantly affects gesture interpretation

### Location & Permissions
The app requires:
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` for GPS speed
- `BODY_SENSORS` for accelerometer/sensor access
- `FOREGROUND_SERVICE` for background location updates

### Package Structure
- Main package: `com.example.cyclesignal` (WearOSMainActivity)
- Compose package: `com.example.cyclesignal.presentation` (MainActivity)
- All files now use consistent `com.example.cyclesignal` package naming

## Dependencies

Primary Wear OS dependencies:
- `androidx.wear:wear:1.2.0` - Core Wear OS UI components
- `com.google.android.gms:play-services-location:21.0.1` - Location services
- `com.google.android.wearable:wearable:2.9.0` - Wearable APIs
- Jetpack Compose for Wear OS (in compose implementation)

## Target SDK & Compatibility
- **compileSdk**: 34
- **minSdk**: 28 (Wear OS minimum)
- **targetSdk**: 34
- Java/Kotlin compatibility: JVM target 1.8
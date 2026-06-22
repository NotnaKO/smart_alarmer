# Shake Puzzle and Ringtone Fallback Design Spec

This specification describes the addition of a new "Shake to Dismiss" puzzle type, randomizing the Math puzzle difficulty between MEDIUM and HARD, and implementing a robust audio fallback routing chain in `AlarmService` to resolve silent alarms on real physical devices.

## Proposed Changes

### 1. Math Puzzle Randomization

#### [MODIFY] [AlarmDismissScreen.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt)
* Update `MathPuzzleView` to randomize puzzle generation:
```kotlin
val puzzle = remember {
    val difficulty = listOf(Difficulty.MEDIUM, Difficulty.HARD).random()
    mathProvider.generate(difficulty)
}
```

---

### 2. "Shake to Dismiss" Puzzle

#### [MODIFY] [AlarmDismissScreen.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt)
* Add `SHAKE` to the `PuzzleType` enum:
```kotlin
enum class PuzzleType { MATH, TYPING, MEMORY, SHAKE }
```
* Implement `ShakePuzzleView(onComplete: () -> Unit, shakeProvider: ShakeSensorProvider)`:
  * Uses `SensorManager` and `Sensor.TYPE_ACCELEROMETER` (in the `AndroidShakeSensorProvider` implementation) to detect shaking.
  * Tracks shake counts (requires 30 shakes).
  * For automated testing (instrumented UI tests), uses a mock `ShakeSensorProvider` that invokes the sensor callback directly to simulate shake events.
  * Ensures sensor listener is registered in `DisposableEffect` and cleaned up on composition dispose.

#### [MODIFY] [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt)
* Add `"SHAKE"` to the selectable list of puzzles in `AlarmEditSheet` to allow the user to select the shake puzzle type.

#### [MODIFY] [AlarmDismissActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt)
* Update `AlarmDismissScreen` call to pass `isPreview = isPreview`.

---

### 3. Ringtone Fallback & Routing

#### [MODIFY] [AlarmService.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmService.kt)
* Update `AudioAttributes` usage to route audio to `USAGE_ALARM` with `CONTENT_TYPE_SONIFICATION` (guaranteeing routing to the physical device's Alarm stream).
* Implement a robust fallback playback loop trying:
  1. Default Alarm Sound (`RingtoneManager.TYPE_ALARM`)
  2. Default Ringtone (`RingtoneManager.TYPE_RINGTONE`)
  3. Default Notification Sound (`RingtoneManager.TYPE_NOTIFICATION`)
  4. Final Fallback: System `ToneGenerator` playing CDMA call guard tone if all system audio streams fail to initialize.

---

## Verification Plan

### Automated Tests
* Update `AlarmDismissScreenTest.kt` to include tests for `ShakePuzzleView` (testing decrementing shake counts with the "Simulate Shake" button).
* Run `./gradlew test` to ensure all JVM unit tests pass.
* Run `./gradlew connectedAndroidTest` to verify that UI and database integrations remain correct.

### Manual Verification
* Run the app using `./run_app.sh`.
* Test-run a newly configured alarm with "SHAKE" enabled via the "Test" button.
* Confirm that:
  1. Clicking "Simulate Shake" works and dismisses the alarm in preview mode.
  2. The actual ringtone works on the physical device.

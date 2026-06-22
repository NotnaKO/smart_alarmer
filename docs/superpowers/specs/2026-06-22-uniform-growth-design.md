# Uniform Volume Growth (Crescendo) to Avoid Heart-Breaking Rings

Design specification for adding a gradual volume ramp-up (crescendo) to active alarms. This feature allows users to wake up gently rather than being startled by a sudden, maximum-volume alarm.

## Goals
- Provide a configurable "Gradual Volume" option per alarm in the alarm editor UI.
- Implement linear volume growth in the foreground service starting from `1` (lowest non-silent setting) up to the device's `maxVolume` over a fixed 30-second duration.
- Ensure the crescendo still enforces the secure volume locking concept (i.e., locking the volume at the current target level calculated for that second so users cannot bypass the alarm by lowering the volume).

---

## Architecture & Database Changes

### 1. Database Entity Addition
In `Alarm.kt`, add a new field to the database representation:
```kotlin
val isGradualVolume: Boolean = true
```
We default this to `true` to ensure new alarms automatically wake the user gently.

### 2. Room Database Version Upgrade & Migration
- Upgrade database version to `2` in `AlarmDatabase.kt`.
- Write a migration `MIGRATION_1_2` to add the `isGradualVolume` column with default value `1` (true in SQLite integers).
- Add the migration to the database builder in `AlarmDatabase.kt`.

### 3. Alarm Scheduling Data Pipeline
- `AlarmScheduler.kt`: Add the `isGradualVolume` property as a boolean extra in the `AlarmReceiver` PendingIntent.
- `AlarmReceiver.kt`: Extract the boolean extra and forward it within the intent to start `AlarmService`.

---

## User Interface changes

### 1. Alarm Edit Sheet
- Add a new `Row` in the `AlarmEditSheet` bottom sheet (under `MainActivity.kt`).
- The row will contain:
  - Text: "Gradual Volume" with a small subtitle "Volume ramps up over 30 seconds"
  - Switch: Bound to a local state variable `isGradualVolume` initialized with `alarm?.isGradualVolume ?: true`.
- When clicking "Save", include the state of `isGradualVolume` to save via `MainViewModel`.

### 2. MainViewModel
- Update the signature of `saveAlarm()` to accept the `isGradualVolume` parameter and copy/construct the `Alarm` object with it.

---

## Alarm Service Volume Ramping Logic

In `AlarmService.kt`:
1. Extract `isGradualVolume` from the starting intent (defaulting to `true`).
2. Implement volume ramping inside the `volumeTimer` execution block:
   - When service is started, save the start timestamp: `val startTime = System.currentTimeMillis()`.
   - On each tick of the 1-second timer loop:
     - If `isGradualVolume` is true and `isPreview` is false:
       - Calculate `elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L`.
       - Ramping duration is fixed at `30L` seconds.
       - Retrieve the current stream max volume: `val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)`.
       - Compute target volume:
         ```kotlin
         val targetVolume = if (elapsedSeconds < 30) {
             1 + (elapsedSeconds * (maxVolume - 1) / 30).toInt()
         } else {
             maxVolume
         }
         ```
       - Force the system stream volume to the computed `targetVolume`:
         ```kotlin
         audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
         ```
     - If `isGradualVolume` is false and `isPreview` is false:
       - Lock the volume to maximum immediately as before:
         ```kotlin
         audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
         ```

---

## Verification Plan

### Automated Unit Tests
- Create unit tests verifying that:
  - `Alarm` defaults `isGradualVolume` to `true`.
  - Database schema changes load and save `isGradualVolume` correctly.

### Manual Verification
- Launch a test alarm (using preview or live scheduling with `isGradualVolume = true`).
- Verify that:
  - The alarm starts playing quietly (at volume level 1).
  - The volume increases noticeably step-by-step each second.
  - The volume reaches maximum strength after 30 seconds.
  - If you attempt to turn the volume down during the ramp-up, the service immediately forces it back to the current crescendo target volume.

# Smart Alarmer Cleanups & Scheduling Plan

This plan details the cleanup of unused template boilerplate code, the implementation of automatic alarm rescheduling on device reboot, and the calculation of precise trigger times based on the active days of the week.

## User Review Required

> [!IMPORTANT]
> The template cleanup requires deleting 7 unused navigation and ViewModel files. This will make the codebase cleaner and more maintainable. We will verify compile and test success immediately after deletion.

## Open Questions

> [!IMPORTANT]
> **Day-of-Week Mapping Discrepancy**
>
> In [Alarm.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/data/Alarm.kt), the `daysOfWeek` property has the comment `// CSV e.g., "1,2,3,4,5"`. In [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt), it is currently hardcoded as `"1,2,3,4,5"`.
>
> There are two main ways to interpret this:
> - **Option A (Standard Calendar API):** `1` = Sunday, `2` = Monday, ..., `7` = Saturday. Under this mapping, `"1,2,3,4,5"` schedules for Sunday-Thursday.
> - **Option B (ISO-8601 / Monday-first):** `1` = Monday, `2` = Tuesday, ..., `7` = Sunday. Under this mapping, `"1,2,3,4,5"` schedules for Monday-Friday.
>
> **Proposed Approach:** We will implement Option B (ISO-8601 / Monday-first) because it aligns with typical alarm app conventions where the week starts on Monday, mapping database values `1-7` to `Calendar` day constants (`2` for Monday, ..., `1` for Sunday).
> Please let us know if you prefer Option A (direct `Calendar` integers) instead.

---

## Proposed Changes

### Cleanups

#### [DELETE] [Navigation.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/Navigation.kt)
* Delete the unused navigation graph boilerplate.

#### [DELETE] [NavigationKeys.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/NavigationKeys.kt)
* Delete unused navigation key definitions.

#### [DELETE] [DataRepository.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/data/DataRepository.kt)
* Delete unused boilerplate repository interface and implementation.

#### [DELETE] [MainScreen.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/main/MainScreen.kt)
* Delete unused template main screen layout.

#### [DELETE] [MainScreenViewModel.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/main/MainScreenViewModel.kt)
* Delete unused template ViewModel.

#### [DELETE] [MainScreenTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/ui/main/MainScreenTest.kt)
* Delete unused instrumented tests for the template main screen.

#### [DELETE] [MainScreenViewModelTest.kt](file:///home/notnako/smart_alarmer/app/src/test/java/com/example/smartalarmer/ui/main/MainScreenViewModelTest.kt)
* Delete unused unit tests for the template ViewModel.

---

### Boot Rescheduling

#### [MODIFY] [AlarmDao.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/data/AlarmDao.kt)
* Add a suspending query method `getEnabledAlarms()` to fetch all currently active alarms in the database:
```kotlin
@Query("SELECT * FROM alarms WHERE isEnabled = 1")
suspend fun getEnabledAlarms(): List<Alarm>
```

#### [NEW] [BootReceiver.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/BootReceiver.kt)
* Implement a `BroadcastReceiver` listening for `ACTION_BOOT_COMPLETED` that uses a coroutine scope with `goAsync()` to load active alarms and reschedule them using `AlarmScheduler`.

#### [MODIFY] [AndroidManifest.xml](file:///home/notnako/smart_alarmer/app/src/main/AndroidManifest.xml)
* Request the `RECEIVE_BOOT_COMPLETED` permission.
* Register `BootReceiver` to receive the boot completed action.

---

### Precise Day-of-Week Scheduling

#### [MODIFY] [AlarmScheduler.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmScheduler.kt)
* Update `AlarmScheduler.schedule()` to parse active days of the week and scan up to 7 days ahead (using `Calendar` offsets) to find the nearest matching day that occurs in the future.
* Map ISO-8601 day integers (`1` = Monday, ..., `7` = Sunday) from the database to standard Java `Calendar` day constants:
  - `1` (Mon) -> `Calendar.MONDAY` (2)
  - `2` (Tue) -> `Calendar.TUESDAY` (3)
  - `3` (Wed) -> `Calendar.WEDNESDAY` (4)
  - `4` (Thu) -> `Calendar.THURSDAY` (5)
  - `5` (Fri) -> `Calendar.FRIDAY` (6)
  - `6` (Sat) -> `Calendar.SATURDAY` (7)
  - `7` (Sun) -> `Calendar.SUNDAY` (1)

---

## Verification Plan

### Automated Tests
* Run `./gradlew compileDebugKotlin` to verify the project builds after cleanups.
* Run `./gradlew test` to verify all local unit tests (puzzles, engines, etc.) still pass.
* Run `./gradlew connectedAndroidTest` to verify instrumented database operations still function.

### Emulator Verification
We will verify scheduling behavior and boot rescheduling on the active Android Emulator (`emulator-5554`) through the following step-by-step process:

1. **Verify App Deployment & UI Launch:**
   * Build and install the APK on the emulator:
     ```bash
     ./gradlew installDebug
     ```
   * Launch the app's main activity:
     ```bash
     adb shell am start -n com.example.smartalarmer/.MainActivity
     ```
   * Confirm the UI loads successfully and lists no alarms initially.

2. **Verify Alarm Scheduling and DB Sync:**
   * Click the Floating Action Button (FAB) in the UI to open the Time Picker.
   * Schedule an alarm for **3 minutes** in the future.
   * Query the Android system `AlarmManager` state via ADB to verify the alarm is scheduled:
     ```bash
     adb shell dumpsys alarm | grep com.example.smartalarmer
     ```
   * Confirm that the alarm is listed in the output with the correct trigger time.

3. **Verify Boot Rescheduling (BootReceiver):**
   * While the alarm is active, simulate a device reboot broadcast to target the app:
     ```bash
     adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.example.smartalarmer
     ```
   * Read the device logs (logcat) to verify the `BootReceiver` intercepting the event, querying the active alarms, and rescheduling:
     ```bash
     adb logcat -d | grep -E "BootReceiver|AlarmScheduler"
     ```
   * Re-verify scheduled alarms via `adb shell dumpsys alarm` to ensure the alarm is still scheduled.

4. **Verify Exact Triggering and Puzzle UI Flow:**
   * Wait for the scheduled alarm time to arrive.
   * Verify the full-screen dismiss overlay activity (`AlarmDismissActivity`) opens on the emulator.
   * Solve the 3 puzzles sequentially (Math, Memory, Typing) and confirm the alarm shuts off when the tasks are completed.

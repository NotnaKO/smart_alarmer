# Smart Alarmer

A native Android alarm clock that forces you to wake up by completing cognitive puzzles before the alarm can be silenced. No snooze button, no mercy.

## Overview

Smart Alarmer uses Android's `AlarmManager` to schedule exact alarms that trigger a foreground service playing audio at maximum volume. The only way to stop the noise is to solve a configurable sequence of puzzles (math, typing, and memory pattern recognition). The alarm survives device reboots and automatically reschedules recurring alarms after they fire.

### Key Features

- **Three puzzle types**: Math equations, typing challenges, and memory pattern games
- **Recurring alarms**: ISO-8601 day-of-week scheduling (Monday = 1 through Sunday = 7)
- **Boot persistence**: Alarms reschedule automatically when the device restarts
- **Volume lock**: The alarm stream is forced to maximum every second while active
- **Full-screen overlay**: The puzzle screen appears over the lock screen with back-button disabled
- **Room database**: Persistent alarm storage with reactive Flow-based UI updates

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                         │
│          (Compose UI: alarm list, FAB, TimePicker)          │
└────────────────────────┬────────────────────────────────────┘
                         │ insert/update/delete
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    AlarmDatabase (Room)                      │
│  ┌──────────┐    ┌───────────┐                              │
│  │  Alarm    │    │  AlarmDao  │ ← getEnabledAlarms()       │
│  │  Entity   │    │           │ ← getAlarmById(id)          │
│  └──────────┘    └───────────┘                              │
└────────────────────────┬────────────────────────────────────┘
                         │
          schedule()     │     cancel()
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     AlarmScheduler                           │
│  calculateNextTriggerTime(alarm, now) → Calendar             │
│  schedule(context, alarm) → AlarmManager.setExactAndAllow...│
│  cancel(context, alarm)                                      │
└────────────────────────┬────────────────────────────────────┘
                         │ RTC_WAKEUP broadcast
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     AlarmReceiver                            │
│  → starts AlarmService (foreground)                          │
│  → reschedules recurring alarms / disables one-time alarms  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      AlarmService                            │
│  → foreground notification with full-screen intent           │
│  → starts MediaPlayer (alarm tone, looping)                  │
│  → locks volume to max every 1 second                        │
│  → launches AlarmDismissActivity directly                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  AlarmDismissActivity                         │
│  showWhenLocked + turnScreenOn + back-button disabled        │
│  → hosts AlarmDismissScreen (Compose)                        │
│  → onDismissComplete → stopService + finish()                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   AlarmDismissScreen                          │
│  Puzzle sequence: shuffled, N-of-M from config               │
│  ┌──────────────┬──────────────┬──────────────┐              │
│  │ MathPuzzleView│TypingPuzzle │MemoryPuzzle  │              │
│  │              │  View       │  View         │              │
│  └──────┬───────┴──────┬──────┴──────┬────────┘              │
│         │              │             │                        │
│    MathEngine    TypingEngine   MemoryEngine                 │
│   (MathPuzzle    (TypingPuzzle  (MemoryPuzzle                │
│    Provider)      Provider)      Provider)                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     BootReceiver                              │
│  ACTION_BOOT_COMPLETED → queries enabled alarms from DB      │
│  → reschedules each via AlarmScheduler.schedule()            │
└─────────────────────────────────────────────────────────────┘
```

---

## Source Files

### Core Alarm Flow

| File | Purpose |
|------|---------|
| `AlarmScheduler.kt` | Calculates the next trigger time and registers exact alarms via `AlarmManager`. The core calculation logic is in `calculateNextTriggerTime()`, which is pure and unit-testable. |
| `AlarmReceiver.kt` | `BroadcastReceiver` triggered by `AlarmManager`. Starts the foreground `AlarmService` and automatically reschedules recurring alarms for the next active day. Disables one-time alarms. |
| `AlarmService.kt` | Foreground service that plays the alarm tone at max volume, locks the volume stream, shows a notification with full-screen intent, and launches the dismiss overlay. |
| `AlarmDismissActivity.kt` | Full-screen activity that appears over the lock screen. Hosts the Compose puzzle UI. Disables the back button. On completion, stops `AlarmService` and finishes itself. |
| `AlarmDismissScreen.kt` | Compose screen that orchestrates puzzle progression (Task 1 of N → Task N of N). Delegates to individual puzzle views. |
| `BootReceiver.kt` | Listens for `ACTION_BOOT_COMPLETED` and reschedules all enabled alarms from the Room database. Uses `goAsync()` for safe coroutine work in a receiver. |

### Data Layer

| File | Purpose |
|------|---------|
| `data/Alarm.kt` | Room `@Entity`. Fields: `id`, `hour`, `minute`, `daysOfWeek` (CSV of ISO-8601 day numbers), `isEnabled`, `puzzlesList` (CSV of puzzle types), `puzzleCount`. |
| `data/AlarmDao.kt` | Room DAO with `getAllAlarms()` (Flow), `getEnabledAlarms()`, `getAlarmById()`, `insertAlarm()`, `updateAlarm()`, `deleteAlarm()`. |
| `data/AlarmDatabase.kt` | Singleton Room database with thread-safe `getDatabase()` builder. |

### Puzzle Engines

| File | Purpose |
|------|---------|
| `puzzle/PuzzleProviders.kt` | Interfaces (`MathPuzzleProvider`, `TypingPuzzleProvider`, `MemoryPuzzleProvider`) that enable dependency injection for testing. |
| `puzzle/MathEngine.kt` | Generates arithmetic puzzles at Easy (add/subtract), Medium (multiply + add), and Hard (solve for x) difficulty levels. Implements `MathPuzzleProvider`. |
| `puzzle/TypingEngine.kt` | Provides random motivational quotes and case-sensitive matching. Implements `TypingPuzzleProvider`. |
| `puzzle/MemoryEngine.kt` | Generates random sequences of grid indices (0–8) and validates step-by-step user input. Implements `MemoryPuzzleProvider`. |

### UI

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Settings screen: displays alarm list via `LazyColumn`, FAB opens `TimePickerDialog`, toggle/delete controls per alarm. |
| `theme/` | Material 3 dark theme configuration. |

---

## Day-of-Week Encoding

Alarm days are stored as a comma-separated string of ISO-8601 integers:

| Value | Day |
|-------|-----------|
| 1 | Monday |
| 2 | Tuesday |
| 3 | Wednesday |
| 4 | Thursday |
| 5 | Friday |
| 6 | Saturday |
| 7 | Sunday |

Example: `"1,2,3,4,5"` = weekdays only. An empty string means a one-time alarm.

`AlarmScheduler.calculateNextTriggerTime()` maps these values to Java `Calendar.MONDAY` … `Calendar.SUNDAY` constants and iterates up to 7 days ahead to find the next active future time.

---

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE` | Run `AlarmService` as a foreground service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required `foregroundServiceType` for audio on Android 14+ |
| `USE_FULL_SCREEN_INTENT` | Launch dismiss activity from the notification |
| `SCHEDULE_EXACT_ALARM` | Schedule exact alarms (user-grantable on Android 12+) |
| `USE_EXACT_ALARM` | Unconditional exact alarm permission for clock apps (Android 13+) |
| `WAKE_LOCK` | Keep CPU alive during alarm processing |
| `POST_NOTIFICATIONS` | Show foreground service notification |
| `RECEIVE_BOOT_COMPLETED` | Trigger `BootReceiver` after device restart |

> **Note on Android 14+ (SDK 34+):** The `SCHEDULE_EXACT_ALARM` permission is revoked by default. On physical devices users must enable "Alarms & reminders" in Special App Access settings. On emulators, run:
> ```bash
> adb shell appops set com.example.smartalarmer SCHEDULE_EXACT_ALARM allow
> ```

---

## Build & Run

```bash
# Compile
./gradlew compileDebugKotlin

# Run unit tests (JVM)
./gradlew testDebugUnitTest

# Run instrumented tests (requires emulator or device)
./gradlew connectedAndroidTest

# Build and install debug APK
./gradlew installDebug
```

**Requirements:** JDK 17, Android SDK 36, Gradle 9.1

---

## Testing Strategy

### Test Pyramid

```
        ┌───────────────────────┐
        │  Emulator E2E Tests   │  ← Manual via ADB
        │  (integration)        │
        ├───────────────────────┤
        │  Instrumented Tests   │  ← connectedAndroidTest
        │  (Room, on-device)    │
        ├───────────────────────┤
        │    JVM Unit Tests     │  ← testDebugUnitTest
        │  (engines, scheduler) │
        └───────────────────────┘
```

### 1. JVM Unit Tests (`app/src/test/`)

These run on the host JVM with no Android framework required.

| Test Class | What It Covers |
|------------|---------------|
| `MathEngineTest` | Verifies puzzle generation at all three difficulty levels: correct difficulty field, operator presence, and answer range. |
| `TypingEngineTest` | Tests exact string matching (including whitespace trimming) and non-empty quote generation. |
| `MemoryEngineTest` | Tests sequence generation (correct length, valid indices 0–8) and step-by-step verification (correct prefix, wrong prefix, overflow). |
| `AlarmSchedulerTest` | Tests `calculateNextTriggerTime()` against a mock `Calendar` for 6 scenarios: one-time future, one-time past, recurring same-day future, recurring same-day past (skip to next active day), recurring different day, and weekly rollover when the only active day has passed. |

The `AlarmSchedulerTest` was made possible by extracting the date calculation logic from `AlarmScheduler.schedule()` into a pure function `calculateNextTriggerTime(alarm, now)` that accepts an injectable `Calendar` for the current time.

### 2. Instrumented Tests (`app/src/androidTest/`)

These run on a real device or emulator and require the Android runtime.

| Test Class | What It Covers |
|------------|---------------|
| `AlarmDatabaseTest` | Creates an in-memory Room database, inserts alarms, reads them back via `getAllAlarms()` Flow, and filters enabled-only alarms via `getEnabledAlarms()`. Validates CRUD operations and query correctness. |

### 3. Emulator End-to-End Verification

Full integration testing was performed manually on an Android 16 emulator (`emulator-5554`). The process is documented below because it demonstrates several non-obvious platform behaviors.

#### Setup
```bash
# Grant exact alarm permission (Android 14+)
adb shell appops set com.example.smartalarmer SCHEDULE_EXACT_ALARM allow

# Install debug build
./gradlew installDebug
```

#### Alarm Scheduling Verification
1. Created an alarm via the FAB `TimePickerDialog` in `MainActivity`.
2. Verified it was persisted in Room by pulling the SQLite database files:
   ```bash
   # Must pull ALL three WAL-mode files for valid data
   adb shell "run-as com.example.smartalarmer cat databases/alarm_database" > /tmp/db
   adb shell "run-as com.example.smartalarmer cat databases/alarm_database-shm" > /tmp/db-shm
   adb shell "run-as com.example.smartalarmer cat databases/alarm_database-wal" > /tmp/db-wal
   python3 -c "import sqlite3; conn=sqlite3.connect('/tmp/db'); print(conn.execute('select * from alarms').fetchall())"
   ```
3. Confirmed the alarm was registered in Android's alarm subsystem:
   ```bash
   adb shell dumpsys alarm | grep com.example.smartalarmer
   ```

#### Alarm Trigger & Puzzle Dismiss Flow
1. Waited for the alarm to fire at the scheduled time.
2. Verified via logcat that the correct puzzle sequence was generated:
   ```bash
   adb logcat -d | grep TEST_DEBUG
   # Output: Puzzles: TYPING, MATH, MEMORY
   # Output: Typing Quote: The early bird gets the worm.
   ```
3. Solved each puzzle using ADB shell input commands:
   - **Typing puzzle**: Used `adb shell input text` to type the target quote character-by-character, then tapped Submit.
   - **Math puzzle**: Read the equation from `uiautomator dump`, computed the answer, tapped the on-screen numeric keyboard buttons by coordinate, then tapped ✔.
   - **Memory puzzle**: Read the flashed sequence from logcat (`TEST_DEBUG: Memory Sequence: 5, 2, 4, 4`), then tapped the corresponding grid buttons by coordinate after the pattern display finished.
4. Confirmed that after solving all 3 puzzles:
   - `AlarmDismissActivity` finished.
   - `AlarmService` stopped (verified via `adb shell dumpsys activity services`).
   - Audio playback ceased.
   - The app returned to `MainActivity`.

#### Coordinate Discovery via UI Automator
Button coordinates for ADB tap commands were obtained by:
```bash
adb shell uiautomator dump /data/local/tmp/uidump.xml
adb pull /data/local/tmp/uidump.xml /tmp/uidump.xml
# Parse to extract text labels and bounding rectangles
python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('/tmp/uidump.xml')
for elem in tree.getroot().iter():
    text = elem.get('text', '')
    bounds = elem.get('bounds', '')
    if text: print(f'{text:15} {bounds}')
"
```

#### Soft Keyboard Interaction Note
When the soft keyboard is open (during the typing puzzle), Compose `adjustResize` pushes the dialog upward, changing button y-coordinates. The workaround is to dismiss the keyboard first:
```bash
adb shell input keyevent KEYCODE_BACK
```

#### Boot Rescheduling
Sending `ACTION_BOOT_COMPLETED` via `adb shell am broadcast` is blocked by Android security. Boot rescheduling was verified by:
1. Inspecting `BootReceiver.kt` code logic (queries `getEnabledAlarms()` and calls `AlarmScheduler.schedule()` for each).
2. Confirming compilation and that the receiver is correctly registered in `AndroidManifest.xml` with the `BOOT_COMPLETED` intent filter.
3. Verifying the `AlarmSchedulerTest` unit tests cover the same `calculateNextTriggerTime()` logic that `BootReceiver` invokes.

---

## Implementation History

Development proceeded incrementally, with each feature committed and verified before moving to the next:

| Commit | Description |
|--------|-------------|
| `c955390` | Implement `TypingEngine` and unit tests |
| `d3f0fef` | Implement `MemoryEngine` and unit tests |
| `943bd4d` | Implement Room database persistence and in-memory test |
| `84b8a53` | Implement `AlarmReceiver` and `AlarmScheduler` |
| `361beed` | Implement `AlarmService` and `AlarmDismissActivity` |
| `7c4b5ce` | Implement `AlarmDismissScreen` and puzzle Compose views |
| `a420bc3` | Implement `MainActivity` with alarm configuration controls |
| `418aee6` | Custom alarm list and FAB `TimePickerDialog` with Material Icons |
| `3b8d350` | Update `AlarmDao.insertAlarm()` to return row ID |
| `5be85d3` | End-to-end multiple alarm scheduling with time picker UI and emulator verification |
| `c8a04f9` | **Cleanup:** delete unused template boilerplate (`Navigation.kt`, `DataRepository.kt`, `MainScreen.kt`, `MainScreenViewModel.kt`, etc.) |
| `151b9fe` | Implement `BootReceiver` to reschedule active alarms on device boot |
| `d79cc28` | Precise day-of-week scheduling calculation (ISO-8601 mapping) |
| `2e379f1` | Extract `calculateNextTriggerTime()`, add scheduling unit tests, add auto-rescheduling in `AlarmReceiver` |

### Puzzle Provider Interfaces (Latest)

The most recent user change introduced dependency injection interfaces for the puzzle engines:

```kotlin
// puzzle/PuzzleProviders.kt
interface MathPuzzleProvider {
    fun generate(difficulty: Difficulty): MathPuzzle
}

interface TypingPuzzleProvider {
    fun getRandomQuote(): String
    fun isMatch(target: String, input: String): Boolean
}

interface MemoryPuzzleProvider {
    fun generateSequence(length: Int): List<Int>
    fun verifyStep(sequence: List<Int>, userInputs: List<Int>): Boolean
}
```

Each engine object (`MathEngine`, `TypingEngine`, `MemoryEngine`) now implements its respective interface, and all Compose puzzle views accept providers as optional parameters with production defaults. This enables:
- **Deterministic UI testing**: Inject fake providers that return known puzzles.
- **Compose Preview support**: Supply lightweight stubs without random generation.
- **Future extensibility**: Swap puzzle logic without touching UI code.

---

## Known Limitations & Gotchas

1. **`SCHEDULE_EXACT_ALARM` on Android 14+**: The permission is revoked by default on API 34+. The app declares `USE_EXACT_ALARM` as a fallback, but on some OEM ROMs users may still need to manually grant "Alarms & reminders" access.

2. **Room WAL mode**: When pulling the database for inspection, you must copy `alarm_database`, `alarm_database-shm`, and `alarm_database-wal` together. Pulling only the main file produces an apparently-empty database.

3. **One alarm per ID**: `PendingIntent` request codes use `alarm.id`, so each alarm can only have one pending trigger at a time. This is intentional for non-overlapping recurring alarms.

4. **No snooze**: By design. The only escape is solving all configured puzzles.

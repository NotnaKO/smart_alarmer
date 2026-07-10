# Smart Alarmer

A native Android alarm clock that forces you to wake up by completing cognitive puzzles before the alarm can be silenced. No snooze button, no mercy.

## Overview

Smart Alarmer uses Android's `AlarmManager` to schedule exact alarms that trigger a foreground service playing audio at maximum volume. The only way to stop the noise is to solve a configurable sequence of puzzles (math, typing, and memory pattern recognition). The alarm survives device reboots and automatically reschedules recurring alarms after they fire.

### Key Features

- **Four puzzle types**: Math equations, typing challenges, memory pattern games, and physical shake challenges with a sensor-safe fallback.
- **Customizable Alarm Configuration**: A slide-up `ModalBottomSheet` lets you configure specific repeat weekdays, choose which puzzle types to show, and set the puzzle count.
- **Modern Glassmorphic Dark Theme**: Premium styling featuring semi-transparent overlays, glowing accents, and smooth feedback animations.
- **Safe Preview/Test Mode**: Test alarm configurations directly from the settings list with a single click. The test mode runs in a non-disruptive activity context (no loud sound, no max-volume locks, no disabled back button).
- **MVVM Architecture**: Clean separation of UI and business logic using ViewModels and reactive StateFlow streams.
- **Boot persistence**: Alarms reschedule automatically when the device restarts.
- **Volume lock**: The alarm stream is forced to maximum every second while active.
- **Full-screen overlay**: The puzzle screen appears over the lock screen with back-button disabled for real alarms.
- **Room database**: Persistent alarm storage with reactive Flow-based UI updates.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                         │
│   (Compose UI: settings, Glassmorphic cards, EditSheet)      │
└────────────────────────┬────────────────────────────────────┘
                         │ observes StateFlow
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                       MainViewModel                         │
│            (Coordinates UI state and DB operations)          │
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
│  schedule(context, alarm) → AlarmManager.setAlarmClock()    │
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
│  │  (skipped during Preview Mode)                            │
│  → launches AlarmDismissActivity directly                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  AlarmDismissActivity                         │
│  If real alarm: showWhenLocked, turnScreenOn, back disabled │
│  If preview mode: safe backdrop, back button allowed        │
│  → hosts AlarmDismissScreen (Compose)                        │
│  → onDismissComplete → finish() (+ stopService if real)      │
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
│  Boot/time/time-zone/app update → query enabled alarms       │
│  → reschedules each via AlarmScheduler.schedule()            │
└─────────────────────────────────────────────────────────────┘
```

---

## Source Files

### Core Alarm Flow

| File | Purpose |
|------|---------|
| `AlarmScheduler.kt` | Calculates the next trigger time and registers user-visible exact alarms via `AlarmManager.setAlarmClock()`. Scheduling returns a typed result for success, missing permission, or system failure. |
| `AlarmReceiver.kt` | `BroadcastReceiver` triggered by `AlarmManager`. Starts the foreground `AlarmService` and automatically reschedules recurring alarms for the next active day. Disables one-time alarms. |
| `AlarmService.kt` | Owns one active alarm session, asynchronously prepares fallback audio, manages volume/audio focus, uses alarm-specific notification identities, and safely replaces overlapping alarms. |
| `AlarmDismissActivity.kt` | Full-screen activity that appears over the lock screen. Hosts the Compose puzzle UI, accepts replacement alarm intents, and handles normal alarm security locks or safe `IS_PREVIEW` executions. |
| `AlarmDismissScreen.kt` | Compose screen that orchestrates puzzle progression (Task 1 of N → Task N of N). Delegates to individual puzzle views. |
| `BootReceiver.kt` | Reschedules enabled alarms after boot and after exact-alarm access is granted. Uses `goAsync()` for safe coroutine work in a receiver. |

### UI & Architecture Layer

| File | Purpose |
|------|---------|
| `ui/main/MainViewModel.kt` | Lifecycle-managed state holder that coordinates injected repository and scheduling abstractions and emits one-shot UI events without retaining Android `Context`. |
| `MainActivity.kt` | Displays the Glassmorphic alarm settings dashboard and hosts the slide-up `AlarmEditSheet` bottom drawer editor. |
| `theme/` | Material 3 dark theme configuration. |

### Data Layer

| File | Purpose |
|------|---------|
| `data/Alarm.kt` | Room `@Entity`. Fields: `id`, `hour`, `minute`, `daysOfWeek` (CSV of ISO-8601 day numbers), `isEnabled`, `puzzlesList` (CSV of puzzle types), `puzzleCount`. |
| `data/AlarmDao.kt` | Room DAO with `getAllAlarms()` (Flow), `getEnabledAlarms()`, `getAlarmById()`, `insertAlarm()`, `updateAlarm()`, `deleteAlarm()`. |
| `data/AlarmRepository.kt` | Repository boundary used by the ViewModel, with a Room-backed implementation that owns generated-ID mapping. |
| `data/AlarmDatabase.kt` | Singleton Room database with thread-safe `getDatabase()` builder and explicit migrations from versions 1 through 3. Versioned schemas are committed under `app/schemas/`. |

Alarm database files are deliberately excluded from cloud backup and device
transfer. Alarm rows contain operational enabled/disabled state, while Android
does not restore the matching `AlarmManager` registrations; excluding them
prevents a restored alarm from appearing enabled without actually being armed.

### Puzzle Engines

| File | Purpose |
|------|---------|
| `puzzle/PuzzleProviders.kt` | Interfaces (`MathPuzzleProvider`, `TypingPuzzleProvider`, `MemoryPuzzleProvider`) that enable dependency injection for testing. |
| `puzzle/MathEngine.kt` | Generates arithmetic puzzles at Easy (add/subtract), Medium (multiply + add), and Hard (solve for x) difficulty levels. Implements `MathPuzzleProvider`. |
| `puzzle/TypingEngine.kt` | Provides random motivational quotes and case-sensitive matching. Implements `TypingPuzzleProvider`. |
| `puzzle/MemoryEngine.kt` | Generates random sequences of grid indices (0–8) and validates step-by-step user input. Implements `MemoryPuzzleProvider`. |

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

The persisted CSV columns remain compatible with database versions 1–3, but
application code consumes them through `AlarmDays` and `PuzzleSelection`.
Those domain values discard unknown legacy values, remove duplicates, produce
canonical ordering, and guarantee a Math fallback for an invalid puzzle list.

---

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE` | Run `AlarmService` as a foreground service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required `foregroundServiceType` for audio on Android 14+ |
| `USE_FULL_SCREEN_INTENT` | Launch dismiss activity from the notification |
| `SCHEDULE_EXACT_ALARM` | Schedule exact alarms (user-grantable on Android 12+) |
| `WAKE_LOCK` | Keep CPU alive during alarm processing |
| `MODIFY_AUDIO_SETTINGS` | Apply gradual alarm volume and restore the previous level after dismissal |
| `POST_NOTIFICATIONS` | Show foreground service notification |
| `RECEIVE_BOOT_COMPLETED` | Trigger `BootReceiver` after device restart |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Open the system exemption request for reliable OEM background execution |

> **Note on Android 14+ (SDK 34+):** The `SCHEDULE_EXACT_ALARM` permission is revoked by default. On physical devices users must enable "Alarms & reminders" in Special App Access settings. On emulators, run:
> ```bash
> adb shell appops set com.example.smartalarmer SCHEDULE_EXACT_ALARM allow
> ```

---

## Build & Run

```bash
# Launch emulator, build, install, and run on emulator (using launcher script)
./run_app.sh

# Run unit tests (JVM)
./gradlew test

# Run instrumented UI tests (requires emulator or device)
./gradlew connectedAndroidTest

# Build debug APK
./gradlew assembleDebug
```

After building, the APK file can be retrieved at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Testing Strategy

### Test Pyramid

```
        ┌───────────────────────┐
        │  Emulator E2E Tests   │  ← Manual via ADB / script
        │  (integration)        │
        ├───────────────────────┤
        │  Instrumented Tests   │  ← connectedAndroidTest
        │  (Room, on-device UI) │
        ├───────────────────────┤
        │    JVM Unit Tests     │  ← test task
        │  (engines, ViewModel) │
        └───────────────────────┘
```

### 1. JVM Unit Tests (`app/src/test/`)

These run on the host JVM with no Android framework required.

| Test Class | What It Covers |
|------------|---------------|
| `MathEngineTest` | Verifies puzzle generation at all three difficulty levels: correct difficulty field, operator presence, and answer range. |
| `TypingEngineTest` | Tests exact string matching (including whitespace trimming) and non-empty quote generation. |
| `MemoryEngineTest` | Tests sequence generation (correct length, valid indices 0–8) and step-by-step verification (correct prefix, wrong prefix, overflow). |
| `AlarmSchedulerTest` | Tests `calculateNextTriggerTime()` against a mock `Calendar` for scheduling combinations. |
| `MainViewModelTest` | Verifies view model state flow, bottom sheet visibility switches, and database save/toggle/delete hooks. |

### 2. Instrumented UI & Integration Tests (`app/src/androidTest/`)

These run on a real device or emulator and require the Android runtime.

| Test Class | What It Covers |
|------------|---------------|
| `AlarmDatabaseTest` | Creates an in-memory Room database, inserts alarms, reads them back, and validates CRUD operations. |
| `AlarmMigrationTest` | Creates a version 1 database, runs 1→2→3 migrations, lets Room validate the final schema, and checks data/default preservation. |
| `AlarmListScreenTest` | Tests Composable settings cards, dynamic weekdays text generation, play/test trigger callbacks, and edit clicks. |
| `AlarmDismissScreenTest` | Verifies correctness of Compose states in individual Math, Memory, and Typing puzzle screens. |
| `AlarmDismissActivityTest` | Launches the activity in preview mode (`IS_PREVIEW = true`) to verify the back button destroys it correctly. |

---

## Implementation History

Development proceeded incrementally, with each feature committed and verified before moving to the next:

| Commit | Description |
|--------|-------------|
| `8e2b572` | **Latest:** Add run_app.sh launcher script |
| `9ce179e` | Implement Glassmorphism styling, BottomSheet editor, and AlarmListScreenTest updates |
| `05395e7` | Support IS_PREVIEW flag in AlarmDismissActivity and write AlarmDismissActivityTest |
| `ae9ac6c` | Implement MainViewModel and MainViewModelTest |
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
| `c8a04f9` | **Cleanup:** delete unused template boilerplate |
| `151b9fe` | Implement `BootReceiver` to reschedule active alarms on device boot |
| `d79cc28` | Precise day-of-week scheduling calculation (ISO-8601 mapping) |
| `2e379f1` | Extract `calculateNextTriggerTime()`, add scheduling unit tests, add auto-rescheduling in `AlarmReceiver` |

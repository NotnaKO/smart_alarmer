# Smart Alarmer

A native Android alarm clock that forces you to wake up by completing cognitive puzzles before the alarm can be silenced. No snooze button, no mercy.

## Overview

Smart Alarmer uses Android's `AlarmManager` to schedule exact alarms that trigger a foreground service playing audio with a gradual volume ramp. Verified puzzle progress reduces the sound, while completing the configured puzzle sequence is the only way to dismiss the alarm. The alarm survives device reboots and automatically reschedules recurring alarms after they fire.

### Key Features

- **Four puzzle types**: Math equations, typing challenges, memory pattern games, and physical shake challenges with a sensor-safe fallback.
- **Customizable Alarm Configuration**: A slide-up `ModalBottomSheet` lets you configure specific repeat weekdays, choose which puzzle types to show, and set the puzzle count.
- **Modern Glassmorphic Dark Theme**: Premium styling featuring semi-transparent overlays, glowing accents, and smooth feedback animations.
- **Safe Preview/Test Mode**: Test alarm configurations directly from the settings list with a single click. The test mode runs in a non-disruptive activity context (no loud sound, no max-volume locks, no disabled back button).
- **MVVM Architecture**: Clean separation of UI and business logic using ViewModels and reactive StateFlow streams.
- **Boot persistence**: Alarms reschedule automatically when the device restarts.
- **Wake-up checks**: Optional chained follow-up alarms require one easy task after the main alarm, with configurable count and 5, 10, or 15 minute intervals measured from each completion.
- **Progress-aware volume**: Volume rises over a selectable 30, 60, 120, or 240 seconds, falls with verified puzzle progress, and resumes rising after five seconds of inactivity.
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
│  AlarmTimeCalculator(Clock, ZoneId) → next Instant           │
│  schedule(context, alarm) → AlarmManager.setAlarmClock()    │
│  cancel(context, alarm)                                      │
└────────────────────────┬────────────────────────────────────┘
                         │ RTC_WAKEUP broadcast
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     AlarmReceiver                            │
│  → validates the persisted alarm                            │
│  → starts AlarmService (foreground)                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      AlarmService                            │
│  → foreground notification with full-screen intent           │
│  → starts MediaPlayer (alarm tone, looping)                  │
│  → enforces progress-aware volume every 1 second             │
│  │  (skipped during Preview Mode)                            │
│  → keeps CPU awake until the alarm session is dismissed      │
│  → after initialization: reschedules/updates schedule health │
└────────────────────────┬────────────────────────────────────┘
                         │
              full-screen/content PendingIntent
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
│  MathPuzzleView · TypingPuzzleView · MemoryPuzzleView         │
│  ShakePuzzleView · VirtualKeyboard                            │
│         │              │             │          │             │
│    MathEngine    TypingEngine   MemoryEngine  ShakeSensor    │
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
| `AlarmTimeCalculator.kt` | Pure `java.time` wall-clock calculation with injected `Clock` and `ZoneId`, including deterministic DST gap/overlap behavior. |
| `AlarmReceiver.kt` | `BroadcastReceiver` triggered by `AlarmManager`. Validates that the persisted alarm is still enabled and requests the foreground `AlarmService`; failed start requests are recorded without disabling the alarm. |
| `AlarmService.kt` | Owns one active alarm session, posts foreground state first, persists launcher recovery data, delegates playback/audio focus/wake-lock handling, and only then confirms delivery so recurring rescheduling or one-time disablement cannot happen before initialization. |
| `AlarmDismissActivity.kt` | Full-screen activity that appears over the lock screen. Hosts the Compose puzzle UI, accepts replacement alarm intents, and handles normal alarm security locks or safe `IS_PREVIEW` executions. |
| `AlarmDismissScreen.kt` | Saveable puzzle-sequence orchestration (Task 1 of N → Task N of N), including rotation-safe progress. |
| `MathPuzzleView.kt`, `TypingPuzzleView.kt`, `MemoryPuzzleView.kt`, `ShakePuzzleView.kt` | Focused, accessible puzzle controls with injectable providers. |
| `VirtualKeyboard.kt` | Localized 48 dp virtual keyboard with labeled shift, backspace, and space controls. |
| `BootReceiver.kt` | Reschedules enabled alarms after boot and after exact-alarm access is granted. Uses `goAsync()` for safe coroutine work in a receiver. |

### UI & Architecture Layer

| File | Purpose |
|------|---------|
| `ui/main/MainViewModel.kt` | Lifecycle-managed state holder that coordinates injected repository and scheduling abstractions and emits one-shot UI events without retaining Android `Context`. |
| `MainActivity.kt` | Activity wiring, lifecycle-aware state collection, permission refresh, and dashboard orchestration. |
| `ActiveAlarmRecovery.kt` | Recreates the dismiss intent from durable active-session state when the user returns through the launcher, while suppressing recovery after dismissal has begun. |
| `AlarmItemCard.kt` | Accessible alarm summary and alarm actions. |
| `AlarmEditSheet.kt` | Saveable, scrollable alarm editor with sensor-aware puzzle selection and volume-ramp presets. |
| `theme/` | Material 3 dark theme configuration. |

### Data Layer

| File | Purpose |
|------|---------|
| `data/Alarm.kt` | Room entities for alarm configuration and durable active wake-up-check sessions. Alarm settings include check enablement, count, and interval. |
| `data/AlarmDao.kt` | Room DAO with `getAllAlarms()` (Flow), `getEnabledAlarms()`, `getAlarmById()`, `insertAlarm()`, `updateAlarm()`, `deleteAlarm()`. |
| `data/AlarmRepository.kt` | Repository boundary used by the ViewModel, with a Room-backed implementation that owns generated-ID mapping. |
| `data/AlarmDatabase.kt` | Singleton Room database with thread-safe `getDatabase()` builder and explicit migrations from versions 1 through 6. Versioned schemas are committed under `app/schemas/`. Version 6 adds wake-up-check settings and durable active sessions. |

Alarm database files are deliberately excluded from cloud backup and device
transfer. Alarm rows contain operational enabled/disabled state, while Android
does not restore the matching `AlarmManager` registrations; excluding them
prevents a restored alarm from appearing enabled without actually being armed.
The transient `active_alarm_session` preferences are excluded for the same
reason and to prevent a restored device from inheriting another device's saved
alarm-volume recovery state.

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
| `SCHEDULE_EXACT_ALARM` | Android 12/12L compatibility for exact alarms (`maxSdkVersion=32`) |
| `USE_EXACT_ALARM` | Automatically granted exact-alarm access for this dedicated alarm app on Android 13+ |
| `WAKE_LOCK` | Keep CPU alive during alarm processing |
| `MODIFY_AUDIO_SETTINGS` | Apply progress-aware alarm volume and restore the previous level after dismissal |
| `POST_NOTIFICATIONS` | Show foreground service notification |
| `RECEIVE_BOOT_COMPLETED` | Trigger `BootReceiver` after device restart |

> **Note on Android 12/12L (SDK 31–32):** These releases use the user-controlled
> `SCHEDULE_EXACT_ALARM` special access. Enable “Alarms & reminders” on a physical
> test device. On an emulator, run:
> ```bash
> adb shell appops set com.notnako.smartalarmer SCHEDULE_EXACT_ALARM allow
> ```

---

## Build & Run

```bash
# Launch emulator, build, install, and run on emulator (using launcher script)
./run_app.sh

# Run unit tests (JVM)
./gradlew test

# Run instrumented UI tests with a headless, resource-limited emulator
./run_instrumented_tests.sh

# Build debug APK
./gradlew assembleDebug
```

The emulator wrappers deliberately use Google SwiftShader with Vulkan disabled, two pinned CPU
cores, reduced process priority, a memory ceiling, and no snapshots. The
instrumented-test wrapper additionally runs headless, enforces a 15-minute
timeout, and always shuts down the AVD. Avoid starting the project AVD directly,
because the host Vulkan/gfxstream path can destabilize the Linux desktop.
The lightweight `small_phone` AVD is preferred when installed; set `ANDROID_AVD`
to select another profile explicitly.
Android Emulator 36.6.11 is fail-fast blocked on Fedora 44 because local host
and software renderer boots both reproduce a QEMU `SIGSEGV`; use a physical
device or a different emulator package version on that host combination.

After building, the APK file can be retrieved at:
`app/build/outputs/apk/debug/app-debug.apk`

### Release builds

Production builds use the application ID `com.notnako.smartalarmer` and R8
code/resource shrinking. Local builds derive a monotonically increasing
`versionCode` from the Git commit count. `build_release.sh` accepts a version
name by itself (for example, `./build_release.sh 0.1.0-alpha.3`) and, with no
arguments, derives the name from the latest reachable `v...` Git tag. CI and
release shells can still override both values with
`SMART_ALARMER_VERSION_CODE` and `SMART_ALARMER_VERSION_NAME`.

Release signing never falls back to the Android debug key. Set all four signing
variables to produce a signed artifact:

```bash
export SMART_ALARMER_KEYSTORE_FILE=/secure/path/smart-alarmer.jks
export SMART_ALARMER_KEYSTORE_PASSWORD='...'
export SMART_ALARMER_KEY_ALIAS='smart-alarmer'
export SMART_ALARMER_KEY_PASSWORD='...'
./gradlew bundleRelease
```

With no signing variables, `assembleRelease`/`bundleRelease` creates an
unsigned, minified artifact suitable for build verification only. Keystores are
ignored by Git and should be stored in a secret manager, outside this repository.

---

## Testing Strategy

### Test Pyramid

```
        ┌───────────────────────┐
        │  Emulator E2E Tests   │  ← connectedAndroidTest matrix
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
| `AlarmTimeCalculatorTest` | Tests one-time/recurring wall-clock calculations with fixed clocks, explicit zones, and DST gap/overlap transitions. |
| `AlarmSchedulerTest` | Tests exact-alarm permission and failure result handling. |
| `MainViewModelTest` | Verifies view model state flow, bottom sheet visibility switches, and database save/toggle/delete hooks. |

### 2. Instrumented UI & Integration Tests (`app/src/androidTest/`)

These run on a real device or emulator and require the Android runtime.
CI runs the full API 26, 31, 34, and 36 emulator matrix for pull requests,
pushes, and manual runs so exact-alarm, notification, and full-screen behavior
is exercised at each relevant platform boundary.

| Test Class | What It Covers |
|------------|---------------|
| `AlarmDatabaseTest` | Creates an in-memory Room database, inserts alarms, reads them back, and validates CRUD operations. |
| `AlarmMigrationTest` | Creates a version 1 database, runs every migration through version 5, lets Room validate the final schema, and checks data/default preservation. |
| `AlarmListScreenTest` | Tests Composable settings cards, dynamic weekdays text generation, play/test trigger callbacks, and edit clicks. |
| `AlarmDismissScreenTest` | Verifies Math, Memory, Typing, and Shake behavior, accessibility semantics, saved puzzle input, and rotation-safe task progression. |
| `AlarmDismissActivityTest` | Launches the activity in preview mode (`IS_PREVIEW = true`) to verify the back button destroys it correctly. |
| `AlarmDeliveryEndToEndTest` | Schedules a safe preview alarm through `AlarmManager` and verifies receiver/service delivery without audio or volume changes. |

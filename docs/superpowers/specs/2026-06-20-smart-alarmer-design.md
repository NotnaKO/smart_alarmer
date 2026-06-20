# Smart Alarmer Android App Design Spec

Design specification for a native Android smart alarm application that prevents easy dismissal by enforcing maximum volume and requiring the user to solve randomized cognitive puzzles (Math, Typing, Memory).

## User Review Required

- **No Snooze Policy**: Once an alarm fires, the user cannot snooze or mute it. The back button, volume keys, and swipe-to-dismiss gestures are overridden or blocked.
- **Max Volume Enforcement**: If the user attempts to lower the volume, the background service will automatically force the alarm volume back to 100%.

## Open Questions
- None. All requirements have been aligned and finalized.

## Proposed Changes

We will create a native Android application using Kotlin, Jetpack Compose (Material 3), and Gradle (AGP 9).

### Core Components

#### [NEW] [AlarmReceiver.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmReceiver.kt)
A standard BroadcastReceiver that handles intents fired by Android's `AlarmManager`.
- Starts the `AlarmService` using `startForegroundService` when the scheduled time is reached.

#### [NEW] [AlarmService.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmService.kt)
A foreground service that handles alarm playback and volume enforcement.
- Promotes itself to a foreground service with a high-importance notification channel.
- Attaches a `fullScreenIntent` targeting the `AlarmDismissActivity` so it displays immediately on top of the lock screen.
- Runs a looping `MediaPlayer` utilizing the `AudioAttributes.USAGE_ALARM` audio stream.
- Hooks into the system `AudioManager` to lock the volume at 100%.
- Holds a CPU `WakeLock` to keep the processor awake while ringing.

#### [NEW] [AlarmDismissActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt)
The full-screen overlay Activity containing the puzzle interface.
- Configured with `setShowWhenLocked(true)` and `setTurnScreenOn(true)` to bypass the lock screen.
- Disables standard system navigation gestures/back button by overriding `onBackPressedDispatcher`.
- Renders the puzzle state machine in Jetpack Compose.
- Stops the service and finishes itself only when all randomized tasks are successfully completed.

#### [NEW] [AlarmDatabase.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/data/AlarmDatabase.kt)
A Room database to persist alarms and preferences.
- **Alarm Entity**: `id`, `hour`, `minute`, `daysOfWeek` (encoded), `isEnabled`, `puzzlePool` (binary flags or CSV of active puzzles), `puzzleCount` (number of tasks to pick).

### Puzzles Implementation

Three puzzles will be implemented in the initial release:
1. **Math Puzzle**:
   - Generates arithmetic expressions (Easy, Medium, Hard).
   - Displayed with a custom numeric grid (keypad) to prevent keyboard auto-fill tricks.
2. **Typing Puzzle**:
   - Displays a motivational or funny wake-up quote.
   - Text input with auto-correct and pasting disabled. Must match character-for-character.
3. **Memory Puzzle (Simon Says)**:
   - 3x3 grid of colored circles.
   - Flashes a sequence of 4-6 tiles which the user must duplicate. Any error resets the pattern.

### UI/UX Styling
- **Theme**: Premium dark theme featuring `#121212` backgrounds, with electric indigo (`#6366F1`) and neon violet (`#8B5CF6`) accent colors.
- **Visuals**: Clean progress bars, subtle micro-animations for card transitions, and screen state transitions.

## Verification Plan

### Automated Tests

#### 1. Unit Tests (Local JVM Tests under `test/`)
- **`MathPuzzleTest`**:
  - Verify that equations generated for each difficulty (Easy, Medium, Hard) evaluate to the correct expected answer.
  - Verify range constraints (e.g., no negative results in Easy, divisions result in whole integers, algebraic variables resolve correctly).
- **`TypingPuzzleTest`**:
  - Verify character-by-character string matching algorithm (whitespace trimming, case sensitivity, handling of special characters).
  - Verify the quotes list loader returns valid, non-empty, and diverse strings.
- **`MemoryPuzzleTest`**:
  - Verify state machine logic for Simon Says: transitions between pattern playback, user input phase, success state, and error reset state.
  - Verify that tapping a correct item updates the cursor and tapping a wrong item resets the sequence.
- **`AlarmSchedulerTest`**:
  - Test time calculation utility functions (e.g., getting the next epoch millisecond time for a given hour/minute/day of week configuration).
- **`AlarmDatabaseTest` (In-Memory Room DB)**:
  - Test Room DAO operations (`insert`, `delete`, `queryActiveAlarms`) using an in-memory database instance to ensure data integrity.

#### 2. Android Instrumentation Tests (Device/Emulator Tests under `androidTest/`)
- **`AlarmDismissActivityTest` (Compose UI Tests)**:
  - Verify that the custom numeric keypad clicks input characters into the Math answer buffer correctly.
  - Verify that the Typing puzzle visual comparison updates correct letters in green/incorrect in red as typed.
  - Verify the progress bar step increments when transitions happen.
  - Verify that the Activity cannot be closed by a simulated Back button press.

### Manual Verification
1. **Device Deploy**: Compile and install the debug APK on an Android device or emulator.
2. **Alarm Setup**: Set an alarm for 1 minute in the future, selecting all three puzzles (Math, Typing, Memory) with a task count of 3.
3. **Trigger Verification**: Lock the device screen. Once the time is reached, verify that:
   - The device screen turns on and wakes up.
   - The fullscreen alarm activity is presented over the lock screen.
   - High-priority alarm audio begins playing at 100% volume.
4. **Resilience Check**:
   - Press the physical volume-down keys and check that the app immediately resets the volume to max.
   - Attempt to swipe down notifications or press back and check that the overlay remains active.
5. **Puzzle Solve & Dismissal**:
   - Complete the Math, Typing, and Memory tasks sequentially.
   - Verify that completing the final task sends the completion signal to the service, stops the audio playback, and finishes the Activity.

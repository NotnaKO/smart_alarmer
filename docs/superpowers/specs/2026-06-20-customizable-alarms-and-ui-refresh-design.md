# Design Specification: Customizable Alarms and UI Refresh

This document specifies the technical design for introducing full alarm customization (active days, selected puzzles, and puzzle count), a modern Glassmorphism UI refresh, a secure test/preview mode, and an MVVM architecture.

---

## 1. Overview of Proposed Changes

Currently, the application allows users to create alarms, but they are hardcoded to Mon-Fri, all three puzzles (Math, Memory, Typing), and a puzzle count of 3. The user interface uses standard Material 3 layouts without modern aesthetic styling.

To address these limitations, we are introducing:
- **MVVM Architecture**: Introducing `MainViewModel` to separate UI state from database/scheduling operations.
- **Customizable Alarms**: A slide-up `ModalBottomSheet` that enables selecting specific active weekdays, selecting puzzle types, adjusting puzzle count, and choosing the time.
- **UI Refresh (Glassmorphic Theme)**: Adapting a dark theme with semi-transparent elements, borders, glowing highlights, and refined spacing.
- **Alarm Preview Mode**: An intent-based test mode that lets users try their alarm configuration without sounding a loud alarm or locking down their device.

---

## 2. Architecture & State Management

A new ViewModel class, `MainViewModel`, will manage all business logic, database transactions, and UI states.

### `MainViewModel.kt`
- **Location**: `com.example.smartalarmer.ui.main`
- **Package Path**: [MainViewModel.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt)
- **Responsibilities**:
  - Expose `alarms: StateFlow<List<Alarm>>` retrieved from `AlarmDao.getAllAlarms()`.
  - Expose `isBottomSheetVisible: StateFlow<Boolean>`.
  - Expose `editingAlarm: StateFlow<Alarm?>` (non-null when editing an existing alarm).
  - Manage saving (updating or inserting) alarms:
    - Automatically updates the Room Database.
    - Manages scheduling or canceling via `AlarmScheduler`.
  - Manage deleting alarms and canceling their pending intent scheduling.

---

## 3. UI Components & Layout

We will implement a custom Glassmorphic styling system using standard Jetpack Compose modifiers, gradients, and transparencies.

### 3.1. Main Screen Layout ([MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt))
- **Background**: A deep, rich gradient background (`0xFF0F0C20` to `0xFF15102A`).
- **Header**: Large bold typography with the title "Smart Alarmer" and a glassmorphic card for brief status/instructions.
- **Empty State**: An elegant message with an icon advising the user to tap the "+" button.

### 3.2. Alarm Card Component
- **Surface**: `0x1FFFFFFF` background overlay, with a white border (`0x1AFFFFFF`) and round corners.
- **Controls**:
  - A stylized large time display.
  - Dynamic active days indicator:
    - `"Every day"` (all 7 days)
    - `"Weekdays"` (Mon-Fri)
    - `"Weekends"` (Sat-Sun)
    - CSV names (e.g. `"Mon, Wed, Fri"`)
    - `"One-time"` (if no days are selected)
  - Active puzzle badges indicating which tasks (Math, Memory, Typing) are configured.
  - A play button for **Test Alarm**.
  - A delete button.
  - An enable/disable toggle switch.

### 3.3. Alarm Editor Sheet (`AlarmEditSheet`)
A Compose `ModalBottomSheet` displaying:
1. **Selected Time**: Clicking it opens the `TimePickerDialog` to change hour and minute.
2. **Active Weekdays Selector**: Row of circular buttons `[M, T, W, T, F, S, S]`. Selected days are filled with an active gradient, while disabled days are dark gray.
3. **Puzzles Checklist**: Row of checkbox chips to toggle `MATH`, `MEMORY`, and `TYPING`. At least one puzzle type must be selected.
4. **Required Puzzles Count**: Stepper button row `[ - ]  [ 2 ]  [ + ]` to configure the puzzle count. It automatically clamps between `1` and the number of active puzzle types selected.
5. **Save/Cancel Actions**: Full width glassmorphic buttons.

---

## 4. Alarm Preview Mode

We will update [AlarmDismissActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt) to handle test launches securely.

- When launched with `IS_PREVIEW = true`:
  - Skip calling `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
  - Do not override back button presses (allowing the user to exit using the system back gesture/button).
  - When all tasks are completed, invoke `finish()` directly without attempting to stop the non-existent foreground service.

---

## 5. Verification & Testing Plan

### 5.1. Automated Unit Tests
- Update `AlarmSchedulerTest` or add unit tests in `MainViewModelTest` to verify:
  - Correct weekdays selection to CSV conversion.
  - Puzzle count clamping.
  - Schedule / cancel database hooks.

### 5.2. Instrumented UI Tests
- Add a UI test in `AlarmDismissScreenTest` or a new test checking that launching the preview mode renders the tasks and finishes properly without errors.
- Run tests via:
  ```bash
  ./gradlew test
  ./gradlew connectedAndroidTest
  ```

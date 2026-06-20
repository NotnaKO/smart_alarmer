# Multiple Alarm List Design Spec

This document details the design for updating the Smart Alarmer application to support setting custom alarm times using a standard `TimePickerDialog` and managing them via an inline list.

## 1. Requirements & Intent
* Users can view all scheduled alarms in a scrollable list.
* Users can add new alarms by clicking a Floating Action Button (FAB) that displays the Android system `TimePickerDialog`.
* Users can enable or disable existing alarms using a Switch toggle.
* Users can delete alarms from the database and cancel their `AlarmManager` triggers.
* Newly created alarms will be enabled by default and require all three puzzles (Math, Memory, Typing).

## 2. Component Design & Changes

### A. MainActivity UI
We will replace the hardcoded "Schedule Test Alarm" layout with a fully reactive Material 3 layout:
* **Scaffold**: Integrates a Floating Action Button (FAB) for adding alarms.
* **LazyColumn**: Displays the list of alarms queried from Room DB as a Flow.
* **AlarmCard**: Renders:
  * Formatted time text (e.g. `08:30` with AM/PM indicator).
  * Puzzle count summary (e.g. `3 puzzles active`).
  * `Switch`: Bound to the alarm's `isEnabled` property.
  * `IconButton` (Delete): Red icon button to remove the alarm.

### B. TimePickerDialog Integration
* We will implement a wrapper around `TimePickerDialog` in Compose/Activity context:
  - Invoked when the FAB is clicked.
  - Passes the selected hour and minute to the coroutine scope to insert/schedule the alarm.

### C. AlarmManager & Database Coordination
* **Scheduling**: Toggling a Switch ON or adding an alarm invokes `AlarmScheduler.schedule(...)` and inserts/updates the DB record with `isEnabled = true`.
* **Canceling**: Toggling a Switch OFF or deleting an alarm invokes `AlarmScheduler.cancel(...)` and updates/deletes the DB record.

---

## 3. Verification Plan

### Automated Tests
* Update `MainScreenViewModelTest` or add unit tests for verifying database toggles.
* Update `AlarmDatabaseTest.kt` if database schema constraints are affected.

### Manual Verification
* Deploy the updated build to the emulator.
* Verify clicking the FAB opens the `TimePickerDialog`.
* Verify adding a custom alarm displays it in the list.
* Verify toggling the Switch schedules/cancels the alarm in `AlarmManager` and updates the Room database status.

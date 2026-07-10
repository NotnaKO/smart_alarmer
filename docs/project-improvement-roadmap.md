# Smart Alarmer Improvement Roadmap

This document records the July 10, 2026 project review and provides an ordered,
testable path for improving the app. Reliability and safe failure behavior take
priority because a missed or impossible-to-dismiss alarm is more serious than a
missing convenience feature.

## Validation baseline

- `./gradlew testDebugUnitTest`: 20 tests passed.
- `./gradlew lintDebug`: passed with warnings.
- `./gradlew connectedDebugAndroidTest`: 54 tests passed on the
  `medium_phone (AVD) - 16` emulator.
- Instrumented logs showed that preview receiver/service tests started real
  alarm playback twice and launched two dismissal activities. Passing tests did
  not detect this disruptive behavior.

## Phase 1: Alarm safety and reliability

- [x] Make `IS_PREVIEW` propagate from `AlarmReceiver` through `AlarmService`
  to `AlarmDismissActivity`. Preview service starts must not play audio, change
  volume, use a full-screen intent, or launch the activity automatically.
- [x] Add `MODIFY_AUDIO_SETTINGS`, request transient audio focus, restore the
  previous alarm volume, and release every playback fallback resource.
- [x] Make exact-alarm scheduling return a result instead of assuming success.
  Check `canScheduleExactAlarms()`, handle `SecurityException`, select one exact
  alarm permission deliberately, and reschedule after permission is granted.
- [x] Replace automatic dismissal for an invalid/empty puzzle configuration
  with a known solvable fallback puzzle.
- [x] Detect unavailable shake sensors and provide a solvable fallback rather
  than trapping the user.
- [ ] Make `AlarmService` start/stop idempotent. Handle repeated starts, null
  sticky restarts, overlapping alarms, unique notifications/PendingIntents,
  asynchronous media preparation, and `ToneGenerator` ownership.

## Phase 2: Scheduling and persistence

- [ ] Use lifecycle-managed `MainViewModel` creation and inject repository and
  scheduler abstractions. Keep Android `Context`, Toasts, and system calls out
  of the ViewModel.
- [ ] Enable Room schema export and add migration tests for versions 1 -> 2 ->
  3.
- [ ] Define backup/restore behavior. Restored enabled alarms must be reconciled
  with `AlarmManager`, or operational alarm state should be excluded from backup.
- [ ] Replace raw day/puzzle CSV parsing with validated domain values while
  retaining a compatible database migration.
- [ ] Reschedule wall-clock alarms after time or time-zone changes. Prefer
  `java.time` with injected `Clock` and `ZoneId`, including DST tests.

## Phase 3: Testing and release readiness

- [ ] Expand JVM tests around save/toggle/delete behavior, permission denial,
  service restart/re-entry, malformed data, concurrent alarms, and time edges.
- [ ] Run device tests on modern Android behavior in CI (at least API 31 and an
  API 34+ image), not only API 29.
- [ ] Configure production signing, a non-example application ID, versioning,
  R8, and resource shrinking. Never ship the debug signing key.
- [ ] Remove unused Navigation 3 and serialization dependencies and migrate
  Room code generation from kapt to KSP.

## Phase 4: UI, accessibility, and maintainability

- [ ] Split `MainActivity.kt` and `AlarmDismissScreen.kt` into focused screens,
  components, and state holders.
- [ ] Add semantic labels and state to puzzle controls, use at least 48 dp touch
  targets, and support large fonts, landscape, and larger screens.
- [ ] Collect flows with lifecycle awareness and preserve edit/puzzle state
  across recreation where appropriate.
- [ ] Remove puzzle solutions from production logs.
- [ ] Resolve localization lint warnings and remaining hard-coded UI strings.
- [ ] Keep `docs/README.md` and `AGENTS.md` aligned with the implementation,
  including the SHAKE puzzle, DAO signatures, and preview behavior.

## Working agreement

Complete one bounded item at a time. Each item should include regression tests,
the smallest relevant full-suite verification, and an update to its checkbox in
this roadmap.

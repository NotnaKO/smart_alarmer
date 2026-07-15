# Smart Alarmer — AI Agent Guide

This Android alarm clock app forces wake-ups by requiring cognitive puzzles to be solved before the alarm can be silenced. Coded in Kotlin with Jetpack Compose, Room database, and a modular puzzle engine architecture.

---

## 🏗️ Architecture & Package Structure

**See [docs/README.md](docs/README.md)** for complete architecture diagram and feature overview.

**Key packages** (`app/src/main/java/com/example/smartalarmer/`):
- **`ui/main/`** — MainActivity wiring, MainViewModel, AlarmItemCard, and AlarmEditSheet
- **`ui/dismiss/`** — AlarmDismissActivity, sequence screen, keyboard, and focused puzzle views
- **`ui/theme/`** — Glassmorphic dark theme styling
- **`data/`** — Room entities, DAO, database setup, coroutine-driven queries
- **`puzzle/`** — Injectable Math, Typing, Memory, and shake-sensor providers
- **`service/`** — AlarmService (foreground notification, MediaPlayer, volume lock)
- **`receiver/`** — AlarmReceiver (alarm trigger), BootReceiver (reschedule on device reboot)
- **`scheduler/`** — AlarmScheduler (pure calculation; no Android context; testable)

**Pattern**: MVVM with reactive StateFlow streams; Room database drives UI updates via Flow.

---

## 🔨 Build, Run & Test Commands

### Build & Run
```bash
./gradlew build                    # Full clean build
./gradlew installDebug             # Install debug APK to emulator
./gradlew :app:run                 # Build and run on connected device

# Convenience script (launches emulator + installs APK)
bash run_app.sh
```

### Testing
```bash
./gradlew test                     # Unit tests (src/test/)
./gradlew connectedAndroidTest     # Instrumented tests (src/androidTest/)
./gradlew testDebugUnitTest        # Debug unit tests only
```

### Gradle Tasks
```bash
./gradlew tasks                    # List all available tasks
./gradlew app:dependencies         # View dependency tree
./gradlew :app:lintDebug          # Lint checks
```

---

## 📋 Key Conventions & Data Formats

### Day Encoding
Alarm repeat days are stored as **comma-separated ISO-8601 integers** (1=Monday, 7=Sunday):
- `"1,3,5"` = Monday, Wednesday, Friday
- Single day: `"3"` (Wednesday)
- No value = one-time alarm

**When modifying alarm queries or filters**, use `Alarm.repeatDays`; parse or
encode CSV only at persistence and Intent boundaries.

### Puzzle Type Encoding
Puzzle types in config are stored as **comma-separated string values**:
- `"MATH,TYPING,MEMORY,SHAKE"` = all four types
- `"MATH"` = only math puzzles
- Shuffle and limit in AlarmDismissScreen; order from config is ignored
- Use `Alarm.puzzleSelection` / `PuzzleSelection.parse()` so malformed legacy
  values receive the solvable Math fallback.

### Preview Mode Flag
- `IS_PREVIEW = true` in AlarmService/Activity → safe testing mode
- Disables: loud sound, max-volume locks, back-button disable, full-screen overlay
- Enable via Intent extra `"IS_PREVIEW"` boolean when testing
- Always use preview mode for test fixtures; only real mode for instrumented tests when needed

---

## ⚠️ Important Constraints & Pitfalls

### Permissions & Manifest
- **Exact alarm permissions** are critical: use `SCHEDULE_EXACT_ALARM` through API 32 and `USE_EXACT_ALARM` on API 33+; scheduling uses `setAlarmClock()` and returns a typed failure when exact access is unavailable
- **Foreground service** requires `POST_NOTIFICATIONS` (Android 13+)
- **Full-screen intent** for AlarmDismissActivity requires `USE_FULL_SCREEN_INTENT`
- Device reboot, app replacement, time changes, and time-zone changes trigger
  BootReceiver to reschedule all enabled alarms; test these flows

### Foreground Service Lifecycle
- AlarmService must post notification within 10 seconds of start (Android 14+)
- Service is destroyed when AlarmDismissActivity calls `stopService()`; MediaPlayer is released in `onDestroy()`
- Volume lock runs on a 1-second timer loop; don't block the main thread

### Puzzle Engine Interfaces
Puzzle behavior is provided through small injectable interfaces:
- `MathPuzzleProvider`, `TypingPuzzleProvider`, `MemoryPuzzleProvider`, `ShakeSensorProvider`
- Each exposes only the generation/validation or sensor lifecycle operations its UI needs
- **No hard dependency on implementation**; inject for testing or alternative implementations

### Database Queries
- Room queries use Flow for reactive updates; always `collect()` in a coroutine
- `AlarmDao.getAllAlarms()` returns Flow for UI state; `getEnabledAlarms()` is a suspend snapshot query used for rescheduling
- **Avoid blocking calls** on the main thread; use `runTest` in unit tests

---

## 🧪 Testing Strategy

### Unit Tests (`src/test/`)
- **JUnit 4** + kotlinx-coroutines-test framework
- **Scope**: AlarmScheduler (pure logic, no Android context), puzzle engines and sensor selection, MainViewModel
- **Pattern**: Use `runTest` block for coroutine tests; assert state changes via Flow assertions
- No mocking framework; tests focus on direct logic verification

### Instrumented Tests (`src/androidTest/`)
- **AndroidJUnit4** runner with Compose UI testing framework
- **Scope**: Room DAO operations, exported-schema migration coverage, Compose screens (AlarmDismissScreen, AlarmListScreen)
- **Setup**: Room database created with `allowMainThreadQueries()` for simplicity
- **Context injection**: Use `ApplicationProvider.getApplicationContext()` for Room and system services
- **Schema changes**: Commit generated files under `app/schemas/` and extend `AlarmMigrationTest` across every supported upgrade path

### Test Fixtures
- Use `IS_PREVIEW = true` flag in test mode to avoid loud audio and back-button traps
- Mock puzzle providers with fixed answers for UI testing
- Test recurring alarm scheduling with fixed "now" times to avoid flakiness

---

## 🎨 Tech Stack & Notable Dependencies

| Component | Library | Version | Notes |
|-----------|---------|---------|-------|
| **UI Framework** | Jetpack Compose | Latest BOM (2026.03.01) | Material 3 theme, Glassmorphic design |
| **Language** | Kotlin | 2.4.10 | JVM target 17; new compiler required |
| **Persistence** | Room | 2.8.4 | Coroutine support, auto-migrations |
| **Async** | Coroutines + Flow | 1.11.0 | No Rx; pure Flow for reactivity |
| **Compilation** | Compose Compiler | Latest | Plugin alias via version catalog |
| **Code generation** | KSP | 2.3.10 | Room compiler; schemas exported by Room Gradle plugin |
| **Build Config** | Version Catalog | `gradle/libs.versions.toml` | Central dependency management |

**Notably absent**: No Hilt/Dagger DI framework, no networking, no analytics—kept minimal.

---

## 💡 Common Tasks & Patterns

### Adding a New Puzzle Type
1. Create `NewPuzzleProvider` interface + `NewPuzzleProviderImpl` in `puzzle/`
2. Add to `AlarmDismissScreen` UI state and logic
3. Add CSV string constant to config encoding doc
4. Create unit test in `src/test/puzzle/`; use fixed seed for reproducibility
5. Add instrumented UI test in `src/androidTest/ui/`

### Scheduling Recurring Alarms
- Use `AlarmTimeCalculator` with an injected `Clock` and `ZoneId` to compute the
  next trigger as an `Instant`
- Call `AlarmScheduler.schedule()` to register with AlarmManager
- Test with fixed clocks and explicit zones, including DST gaps and overlaps

### Testing AlarmService Behavior
- Create an Intent with `IS_PREVIEW = true` extra
- Launch via Intent; service runs in safe mode (no loud audio, no back-button trap)
- Assert foreground notification is posted within 10 seconds

### UI State Updates
- Observe `MainViewModel.alarms` and sheet StateFlows with `collectAsStateWithLifecycle()`
- Keep user-entered editor and puzzle progress in `rememberSaveable` state so configuration changes do not reset or skip tasks
- Query changes flow through `AlarmDao` → `RoomAlarmRepository` → MainViewModel → UI
- Test with in-memory Room database; inject via `ApplicationProvider.getApplicationContext()`

---

## 📚 Documentation & References

- **[docs/README.md](docs/README.md)** — Full architecture, feature overview, component descriptions
- **Tests** — See `src/test/` and `src/androidTest/` for patterns and examples
- **Manifest** — [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) — Permissions, receivers, services
- **Theme** — [ui/theme/](app/src/main/java/com/example/smartalarmer/ui/theme/) — Glassmorphic design tokens

---

## 🚀 Quick Start for AI Agents

1. **Understand flow**: Alarm fires → AlarmReceiver → AlarmService (notification + audio) → AlarmDismissActivity (puzzle UI)
2. **Know the data**: Persistence uses CSV, while application code uses `AlarmDays`, `PuzzleSelection`, and their enums
3. **Test approach**: Use `runTest` for unit tests, in-memory Room for instrumented, preview mode for UI safety
4. **Before committing**: Run `./gradlew build && ./gradlew test && ./gradlew connectedAndroidTest` (if emulator running)
5. **Review manifest** when modifying permissions, services, or receivers

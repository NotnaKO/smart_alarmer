# Smart Alarmer — AI Agent Guide

This Android alarm clock app forces wake-ups by requiring cognitive puzzles to be solved before the alarm can be silenced. Coded in Kotlin with Jetpack Compose, Room database, and a modular puzzle engine architecture.

---

## 🏗️ Architecture & Package Structure

**See [docs/README.md](docs/README.md)** for complete architecture diagram and feature overview.

**Key packages** (`app/src/main/java/com/example/smartalarmer/`):
- **`ui/main/`** — MainActivity, MainViewModel, Compose screens (alarm list, config sheet)
- **`ui/dismiss/`** — AlarmDismissActivity, AlarmDismissScreen, puzzle UI components
- **`ui/theme/`** — Glassmorphic dark theme styling
- **`data/`** — Room entities, DAO, database setup, coroutine-driven queries
- **`puzzle/`** — Abstract `PuzzleProvider` interfaces and 3 engine implementations
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
- `"MATH,TYPING,MEMORY"` = all three types
- `"MATH"` = only math puzzles
- Shuffle and limit in AlarmDismissScreen; order from config is ignored
- Use `Alarm.puzzleSelection` / `PuzzleSelection.parse()` so malformed legacy
  values receive the solvable Math fallback.

### Preview Mode Flag
- `IS_PREVIEW = true` in AlarmService/Activity → safe testing mode
- Disables: loud sound, max-volume locks, back-button disable, full-screen overlay
- Enable via PendingIntent extra `"is_preview"` boolean when testing
- Always use preview mode for test fixtures; only real mode for instrumented tests when needed

---

## ⚠️ Important Constraints & Pitfalls

### Permissions & Manifest
- **Exact alarm permission** (`SCHEDULE_EXACT_ALARM`) is critical; fallback to `setAndAllow...` on older APIs
- **Foreground service** requires `POST_NOTIFICATIONS` (Android 13+)
- **Full-screen intent** for AlarmDismissActivity requires `USE_FULL_SCREEN_INTENT`
- Device reboot triggers BootReceiver to reschedule all enabled alarms; test this flow

### Foreground Service Lifecycle
- AlarmService must post notification within 10 seconds of start (Android 14+)
- Service is destroyed when AlarmDismissActivity calls `stopService()`; MediaPlayer is released in `onDestroy()`
- Volume lock runs on a 1-second timer loop; don't block the main thread

### Puzzle Engine Interfaces
All puzzle engines implement the same interface pattern via DI:
- `MathPuzzleProvider`, `TypingPuzzleProvider`, `MemoryPuzzleProvider`
- Each provides `generatePuzzle()` and `isPuzzleSolved(answer)` methods
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
| **Language** | Kotlin | 2.0.21 | JVM target 17; new compiler required |
| **Persistence** | Room | 2.8.4 | Coroutine support, auto-migrations |
| **Navigation** | Navigation 3 | Latest | Fragment-free; composable-first |
| **Async** | Coroutines + Flow | 1.10.2 | No Rx; pure Flow for reactivity |
| **Compilation** | Compose Compiler | Latest | Plugin alias via version catalog |
| **Serialization** | Kotlin Serialization | Latest | Declared; not actively used yet |
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
- Use `AlarmScheduler.calculateNextTriggerTime()` to compute next trigger (pure logic)
- Call `AlarmScheduler.schedule()` to register with AlarmManager
- Test with fixed "now" times to avoid flakiness; use `runTest` block

### Testing AlarmService Behavior
- Create a PendingIntent with `is_preview = true` extra
- Launch via Intent; service runs in safe mode (no loud audio, no back-button trap)
- Assert foreground notification is posted within 10 seconds

### UI State Updates
- Observe `MainViewModel.alarms` and sheet StateFlows in Compose
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
2. **Know the data**: Days as CSV ints, puzzle types as CSV strings; no enums for encoding
3. **Test approach**: Use `runTest` for unit tests, in-memory Room for instrumented, preview mode for UI safety
4. **Before committing**: Run `./gradlew build && ./gradlew test && ./gradlew connectedAndroidTest` (if emulator running)
5. **Review manifest** when modifying permissions, services, or receivers

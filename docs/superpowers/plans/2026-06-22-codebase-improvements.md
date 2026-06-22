# Codebase Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean up package structures, simplify StateFlow usage, move Timer to coroutines, centralize theme colors, localize typing quotes, add save alarm triggers Toast, and fix shake preview spec documentation.

**Architecture:** Moving files to their respective packages, updating imports and manifests, replacing Java Timer with Service coroutines, and utilizing standard Android resource APIs for localized strings.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines, Room, Android API.

---

### Task 1: Reorganize Packages and Update Manifest/Imports

**Files:**
- Create and Move:
  - `app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt`
  - `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissActivity.kt`
  - `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt`
  - `app/src/main/java/com/example/smartalarmer/receiver/AlarmReceiver.kt`
  - `app/src/main/java/com/example/smartalarmer/receiver/BootReceiver.kt`
  - `app/src/main/java/com/example/smartalarmer/service/AlarmService.kt`
  - `app/src/main/java/com/example/smartalarmer/scheduler/AlarmScheduler.kt`
  - `app/src/main/java/com/example/smartalarmer/utils/DeviceUtils.kt`
  - `app/src/main/java/com/example/smartalarmer/ui/theme/Color.kt`
  - `app/src/main/java/com/example/smartalarmer/ui/theme/Theme.kt`
  - `app/src/main/java/com/example/smartalarmer/ui/theme/Type.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Delete old locations.

- [ ] **Step 1: Move classes to their destination directories**
  Run:
  ```bash
  mkdir -p app/src/main/java/com/example/smartalarmer/ui/dismiss
  mkdir -p app/src/main/java/com/example/smartalarmer/receiver
  mkdir -p app/src/main/java/com/example/smartalarmer/service
  mkdir -p app/src/main/java/com/example/smartalarmer/scheduler
  mkdir -p app/src/main/java/com/example/smartalarmer/utils
  mkdir -p app/src/main/java/com/example/smartalarmer/ui/theme
  
  git mv app/src/main/java/com/example/smartalarmer/MainActivity.kt app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt
  git mv app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissActivity.kt
  git mv app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt
  git mv app/src/main/java/com/example/smartalarmer/AlarmReceiver.kt app/src/main/java/com/example/smartalarmer/receiver/AlarmReceiver.kt
  git mv app/src/main/java/com/example/smartalarmer/BootReceiver.kt app/src/main/java/com/example/smartalarmer/receiver/BootReceiver.kt
  git mv app/src/main/java/com/example/smartalarmer/AlarmService.kt app/src/main/java/com/example/smartalarmer/service/AlarmService.kt
  git mv app/src/main/java/com/example/smartalarmer/AlarmScheduler.kt app/src/main/java/com/example/smartalarmer/scheduler/AlarmScheduler.kt
  git mv app/src/main/java/com/example/smartalarmer/DeviceUtils.kt app/src/main/java/com/example/smartalarmer/utils/DeviceUtils.kt
  
  git mv app/src/main/java/com/example/smartalarmer/theme/Color.kt app/src/main/java/com/example/smartalarmer/ui/theme/Color.kt
  git mv app/src/main/java/com/example/smartalarmer/theme/Theme.kt app/src/main/java/com/example/smartalarmer/ui/theme/Theme.kt
  git mv app/src/main/java/com/example/smartalarmer/theme/Type.kt app/src/main/java/com/example/smartalarmer/ui/theme/Type.kt
  ```

- [ ] **Step 2: Update AndroidManifest.xml package declarations**
  Modify: `app/src/main/AndroidManifest.xml`
  Update class paths:
  - `.MainActivity` -> `.ui.main.MainActivity`
  - `.AlarmReceiver` -> `.receiver.AlarmReceiver`
  - `.BootReceiver` -> `.receiver.BootReceiver`
  - `.AlarmService` -> `.service.AlarmService`
  - `.AlarmDismissActivity` -> `.ui.dismiss.AlarmDismissActivity`

- [ ] **Step 3: Fix packages and imports in moved classes**
  Update the `package` declaration at the top of each moved file and update all imports to target the correct new packages.

---

### Task 2: Simplify ViewModel StateFlow with stateIn

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt`

- [ ] **Step 1: Simplify `alarms` stream**
  Change the collection setup:
  ```kotlin
  val alarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms()
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = emptyList()
      )
  ```
  Ensure correct imports:
  `import kotlinx.coroutines.flow.stateIn` and `import kotlinx.coroutines.flow.SharingStarted`

---

### Task 3: Replace Java Timer with Coroutines in AlarmService

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/service/AlarmService.kt`

- [ ] **Step 1: Replace Timer with Coroutines**
  Use `serviceScope` with `delay` to lock volume/increase volume gently.
  ```kotlin
  private val serviceJob = SupervisorJob()
  private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
  ```
  In `onDestroy()`, cancel the scope:
  ```kotlin
  override fun onDestroy() {
      mediaPlayer?.stop()
      mediaPlayer?.release()
      serviceJob.cancel()
      super.onDestroy()
  }
  ```

---

### Task 4: Centralize Theme Colors

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt`

- [ ] **Step 1: Define customized color tokens in `Color.kt`**
  ```kotlin
  val IndigoPrimary = Color(0xFF6366F1)
  val DarkBgStart = Color(0xFF0F0C20)
  val DarkBgEnd = Color(0xFF15102A)
  val CardBgGlass = Color(0x0FFFFFFF)
  val CardBorderGlass = Color(0x1AFFFFFF)
  val BottomSheetBg = Color(0xFF1E1B3A)
  val BottomSheetDrag = Color(0x33FFFFFF)
  val GreenSuccess = Color(0xFF10B981)
  val OrangeWarning = Color(0xFFF59E0B)
  val RedError = Color(0xFFEF4444)
  val DarkGreyButton = Color(0xFF333333)
  ```

- [ ] **Step 2: Replace hexadecimal Color construction in layouts**
  Replace raw `Color(0xFF...)` references in layouts with imports from `com.example.smartalarmer.ui.theme`.

---

### Task 5: Move Hardcoded Quotes to string-array

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/example/smartalarmer/puzzle/PuzzleProviders.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/puzzle/TypingEngine.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt`
- Modify: `app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissScreenTest.kt`

- [ ] **Step 1: Add quotes to strings.xml**
  ```xml
  <string-array name="typing_quotes">
      <item>The early bird gets the worm.</item>
      <item>Waking up is the first step to success.</item>
      <item>No snooze allowed. Rise and shine!</item>
      <item>Make today count. Get out of bed.</item>
  </string-array>
  ```

- [ ] **Step 2: Update `TypingPuzzleProvider` interface & implementation**
  Update `getRandomQuote()` to take `quotes: List<String>`.
  ```kotlin
  interface TypingPuzzleProvider {
      fun getRandomQuote(quotes: List<String>): String
      fun isMatch(target: String, input: String): Boolean
  }
  ```

- [ ] **Step 3: Update `AlarmDismissScreen.kt` to load resources**
  Retrieve string-array inside Compose:
  ```kotlin
  val quotes = androidx.compose.ui.res.stringArrayResource(R.array.typing_quotes).toList()
  val targetQuote = remember { typingProvider.getRandomQuote(quotes) }
  ```

---

### Task 6: Show Toast with Trigger Duration upon Alarm Save

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt`

- [ ] **Step 1: Compute time difference and trigger a Toast**
  Inside `saveAlarm`:
  ```kotlin
  val nextTrigger = AlarmScheduler.calculateNextTriggerTime(newAlarm, java.util.Calendar.getInstance())
  val diffMs = nextTrigger.timeInMillis - System.currentTimeMillis()
  val hours = diffMs / (3600 * 1000)
  val minutes = (diffMs % (3600 * 1000)) / (60 * 1000)
  val timeText = if (hours > 0) "$hours hours and $minutes minutes" else "$minutes minutes"
  android.widget.Toast.makeText(context, "Alarm set for $timeText from now", android.widget.Toast.LENGTH_LONG).show()
  ```

---

### Task 7: Update Design Spec & Clean Up

**Files:**
- Modify: `docs/superpowers/specs/2026-06-20-shake-puzzle-and-ringtone-fallback-design.md`

- [ ] **Step 1: Clean up "Simulate Shake" preview references**
  Remove requirements for the UI simulated shake button in preview mode, documenting the instrumented testing approach instead.

# Lockscreen Alarm Screen Takeover and Typing Puzzle Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure the alarm screen takeover is allowed on modern Android versions (Android 13+) by checking and requesting notification, exact alarm, and full-screen intent permissions, keep the screen active using `FLAG_KEEP_SCREEN_ON`, and enable typing on the lockscreen by implementing a custom virtual keyboard for the Typing puzzle. Additionally, add a UI Automator-based lockscreen instrumented test to verify screen wake-up and Compose rendering under locked conditions.

**Architecture:**
- **Permission Onboarding:** Checks and requests `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and `USE_FULL_SCREEN_INTENT` on resume in `MainActivity` and displays a glassmorphic warning card at the top.
- **Activity and Notification Hardening:** Configures `AlarmDismissActivity` with `FLAG_KEEP_SCREEN_ON`. Set maximum priority on foreground notification channel and notification.
- **Custom Keyboard:** Employs a Compose-only `VirtualKeyboard` in `TypingPuzzleView` with QWERTY buttons (including letters, spaces, `.`, `!`, shift, backspace) and read-only text fields to bypass lockscreen input restrictions.
- **Instrumented Test:** Creates a test with UI Automator putting the device to sleep and launching the Activity, asserting the screen wakes up and UI is interactive.

**Tech Stack:** Jetpack Compose, Kotlin, Android SDK 24+, Android JUnit, UI Automator.

---

### Task 1: Add UI Automator Dependency and Gradle Configuration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Update Version Catalog**
  Add `uiautomator` to `gradle/libs.versions.toml` under `[versions]` and `[libraries]`.
  Target content in `gradle/libs.versions.toml`:
  ```toml
  [versions]
  ...
  room = "2.8.4"
  uiautomator = "2.3.0"

  [libraries]
  ...
  room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
  androidx-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "uiautomator" }
  ```

- [ ] **Step 2: Update App Build Gradle**
  Add the dependency `androidTestImplementation(libs.androidx.uiautomator)` to `app/build.gradle.kts` dependencies block.
  Target content in `app/build.gradle.kts`:
  ```kotlin
  dependencies {
      ...
      androidTestImplementation(libs.androidx.test.espresso.core)
      androidTestImplementation(libs.androidx-uiautomator)
      ...
  }
  ```

- [ ] **Step 3: Run gradle sync/build to verify dependencies resolve**
  Run: `./gradlew compileDebugAndroidTestSources`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit dependencies**
  ```bash
  git add gradle/libs.versions.toml app/build.gradle.kts
  git commit -m "chore: add UI Automator dependency for instrumented tests"
  ```

---

### Task 2: Implement Permissions Onboarding Banner in MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/MainActivity.kt`

- [ ] **Step 1: Add Permission Check & Onboarding Banner UI**
  Add reactive checks for `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and `USE_FULL_SCREEN_INTENT` to `MainActivity.kt`.
  Modify [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt#L44-L82) to add state tracking and render a card at the top of the alarm list when permissions are missing.

  Target implementation to insert before `Scaffold` or at the top of content:
  ```kotlin
  // Add these imports at the top
  import android.Manifest
  import android.app.AlarmManager
  import android.app.NotificationManager
  import android.content.Context
  import android.content.pm.PackageManager
  import android.net.Uri
  import android.os.Build
  import android.provider.Settings
  import androidx.activity.compose.rememberLauncherForActivityResult
  import androidx.activity.result.contract.ActivityResultContracts
  import androidx.core.content.ContextCompat
  ```

  And add the UI state and event observer inside `setContent { SmartAlarmerTheme { ... } }`:
  ```kotlin
  val context = LocalContext.current
  var hasNotificationPermission by remember { mutableStateOf(true) }
  var hasExactAlarmPermission by remember { mutableStateOf(true) }
  var hasFullScreenIntentPermission by remember { mutableStateOf(true) }

  val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { hasNotificationPermission = it }
  )

  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
      val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
          if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
              hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.POST_NOTIFICATIONS
                  ) == PackageManager.PERMISSION_GRANTED
              } else {
                  true
              }

              hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                  val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                  alarmManager.canScheduleExactAlarms()
              } else {
                  true
              }

              hasFullScreenIntentPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                  val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                  notificationManager.canUseFullScreenIntent()
              } else {
                  true
              }
          }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
          lifecycleOwner.lifecycle.removeObserver(observer)
      }
  }
  ```

  Display the banner at the top of the `Column` (above the "Smart Alarmer" text or just below it) if any permission is false:
  ```kotlin
  if (!hasNotificationPermission || !hasExactAlarmPermission || !hasFullScreenIntentPermission) {
      Card(
          modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
              .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
          colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
          shape = RoundedCornerShape(16.dp)
      ) {
          Column(modifier = Modifier.padding(16.dp)) {
              Text(
                  text = "Permissions Required",
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  fontSize = 16.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                  text = "Please enable all permissions below to ensure alarms wake up your device and display properly over the lock screen.",
                  color = Color.LightGray,
                  fontSize = 13.sp
              )
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth()
              ) {
                  if (!hasNotificationPermission) {
                      Button(
                          onClick = {
                              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                  requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                              }
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                          shape = RoundedCornerShape(8.dp)
                      ) {
                          Text("Allow Notifications", fontSize = 11.sp)
                      }
                  }
                  if (!hasExactAlarmPermission) {
                      Button(
                          onClick = {
                              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                  val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                      data = Uri.parse("package:${context.packageName}")
                                  }
                                  context.startActivity(intent)
                              }
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                          shape = RoundedCornerShape(8.dp)
                      ) {
                          Text("Allow Alarms", fontSize = 11.sp)
                      }
                  }
                  if (!hasFullScreenIntentPermission) {
                      Button(
                          onClick = {
                              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                  val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                      data = Uri.parse("package:${context.packageName}")
                                  }
                                  context.startActivity(intent)
                              }
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                          shape = RoundedCornerShape(8.dp)
                      ) {
                          Text("Allow Lockscreen Display", fontSize = 11.sp)
                      }
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 2: Verify compile success**
  Run: `./gradlew compileDebugKotlin`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit MainActivity modifications**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/MainActivity.kt
  git commit -m "feat: implement permission check and warning banner UI in MainActivity"
  ```

---

### Task 3: Harden AlarmDismissActivity and AlarmService Lock Screen behavior

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt`
- Modify: `app/src/main/java/com/example/smartalarmer/AlarmService.kt`

- [ ] **Step 1: Keep Screen On in AlarmDismissActivity**
  Add `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` unconditionally to `AlarmDismissActivity.kt` in `onCreate`.
  Modify [AlarmDismissActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt#L22-L35):
  ```kotlin
          if (!isPreview) {
              // Show on lock screen
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                  setShowWhenLocked(true)
                  setTurnScreenOn(true)
              } else {
                  @Suppress("DEPRECATION")
                  window.addFlags(
                      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                              or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                  )
              }
              window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          }
  ```

- [ ] **Step 2: Increase Notification Priority in AlarmService**
  Modify the `NotificationCompat.Builder` in [AlarmService.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmService.kt#L43-L50) to use max priority and high importance:
  ```kotlin
          val notification = NotificationCompat.Builder(this, channelId)
              .setContentTitle("WAKE UP NOW!")
              .setContentText("Complete tasks to silence the alarm")
              .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
              .setPriority(NotificationCompat.PRIORITY_MAX) // Increased from HIGH to MAX
              .setCategory(NotificationCompat.CATEGORY_ALARM)
              .setFullScreenIntent(fullScreenPendingIntent, true)
              .build()
  ```

- [ ] **Step 3: Verify compile success**
  Run: `./gradlew compileDebugKotlin`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit hardening changes**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt app/src/main/java/com/example/smartalarmer/AlarmService.kt
  git commit -m "feat: keep screen on during alarm dismiss and set notification priority to MAX"
  ```

---

### Task 4: Implement Custom Virtual Keyboard in TypingPuzzleView

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt`

- [ ] **Step 1: Implement `VirtualKeyboard` and Integrate with `TypingPuzzleView`**
  Modify [AlarmDismissScreen.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt#L159-L198):
  - Set `readOnly = true` on the `TextField`.
  - Display the `VirtualKeyboard` below the `TextField` to allow typing without system soft keyboard.

  Code Content to add to `AlarmDismissScreen.kt`:
  ```kotlin
  @Composable
  fun VirtualKeyboard(
      onKeyClick: (Char) -> Unit,
      onBackspace: () -> Unit,
      modifier: Modifier = Modifier
  ) {
      var isShifted by remember { mutableStateOf(false) }

      val rows = remember(isShifted) {
          listOf(
              listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
              listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
              listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '!')
          ).map { row ->
              if (isShifted) {
                  row.map { if (it in 'a'..'z') it.uppercaseChar() else it }
              } else {
                  row
              }
          }
      }

      Column(
          modifier = modifier
              .fillMaxWidth()
              .background(Color(0xFF1E1B3A), RoundedCornerShape(16.dp))
              .padding(8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
          // Rows 1 & 2
          rows.take(2).forEach { row ->
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
              ) {
                  row.forEach { char ->
                      KeyButton(text = char.toString(), onClick = { onKeyClick(char) }, modifier = Modifier.weight(1f))
                  }
              }
          }

          // Row 3 (Shift, letters & punctuation, Backspace)
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
              verticalAlignment = Alignment.CenterVertically
          ) {
              KeyButton(
                  text = "⇧",
                  onClick = { isShifted = !isShifted },
                  containerColor = if (isShifted) Color(0xFF6366F1) else Color(0x33FFFFFF),
                  modifier = Modifier.weight(1.5f)
              )

              rows[2].forEach { char ->
                  KeyButton(text = char.toString(), onClick = { onKeyClick(char) }, modifier = Modifier.weight(1f))
              }

              KeyButton(
                  text = "⌫",
                  onClick = onBackspace,
                  containerColor = Color(0x33FFFFFF),
                  modifier = Modifier.weight(1.5f)
              )
          }

          // Row 4 (Space)
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
          ) {
              KeyButton(
                  text = "Space",
                  onClick = { onKeyClick(' ') },
                  modifier = Modifier.fillMaxWidth()
              )
          }
      }
  }

  @Composable
  fun KeyButton(
      text: String,
      onClick: () -> Unit,
      modifier: Modifier = Modifier,
      containerColor: Color = Color(0x1AFFFFFF),
      contentColor: Color = Color.White
  ) {
      Button(
          onClick = onClick,
          modifier = modifier.height(44.dp),
          colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(0.dp)
      ) {
          Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
      }
  }
  ```

  And update `TypingPuzzleView`:
  ```kotlin
  @Composable
  fun TypingPuzzleView(
      onComplete: () -> Unit,
      typingProvider: TypingPuzzleProvider = TypingEngine,
  ) {
      val targetQuote = remember { typingProvider.getRandomQuote() }
      LaunchedEffect(targetQuote) {
          android.util.Log.d("TEST_DEBUG", "Typing Quote: $targetQuote")
      }
      var input by remember { mutableStateOf("") }
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          Text(text = "Type this exact sentence:", color = Color.Gray, fontSize = 14.sp)
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = targetQuote, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.height(24.dp))
          
          TextField(
              value = input,
              onValueChange = { /* readOnly handles input */ },
              readOnly = true,
              modifier = Modifier.fillMaxWidth(),
              colors = TextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedContainerColor = Color(0xFF222222),
                  unfocusedContainerColor = Color(0xFF222222)
              )
          )
          Spacer(modifier = Modifier.height(16.dp))
          
          VirtualKeyboard(
              onKeyClick = { input += it },
              onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) }
          )
          
          Spacer(modifier = Modifier.height(16.dp))
          Button(
              onClick = {
                  if (typingProvider.isMatch(targetQuote, input)) {
                      onComplete()
                  }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
          ) {
              Text("Submit")
          }
      }
  }
  ```

- [ ] **Step 2: Run existing tests to verify compiling and logic**
  Run: `./gradlew test`
  Expected: BUILD SUCCESSFUL (All unit tests pass)

- [ ] **Step 3: Commit typing keyboard changes**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt
  git commit -m "feat: implement in-app VirtualKeyboard for TypingPuzzleView"
  ```

---

### Task 5: Create Instrumented Lockscreen Takeover Test

**Files:**
- Create: `app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissActivityLockScreenTest.kt`

- [ ] **Step 1: Write Lockscreen Takeover test**
  Create the test file at `app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissActivityLockScreenTest.kt` using `UiDevice` to put the device to sleep and launch `AlarmDismissActivity` over the lockscreen.

  Code Content:
  ```kotlin
  package com.example.smartalarmer.ui

  import android.content.Context
  import android.content.Intent
  import androidx.compose.ui.test.junit4.createEmptyComposeRule
  import androidx.compose.ui.test.onNodeWithText
  import androidx.test.core.app.ActivityScenario
  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import androidx.test.platform.app.InstrumentationRegistry
  import androidx.test.uiautomator.UiDevice
  import com.example.smartalarmer.AlarmDismissActivity
  import org.junit.Assert.assertTrue
  import org.junit.Rule
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class AlarmDismissActivityLockScreenTest {

      @get:Rule
      val composeTestRule = createEmptyComposeRule()

      @Test
      fun alarmDismissActivity_wakesDeviceAndShowsOverLockScreen() {
          val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
          
          // 1. Put device to sleep (lock screen)
          device.sleep()
          Thread.sleep(1000)
          
          // 2. Launch AlarmDismissActivity
          val context = ApplicationProvider.getApplicationContext<Context>()
          val intent = Intent(context, AlarmDismissActivity::class.java).apply {
              putExtra("PUZZLES_LIST", "MATH")
              putExtra("PUZZLE_COUNT", 1)
              putExtra("IS_PREVIEW", false) // Runs in real lockscreen mode
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
          }
          
          ActivityScenario.launch<AlarmDismissActivity>(intent).use {
              Thread.sleep(2000)
              
              // 3. Verify screen is back ON
              assertTrue("Screen should be turned ON by activity", device.isScreenOn)
              
              // 4. Verify Compose puzzle UI content is visible
              composeTestRule.onNodeWithText("Task 1 of 1").assertExists()
          }
          
          // 5. Clean up by waking device
          device.wakeUp()
      }
  }
  ```

- [ ] **Step 2: Run instrumented tests on emulator**
  Run: `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.smartalarmer.ui.AlarmDismissActivityLockScreenTest`
  Expected: PASS

- [ ] **Step 3: Commit instrumented test**
  ```bash
  git add app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissActivityLockScreenTest.kt
  git commit -m "test: add instrumented test for lockscreen activity wake-up and Compose rendering"
  ```

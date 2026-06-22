# Reliable Background & Lockscreen Alarms Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix issues where alarms sometimes fail to trigger in deep sleep (Option B) or fail to display the puzzle screen over the lock screen (Option A) by implementing modern PendingIntent options, OEM-specific detection (Xiaomi/MIUI), and battery optimization warning card guides.

**Architecture:** We will create a `DeviceUtils` helper to detect Xiaomi devices and check battery exemption status. We will add `ActivityOptions` settings for background activity start on Android 14+, declare the request permission in the manifest, and display a glassmorphic warning card in `MainActivity`.

**Tech Stack:** Kotlin, Jetpack Compose, Android SDK (API 34/35/36 compatibility)

---

### Task 1: Add Battery Optimization Intent Permission in Manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Declare REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**
  Add the uses-permission tag to the manifest just below the `RECEIVE_BOOT_COMPLETED` permission.

  ```xml
      <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
      <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
  ```

- [ ] **Step 2: Verify compile status**
  Run: `./gradlew assembleDebug`
  Expected: Successful build.

- [ ] **Step 3: Commit**
  ```bash
  git add app/src/main/AndroidManifest.xml
  git commit -m "manifest: add REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission"
  ```

---

### Task 2: Create DeviceUtils Helper and Test

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/DeviceUtils.kt`
- Create: `app/src/test/java/com/example/smartalarmer/DeviceUtilsTest.kt`

- [ ] **Step 1: Implement DeviceUtils.kt**
  Create the utility object to detect MIUI/Xiaomi and build intents for system settings.

  ```kotlin
  package com.example.smartalarmer

  import android.content.Context
  import android.content.Intent
  import android.net.Uri
  import android.os.Build
  import android.os.PowerManager
  import android.provider.Settings
  import java.lang.reflect.Method

  object DeviceUtils {

      fun isXiaomi(): Boolean {
          val manufacturer = Build.MANUFACTURER ?: ""
          val brand = Build.BRAND ?: ""
          return manufacturer.equals("Xiaomi", ignoreCase = true) || 
                 brand.equals("Xiaomi", ignoreCase = true) || 
                 isMiUi()
      }

      fun isMiUi(): Boolean {
          val name = getSystemProperty("ro.miui.ui.version.name")
          val code = getSystemProperty("ro.miui.ui.version.code")
          return !name.isNullOrBlank() || !code.isNullOrBlank()
      }

      fun isIgnoringBatteryOptimizations(context: Context): Boolean {
          val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
          return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
      }

      fun getMiuiPermissionIntent(context: Context): Intent {
          return try {
              Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                  setClassName(
                      "com.miui.securitycenter",
                      "com.miui.permcenter.permissions.PermissionsEditorActivity"
                  )
                  putExtra("extra_pkgname", context.packageName)
              }
          } catch (e: Exception) {
              getStandardAppInfoIntent(context)
          }
      }

      fun getBatteryOptimizationIntent(context: Context): Intent {
          return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
              data = Uri.parse("package:${context.packageName}")
          }
      }

      fun getStandardAppInfoIntent(context: Context): Intent {
          return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.parse("package:${context.packageName}")
          }
      }

      private fun getSystemProperty(propName: String): String? {
          return try {
              val systemProperties = Class.forName("android.os.SystemProperties")
              val getMethod: Method = systemProperties.getMethod("get", String::class.java)
              val value = getMethod.invoke(systemProperties, propName) as? String
              if (value.isNullOrBlank()) null else value
          } catch (e: Exception) {
              null
          }
      }
  }
  ```

- [ ] **Step 2: Create local JVM unit test**
  Write a test ensuring the reflection calls handle class-not-found scenarios gracefully without crashing.

  ```kotlin
  package com.example.smartalarmer

  import org.junit.Assert.assertFalse
  import org.junit.Test

  class DeviceUtilsTest {
      @Test
      fun testIsMiUiOnNonXiaomiDvm() {
          // In standard host JVM environment, SystemProperties does not exist
          // and should safely return false.
          assertFalse(DeviceUtils.isMiUi())
      }
  }
  ```

- [ ] **Step 3: Run the unit test**
  Run: `./gradlew testDebugUnitTest --tests "com.example.smartalarmer.DeviceUtilsTest"`
  Expected: PASS

- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/DeviceUtils.kt app/src/test/java/com/example/smartalarmer/DeviceUtilsTest.kt
  git commit -m "feat: add DeviceUtils and local JVM tests"
  ```

---

### Task 3: Update PendingIntent Options in AlarmService

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/AlarmService.kt`

- [ ] **Step 1: Set Background Activity Start Mode on Android 14+**
  Modify lines 39-41 of `AlarmService.kt` to pass `ActivityOptions` to the `PendingIntent`.

  Target:
  ```kotlin
          val fullScreenPendingIntent = PendingIntent.getActivity(
              this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
  ```

  Replacement:
  ```kotlin
          val options = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
              android.app.ActivityOptions.makeBasic().apply {
                  setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
              }
          } else {
              null
          }
          val fullScreenPendingIntent = PendingIntent.getActivity(
              this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options?.toBundle()
          )
  ```

- [ ] **Step 2: Run existing unit tests**
  Run: `./gradlew testDebugUnitTest`
  Expected: All unit tests pass.

- [ ] **Step 3: Commit**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/AlarmService.kt
  git commit -m "feat: configure setPendingIntentBackgroundActivityStartMode for full-screen alarm launch"
  ```

---

### Task 4: Integrate Diagnostic Card in MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/MainActivity.kt`

- [ ] **Step 1: Modify lifecycle state observer to check Battery Optimizations**
  Add state and check logic.
  
  Target lines 62-64:
  ```kotlin
                  var hasNotificationPermission by remember { mutableStateOf(true) }
                  var hasExactAlarmPermission by remember { mutableStateOf(true) }
                  var hasFullScreenIntentPermission by remember { mutableStateOf(true) }
  ```

  Replacement:
  ```kotlin
                  var hasNotificationPermission by remember { mutableStateOf(true) }
                  var hasExactAlarmPermission by remember { mutableStateOf(true) }
                  var hasFullScreenIntentPermission by remember { mutableStateOf(true) }
                  var isIgnoringBatteryOptimizations by remember { mutableStateOf(true) }
                  val isXiaomiDevice = remember { DeviceUtils.isXiaomi() }
  ```

  Target lines 96-98:
  ```kotlin
                              } else {
                                  true
                              }
                          }
  ```

  Replacement:
  ```kotlin
                              } else {
                                  true
                              }
                              
                              isIgnoringBatteryOptimizations = DeviceUtils.isIgnoringBatteryOptimizations(context)
                          }
  ```

- [ ] **Step 2: Insert the Amber warning card in UI Layout**
  Insert the warning card immediately before the main "Permissions Required" card (around line 129).

  Target:
  ```kotlin
                          Column(modifier = Modifier.fillMaxSize()) {
                              if (!hasNotificationPermission || !hasExactAlarmPermission || !hasFullScreenIntentPermission) {
  ```

  Replacement:
  ```kotlin
                          Column(modifier = Modifier.fillMaxSize()) {
                              if (!isIgnoringBatteryOptimizations || isXiaomiDevice) {
                                  Card(
                                      modifier = Modifier
                                          .fillMaxWidth()
                                          .padding(bottom = 16.dp)
                                          .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                      colors = CardDefaults.cardColors(containerColor = Color(0x22F59E0B)),
                                      shape = RoundedCornerShape(16.dp)
                                  ) {
                                      Column(modifier = Modifier.padding(16.dp)) {
                                          Text(
                                              text = "Background Execution Settings",
                                              fontWeight = FontWeight.Bold,
                                              color = Color.White,
                                              fontSize = 16.sp
                                          )
                                          Spacer(modifier = Modifier.height(4.dp))
                                          Text(
                                              text = "To ensure alarms trigger reliably in deep sleep and display over the lockscreen, please verify background settings:",
                                              color = Color.LightGray,
                                              fontSize = 13.sp
                                          )
                                          Spacer(modifier = Modifier.height(12.dp))
                                          Row(
                                              horizontalArrangement = Arrangement.spacedBy(8.dp),
                                              modifier = Modifier.fillMaxWidth()
                                          ) {
                                              if (!isIgnoringBatteryOptimizations) {
                                                  Button(
                                                      onClick = {
                                                          val intent = DeviceUtils.getBatteryOptimizationIntent(context)
                                                          try {
                                                              context.startActivity(intent)
                                                          } catch (e: Exception) {
                                                              // Fallback to general settings if action cannot be started
                                                              val fallback = DeviceUtils.getStandardAppInfoIntent(context)
                                                              context.startActivity(fallback)
                                                          }
                                                      },
                                                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                                      shape = RoundedCornerShape(8.dp),
                                                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                  ) {
                                                      Text("Disable Battery Limits", fontSize = 11.sp, color = Color.Black)
                                                  }
                                              }
                                              if (isXiaomiDevice) {
                                                  Button(
                                                      onClick = {
                                                          val intent = DeviceUtils.getMiuiPermissionIntent(context)
                                                          try {
                                                              context.startActivity(intent)
                                                          } catch (e: Exception) {
                                                              val fallback = DeviceUtils.getStandardAppInfoIntent(context)
                                                              context.startActivity(fallback)
                                                          }
                                                      },
                                                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                                      shape = RoundedCornerShape(8.dp),
                                                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                  ) {
                                                      Text("Xiaomi Settings", fontSize = 11.sp, color = Color.Black)
                                                  }
                                              }
                                          }
                                      }
                                  }
                              }

                              if (!hasNotificationPermission || !hasExactAlarmPermission || !hasFullScreenIntentPermission) {
  ```

- [ ] **Step 3: Run full verification build**
  Run: `./gradlew build`
  Expected: Build succeeds, all unit tests pass, and lint checks complete successfully.

- [ ] **Step 4: Commit**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/MainActivity.kt
  git commit -m "ui: show warning card for battery optimizations and Xiaomi settings"
  ```

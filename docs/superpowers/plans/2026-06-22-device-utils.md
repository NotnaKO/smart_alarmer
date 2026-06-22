# DeviceUtils Helper and Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a DeviceUtils utility object to check for battery optimization status and MIUI/Xiaomi detection, accompanied by local JVM unit tests.

**Architecture:** A static helper object `DeviceUtils` using reflection to query `android.os.SystemProperties` without direct API dependency on host JVMs. This allows safe fallback to `false` when properties aren't available on non-Xiaomi devices or host JVMs.

**Tech Stack:** Kotlin, JUnit 4, Gradle.

---

### Task 1: Create Stub and Failing Test (RED)

**Files:**
- Create: [DeviceUtils.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/DeviceUtils.kt)
- Create: [DeviceUtilsTest.kt](file:///home/notnako/smart_alarmer/app/src/test/java/com/example/smartalarmer/DeviceUtilsTest.kt)

- [ ] **Step 1: Create stub implementation of DeviceUtils.kt**
  We define a stub that returns `true` for `isMiUi()` so that we can verify the test actually fails.

  ```kotlin
  package com.example.smartalarmer

  import android.content.Context
  import android.content.Intent

  object DeviceUtils {
      fun isMiUi(): Boolean {
          return true
      }
  }
  ```

- [ ] **Step 2: Create DeviceUtilsTest.kt**
  Write a test asserting that `isMiUi()` returns `false` on a non-Xiaomi host JVM environment.

  ```kotlin
  package com.example.smartalarmer

  import org.junit.Assert.assertFalse
  import org.junit.Test

  class DeviceUtilsTest {
      @Test
      fun testIsMiUiOnNonXiaomiDvm() {
          assertFalse(DeviceUtils.isMiUi())
      }
  }
  ```

- [ ] **Step 3: Run the unit test to verify it fails**
  Run: `./gradlew testDebugUnitTest --tests "com.example.smartalarmer.DeviceUtilsTest"`
  Expected: FAIL (assertion error: expected false but was true)

---

### Task 2: Implement Real DeviceUtils and Pass Test (GREEN)

**Files:**
- Modify: [DeviceUtils.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/DeviceUtils.kt)

- [ ] **Step 1: Write full implementation in DeviceUtils.kt**
  Replace the stub with the full implementation:

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

- [ ] **Step 2: Run the test to verify it passes**
  Run: `./gradlew testDebugUnitTest --tests "com.example.smartalarmer.DeviceUtilsTest"`
  Expected: PASS

---

### Task 3: Commit and Verification

- [ ] **Step 1: Stage and commit files**
  Stage the new helper and test files.
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/DeviceUtils.kt app/src/test/java/com/example/smartalarmer/DeviceUtilsTest.kt
  git commit -m "feat: add DeviceUtils and local JVM tests"
  ```

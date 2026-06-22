# Design Spec: Reliable Lockscreen Alarms & Background Execution

This design spec outlines changes to solve lockscreen alarm issues. On newer Android versions (14+) and aggressive OEM skins (especially Xiaomi MIUI/HyperOS), background activities and alarm scheduling can be suppressed.

---

## 🎯 Objectives
1. **Prevent Lockscreen Blocks (Option A):** Ensure that the alarm dismiss screen launches correctly when the device is locked.
2. **Prevent Missed Alarms (Option B):** Ensure that the alarm triggers reliably in deep sleep (Doze mode) across all OEMs.
3. **User Education:** Dynamically detect device constraints and guide the user to configure system settings.

---

## 🛠️ Architecture & Changes

### 1. Android 14+ FSI/BAL PendingIntent Updates
When scheduling the alarm, we configure the `PendingIntent` that triggers `AlarmDismissActivity` with the correct `ActivityOptions` to explicitly allow background activity starts.

- **Target File:** [AlarmService.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmService.kt)
- **Action:** Create `ActivityOptions` with `setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)` on Android 14 (API 34) and higher, and pass it to `PendingIntent.getActivity()`.

### 2. Device utility helper (`DeviceUtils.kt`)
We introduce a helper to detect system-level details and generate intents for system settings.

- **New File:** [DeviceUtils.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/DeviceUtils.kt)
- **Functions:**
  - `isXiaomi()`: Checks manufacturer info.
  - `isIgnoringBatteryOptimizations(context)`: Checks if the app is already exempted from battery saver constraints.
  - `getMiuiPermissionIntent(context)`: Launches MIUI permission editor fallback to App Info.
  - `getBatteryOptimizationIntent(context)`: Launches request dialog to disable battery optimization.

### 3. MainActivity Warning Card
We add dynamic checks inside `MainActivity`'s lifecycle resume loop and show a glassmorphic amber warning card if settings require action.

- **Target File:** [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt)
- **UI Element:** Displays a card detailing battery optimization issues (for all devices) and custom MIUI/HyperOS settings (for Xiaomi).

---

## 📋 Manifest Configuration
We declare the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission in `AndroidManifest.xml`.

- **Target File:** [AndroidManifest.xml](file:///home/notnako/smart_alarmer/app/src/main/AndroidManifest.xml)
- **Permission:** `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />`

---

## 🧪 Testing Strategy
1. **Unit Tests:** Verify `DeviceUtils.isXiaomi()` behavior with mocked model/manufacturer properties where possible.
2. **Manual verification:** Run `Test Alarm` from the MainActivity with the screen locked, checking if the alarm fires and activity starts properly.

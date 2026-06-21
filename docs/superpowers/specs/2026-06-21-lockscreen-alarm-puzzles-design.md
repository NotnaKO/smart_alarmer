# Spec: Lockscreen Alarm Screen Takeover and Typing Puzzle Hardening

This specification addresses the issue where the alarm dismiss activity is blocked from launching over the lock screen on modern Android devices (Android 13+), and secure lock screens block the soft keyboard for the Typing puzzle.

---

## 🏗️ Technical Architecture & Design

### 1. Permissions Onboarding UI
To ensure the foreground service is allowed to post notifications and display the full-screen intent activity when the device is locked, we will implement a runtime permission checker.

* **Target Permissions:**
  * `POST_NOTIFICATIONS` (Android 13+ / API 33) - Needed to show foreground service notifications.
  * `SCHEDULE_EXACT_ALARM` (Android 12+ / API 31) - Needed to trigger exact alarms at exact times.
  * `USE_FULL_SCREEN_INTENT` (Android 14+ / API 34) - Needed to launch the activity over the lockscreen.
* **Onboarding Banner:** A glassmorphic banner shown at the top of the main screen in `MainActivity.kt` if any of the above permissions are missing, prompting the user with an actionable button to grant it.

### 2. Lock Screen Activity & Notification Hardening
* **Screen Wake & Keep On:** Call `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` in `AlarmDismissActivity`'s `onCreate()` to prevent the screen from dimming/turning off while the user solves the puzzles.
* **Notification Priority:** Set the priority of the notification in `AlarmService` to `PRIORITY_MAX` and set the category to `CATEGORY_ALARM`.

### 3. Custom Virtual QWERTY Keyboard
To bypass OS-level restrictions that disable the system soft keyboard on secure lockscreens, we will implement an in-app virtual keyboard for the Typing puzzle.
* **ReadOnly TextField:** Set `readOnly = true` on the `TextField` in `TypingPuzzleView`.
* **Custom Layout:** Display a grid of standard QWERTY characters, a spacebar, backspace, shift toggle, and submit button.
* **Input Mutation:** Modifies the input string directly from Compose button click handlers.

---

## 📋 Proposed File Changes

### [MODIFY] [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt)
* Add permission tracking state (`showNotificationPermissionWarning`, `showExactAlarmWarning`, `showFullScreenIntentWarning`).
* Request permissions using standard Activity contracts or settings redirects.
* Display the permission onboarding card if permissions are missing.

### [MODIFY] [AlarmDismissActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt)
* Keep screen on using `FLAG_KEEP_SCREEN_ON` on the Window.
* Harden `setShowWhenLocked` and `setTurnScreenOn` lifecycle setup.

### [MODIFY] [AlarmService.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmService.kt)
* Upgrade notification priority to `PRIORITY_MAX`.

### [MODIFY] [AlarmDismissScreen.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt)
* Implement `VirtualKeyboard` composable.
* Update `TypingPuzzleView` to use `readOnly = true` on `TextField` and display `VirtualKeyboard` below it.

---

## 🧪 Verification Plan

### Automated Tests
* Run existing tests: `./gradlew test` and `./gradlew connectedAndroidTest` to ensure no regressions.
* Add unit tests verifying `TypingPuzzleView` still calls `onComplete` upon correct input typed via virtual keyboard.

### Manual Verification
* Run the app on the emulator.
* Verify permission warnings appear when permissions are missing.
* Grant permissions and ensure warning banners disappear.
* Trigger a test alarm in preview mode and verify the Typing puzzle works and can be solved using the new custom virtual keyboard.

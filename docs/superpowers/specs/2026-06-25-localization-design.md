# Localization & Keyboard Layout Design Spec

This specification outlines the strategy and implementation details for localizing the **Smart Alarmer** Android application into Spanish (`es`), German (`de`), and Russian (`ru`). It also details the design of a dynamic, localized virtual keyboard layout for the typing puzzle to match each user's language environment.

---

## 1. Objectives

1.  **String Extraction**: Centralize all user-facing strings (static labels, buttons, error cards, and dialog texts) from `MainActivity.kt`, `AlarmDismissScreen.kt`, and `AlarmService.kt` into Android resource files.
2.  **Resource Translation**: Provide translation files for Spanish (`es`), German (`de`), and Russian (`ru`) locales.
3.  **Correct Pluralization**: Implement localized plural formats for the alarm-setting toast using Android's `<plurals>` resources.
4.  **Dynamic Keyboard Layouts**: Adjust the in-app virtual keyboard in the typing puzzle to match QWERTY (default/Spanish with `ñ`), QWERTZ (German with `ä`, `ö`, `ü`, `ß`), and ЙЦУКЕН (Russian Cyrillic).
5.  **Robust Matching**: Implement accent-insensitive and punctuation-tolerant matching in the typing engine validation to ensure a smooth, accessible user experience.

---

## 2. Component Design & Changes

### A. Android Resource Layout (`app/src/main/res/`)

The following files will contain all localized resources:

1.  `app/src/main/res/values/strings.xml` (English / Default)
2.  `app/src/main/res/values-es/strings.xml` (Spanish)
3.  `app/src/main/res/values-de/strings.xml` (German)
4.  `app/src/main/res/values-ru/strings.xml` (Russian)

#### String Identifiers & Base Values

```xml
<resources>
    <!-- Application Name -->
    <string name="app_name">Smart Alarmer</string>

    <!-- Main Screen Settings / Errors -->
    <string name="bg_execution_settings">Background Execution Settings</string>
    <string name="bg_execution_desc">To ensure alarms trigger reliably in deep sleep and display over the lockscreen, please verify background settings:</string>
    <string name="disable_battery_limits">Disable Battery Limits</string>
    <string name="xiaomi_settings">Xiaomi Settings</string>
    <string name="dismiss">Dismiss</string>
    <string name="permissions_required">Permissions Required</string>
    <string name="permissions_desc">Please enable all permissions below to ensure alarms wake up your device and display properly over the lock screen.</string>
    <string name="allow_notifications">Allow Notifications</string>
    <string name="allow_alarms">Allow Alarms</string>
    <string name="allow_lockscreen">Allow Lockscreen Display</string>
    <string name="no_alarms_scheduled">No alarms scheduled.\nTap + to add an alarm.</string>
    <string name="test_btn">Test</string>
    <string name="delete_alarm_desc">Delete Alarm</string>
    <string name="add_alarm_desc">Add Alarm</string>

    <!-- Alarm Edit Sheet -->
    <string name="new_alarm">New Alarm</string>
    <string name="edit_alarm">Edit Alarm</string>
    <string name="time_label">Time</string>
    <string name="repeat_days_label">Repeat Days</string>
    <string name="dismiss_puzzles_label">Dismiss Puzzles</string>
    <string name="puzzles_required">Puzzles Required</string>
    <string name="gradual_volume">Gradual Volume</string>
    <string name="gradual_volume_desc">Volume ramps up over 60 seconds</string>
    <string name="cancel">Cancel</string>
    <string name="save">Save</string>

    <!-- Day Summary Labels -->
    <string name="one_time">One-time</string>
    <string name="every_day">Every day</string>
    <string name="weekdays">Weekdays</string>
    <string name="weekends">Weekends</string>

    <!-- Day Name Abbreviations (Single Letter) -->
    <string name="day_m">M</string>
    <string name="day_t">T</string>
    <string name="day_w">W</string>
    <string name="day_th">T</string>
    <string name="day_f">F</string>
    <string name="day_sa">S</string>
    <string name="day_su">S</string>

    <!-- Day Names Short (3 Letters) -->
    <string name="day_mon">Mon</string>
    <string name="day_tue">Tue</string>
    <string name="day_wed">Wed</string>
    <string name="day_thu">Thu</string>
    <string name="day_fri">Fri</string>
    <string name="day_sat">Sat</string>
    <string name="day_sun">Sun</string>

    <!-- Puzzle Names -->
    <string name="puzzle_math">Math</string>
    <string name="puzzle_memory">Memory</string>
    <string name="puzzle_typing">Typing</string>
    <string name="puzzle_shake">Shake</string>

    <!-- Pluralized Toast Resources -->
    <plurals name="hours_plural">
        <item quantity="one">%1$d hour</item>
        <item quantity="other">%1$d hours</item>
    </plurals>
    <plurals name="minutes_plural">
        <item quantity="one">%1$d minute</item>
        <item quantity="other">%1$d minutes</item>
    </plurals>
    <string name="alarm_set_toast">Alarm set for %1$s from now</string>
    <string name="hours_and_minutes_connector">%1$s and %2$s</string>

    <!-- Alarm Dismiss Screen -->
    <string name="task_progress_format">Task %1$d of %2$d</string>
    <string name="your_answer_format">Your Answer: %1$s</string>
    <string name="type_sentence_label">Type this exact sentence:</string>
    <string name="submit_btn">Submit</string>
    <string name="memorize_pattern">Memorize Pattern...</string>
    <string name="repeat_pattern">Repeat Pattern!</string>
    <string name="shake_device">Shake the device!</string>
    <string name="shakes_remaining">Shakes remaining: %1$d</string>

    <!-- Notification Service -->
    <string name="active_alarm_channel_name">Active Alarm</string>
    <string name="wake_up_title">WAKE UP NOW!</string>
    <string name="wake_up_desc">Complete tasks to silence the alarm</string>

    <!-- Typing Quotes -->
    <string-array name="typing_quotes">
        <item>The early bird gets the worm.</item>
        <item>Waking up is the first step to success.</item>
        <item>No snooze allowed. Rise and shine!</item>
        <item>Make today count. Get out of bed.</item>
    </string-array>
</resources>
```

### B. Custom Virtual Keyboard Layout Selector

In `AlarmDismissScreen.kt`, the custom key layout will be determined using the helper class:

```kotlin
object KeyboardLayouts {
    fun getLayoutForLanguage(language: String): List<List<Char>> {
        return when (language) {
            "ru" -> listOf(
                listOf('й', 'ц', 'у', 'к', 'е', 'н', 'г', 'ш', 'щ', 'з', 'х', 'ъ'),
                listOf('ф', 'ы', 'в', 'а', 'п', 'р', 'о', 'л', 'д', 'ж', 'э'),
                listOf('я', 'ч', 'с', 'м', 'и', 'т', 'ь', 'б', 'ю', '.', '!')
            )
            "de" -> listOf(
                listOf('q', 'w', 'e', 'r', 't', 'z', 'u', 'i', 'o', 'p', 'ü'),
                listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ö', 'ä'),
                listOf('y', 'x', 'c', 'v', 'b', 'n', 'm', 'ß', '.', '!')
            )
            "es" -> listOf(
                listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ñ'),
                listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '!')
            )
            else -> listOf(
                listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
                listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
                listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '!')
            )
        }
    }
}
```

### C. Normalization and Matching Logic

To ensure compatibility and reduce user typing fatigue/accidental mistakes with dynamic keyboard characters, `TypingEngine.isMatch` will perform normalization:
1.  Lowercases all characters.
2.  Decomposes diacritics/accents using Java's standard `Normalizer`.
3.  Filters out non-alphanumeric/non-space symbols (eliminating commas, periods, question marks, dashes, quotes).
4.  Normalizes spaces.

---

## 3. Verification Plan

### Automated Unit Tests
We will add new unit tests under `src/test/java/com/example/smartalarmer/puzzle/TypingEngineTest.kt` to verify:
-  Different language normalizations (accent stripping, punctuation removal).
-  Successful matches across Russian, Spanish, German, and English quotes under the normalized rules.

### Manual Verification
- We can change system language to Spanish (`es`), German (`de`), and Russian (`ru`) and verify that all UI elements, notification titles, and dynamic layouts update as expected.
- We will run the application and trigger a test alarm for each language to ensure the custom virtual keyboard dynamically adjusts row sizes/layout, and allows input.

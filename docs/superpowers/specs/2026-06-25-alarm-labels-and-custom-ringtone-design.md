# Design Spec: Alarm Labels and Custom Ringtones

Design specification for adding customizable text labels and standard system ringtone selection to alarms. This allows users to associate specific reminders with their alarms and pick custom wake-up audio from their system settings.

## Goals
- Allow configuring an optional custom text label for each alarm in the edit sheet.
- Display the alarm label prominently on the dashboard and during the puzzle dismiss flow.
- Allow choosing a custom alarm sound from system ringtones/alarms using the standard Android Ringtone Picker.
- Fall back gracefully to system default alarm sounds if a custom ringtone fails to load or is deleted.

---

## 🏗️ Database Changes

### 1. Alarm Entity (`Alarm.kt`)
Add two new columns:
- `label: String` (defaults to `""` for no label).
- `soundUri: String?` (nullable, defaults to `null` to use the default system sounds).

```kotlin
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String,
    val isEnabled: Boolean = true,
    val puzzlesList: String,
    val puzzleCount: Int = 2,
    val isGradualVolume: Boolean = true,
    val label: String = "",
    val soundUri: String? = null
)
```

### 2. Database Migration (`AlarmDatabase.kt`)
Increment the Room database schema version to `3` and write a migration from version `2` to `3`:
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN label TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE alarms ADD COLUMN soundUri TEXT DEFAULT NULL")
    }
}
```
Register this migration in `Room.databaseBuilder()`:
```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

---

## 🎨 User Interface Changes

### 1. Main Dashboard Card (`AlarmItemCard` in `MainActivity.kt`)
- If `label` is not empty, display the label in bold as the primary text, and show the time (`HH:MM`) below it in a smaller, regular font.
- If `label` is empty, keep the current behavior (showing the time as the primary text).
- In the alarm detail info row, append the selected sound's friendly name (e.g. `Time • Mon, Tue • Math (2 puzzles) • Oxygen`).

### 2. Alarm Edit Bottom Sheet (`AlarmEditSheet` in `MainActivity.kt`)
- **Label Field:** Add a `OutlinedTextField` at the top of the sheet to input the alarm label.
- **Sound Selector Row:** Add a selectable row displaying the active sound name. Clicking it launches the Ringtone Picker.
- **Ringtone Picker Integration:**
  - Create a `rememberLauncherForActivityResult` in `MainActivity` matching the `RingtoneManager` selection intent.
  - Pass the launcher callback and the current human-readable sound name to the sheet composable.

### 3. Alarm Dismiss Screen (`AlarmDismissScreen.kt` and `AlarmDismissActivity.kt`)
- Extend the launcher intent extras in `AlarmReceiver` and `AlarmService` to pass the alarm's `label` string.
- If the label string is not empty, display it at the very top of `AlarmDismissScreen` as a large, bold centered header to remind the user why they are waking up.

---

## 🔊 Ringtone Picker & Playback Integration

### 1. Launching standard Ringtone Picker
In `MainActivity.kt`, launch the picker using:
```kotlin
val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    if (soundUriString != null) {
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUriString))
    }
}
```

### 2. Media Playback (`AlarmService.kt`)
Update the `mediaPlayer` setup to prioritize the custom URI:
```kotlin
val soundUriString = intent?.getStringExtra("SOUND_URI")
val userUri = soundUriString?.let { Uri.parse(it) }

val fallbackUris = mutableListOf<Uri>()
if (userUri != null) {
    fallbackUris.add(userUri)
}
fallbackUris.addAll(listOf(
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
))
```
Iterate through `fallbackUris` inside a try-catch block. The custom sound is tried first; if it throws a resource error or is inaccessible, the service falls back to the next available system tone.

---

## 🧪 Verification Plan

### Automated Unit & Instrumented Tests
- **Database CRUD & Migrations:**
  - Update `AlarmDatabaseTest` (Room DAO tests) to verify that an alarm with custom `label` and `soundUri` fields is successfully inserted, updated, and retrieved with those values preserved.
- **Main App & Dashboard UI (`AlarmListScreenTest`):**
  - Add an instrumented Compose test verifying that when an alarm has a non-empty `label`, the label is displayed as the primary text, and the time is displayed below.
  - Verify that when the alarm's label is empty, the time is displayed as the primary text.
  - Verify that the edit sheet displays the "Label" text field and the "Alarm Sound" button.
- **Alarm Dismiss UI & Integration (`AlarmDismissActivityTest` / `AlarmDismissScreenTest`):**
  - Add an instrumented test that launches `AlarmDismissActivity` in preview mode with a custom label extra and asserts that the custom label is displayed at the top of the dismiss screen.
  - Verify that if the label is empty, the label header is not displayed at all.


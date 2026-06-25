# Alarm Labels and Custom Ringtones Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement custom text labels and standard system ringtone selection for alarms, storing them in Room, and displaying/playing them accordingly in the UI and service.

**Architecture:** Extend the Room database with migration `MIGRATION_2_3` to save `label: String` and `soundUri: String?` on the `Alarm` entity. Update `AlarmScheduler` and `AlarmService` to propagate these via Intents, playing the custom sound and rendering the label on the dismiss screen. Modify `MainActivity` to launch the standard system ringtone picker and configure the new properties in `AlarmEditSheet`.

**Tech Stack:** Kotlin, Jetpack Compose, Room Database, Android Services, RingtoneManager.

---

### Task 1: Database modifications & migrations

**Files:**
- Modify: [Alarm.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/data/Alarm.kt)
- Modify: [AlarmDatabase.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/data/AlarmDatabase.kt)
- Test: [AlarmDaoTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/data/AlarmDaoTest.kt)
- Test: [AlarmDatabaseTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/data/AlarmDatabaseTest.kt)

- [ ] **Step 1: Update the alarm() test helper and write a test in AlarmDaoTest.kt**

Modify `app/src/androidTest/java/com/example/smartalarmer/data/AlarmDaoTest.kt` to include `label` and `soundUri` fields in the `alarm()` helper. Write a new test checking that these fields are saved and loaded correctly:
```kotlin
    private fun alarm(
        hour: Int = 7,
        minute: Int = 30,
        isEnabled: Boolean = true,
        daysOfWeek: String = "1,2,3,4,5",
        isGradualVolume: Boolean = true,
        label: String = "Wake Up",
        soundUri: String? = "content://settings/system/alarm_alert"
    ) = Alarm(
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek,
        isEnabled = isEnabled,
        puzzlesList = "MATH",
        puzzleCount = 1,
        isGradualVolume = isGradualVolume,
        label = label,
        soundUri = soundUri
    )

    @Test
    fun insertAndGetAlarm_withLabelAndSoundUri() = runTest {
        val id = dao.insertAlarm(alarm(label = "Cardio", soundUri = "content://custom/sound")).toInt()
        val alarms = dao.getAllAlarms().first()
        assertEquals(1, alarms.size)
        assertEquals("Cardio", alarms[0].label)
        assertEquals("content://custom/sound", alarms[0].soundUri)
    }
```
Update `AlarmDatabaseTest.kt` to also verify custom label and soundUri fields are stored and retrieved:
```kotlin
    @Test
    @Throws(Exception::class)
    fun writeAlarmAndReadInList() = runBlocking {
        val alarm = Alarm(
            hour = 7,
            minute = 30,
            daysOfWeek = "1,2,3,4,5",
            puzzlesList = "MATH,TYPING",
            puzzleCount = 2,
            label = "Morning",
            soundUri = "content://test/uri"
        )
        alarmDao.insertAlarm(alarm)
        val allAlarms = alarmDao.getAllAlarms().first()
        assertEquals(1, allAlarms.size)
        assertEquals("Morning", allAlarms[0].label)
        assertEquals("content://test/uri", allAlarms[0].soundUri)
    }
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew connectedAndroidTest` (ensure emulator is running)
Expected: Fail to compile due to missing fields in `Alarm.kt`.

- [ ] **Step 3: Modify Alarm.kt to add fields**

Update `app/src/main/java/com/example/smartalarmer/data/Alarm.kt`:
```kotlin
package com.example.smartalarmer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // CSV e.g., "1,2,3,4,5"
    val isEnabled: Boolean = true,
    val puzzlesList: String, // CSV e.g., "MATH,TYPING,MEMORY"
    val puzzleCount: Int = 2,
    val isGradualVolume: Boolean = true,
    val label: String = "",
    val soundUri: String? = null
)
```

- [ ] **Step 4: Update AlarmDatabase.kt to add migration**

Add migration `MIGRATION_2_3` and register it in `app/src/main/java/com/example/smartalarmer/data/AlarmDatabase.kt`:
```kotlin
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN label TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alarms ADD COLUMN soundUri TEXT DEFAULT NULL")
            }
        }
```
Update version to `3` and database builder:
```kotlin
@Database(entities = [Alarm::class], version = 3, exportSchema = false)
// ...
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
```

- [ ] **Step 5: Run tests and verify they pass**

Run: `./gradlew connectedAndroidTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 6: Commit**

Run:
```bash
git add app/src/main/java/com/example/smartalarmer/data/Alarm.kt app/src/main/java/com/example/smartalarmer/data/AlarmDatabase.kt app/src/androidTest/java/com/example/smartalarmer/data/AlarmDaoTest.kt app/src/androidTest/java/com/example/smartalarmer/data/AlarmDatabaseTest.kt
git commit -m "feat: add label and soundUri to Alarm entity with Room migration v3"
```

---

### Task 2: Service & Scheduler changes

**Files:**
- Modify: [AlarmScheduler.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/scheduler/AlarmScheduler.kt)
- Modify: [AlarmReceiver.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/receiver/AlarmReceiver.kt)
- Modify: [AlarmService.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/service/AlarmService.kt)

- [ ] **Step 1: Pass extras in AlarmScheduler and AlarmReceiver**

In `app/src/main/java/com/example/smartalarmer/scheduler/AlarmScheduler.kt` inside `schedule()`:
```kotlin
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("PUZZLES_LIST", alarm.puzzlesList)
            putExtra("PUZZLE_COUNT", alarm.puzzleCount)
            putExtra("IS_GRADUAL_VOLUME", alarm.isGradualVolume)
            putExtra("SOUND_URI", alarm.soundUri)
            putExtra("ALARM_LABEL", alarm.label)
        }
```
In `app/src/main/java/com/example/smartalarmer/receiver/AlarmReceiver.kt`:
```kotlin
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val puzzlesList = intent.getStringExtra("PUZZLES_LIST") ?: "MATH"
        val puzzleCount = intent.getIntExtra("PUZZLE_COUNT", 2)
        val isGradualVolume = intent.getBooleanExtra("IS_GRADUAL_VOLUME", true)
        val soundUri = intent.getStringExtra("SOUND_URI")
        val alarmLabel = intent.getStringExtra("ALARM_LABEL") ?: ""

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("PUZZLES_LIST", puzzlesList)
            putExtra("PUZZLE_COUNT", puzzleCount)
            putExtra("IS_GRADUAL_VOLUME", isGradualVolume)
            putExtra("SOUND_URI", soundUri)
            putExtra("ALARM_LABEL", alarmLabel)
        }
```

- [ ] **Step 2: Update AlarmService.kt to play the custom soundUri and pass the label to Activity**

Update `app/src/main/java/com/example/smartalarmer/service/AlarmService.kt` to play custom URI with fallbacks and add label extra when launching activity:
```kotlin
        val dismissIntent = Intent(this, AlarmDismissActivity::class.java).apply {
            putExtra("PUZZLES_LIST", intent?.getStringExtra("PUZZLES_LIST"))
            putExtra("PUZZLE_COUNT", intent?.getIntExtra("PUZZLE_COUNT", 2))
            putExtra("ALARM_LABEL", intent?.getStringExtra("ALARM_LABEL") ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
```
Modify playback initialization to prioritize custom `soundUri`:
```kotlin
        // Play Loud Sound with fallbacks and correct audio routing
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

        var successfullyStarted = false
        for (uri in fallbackUris) {
            if (uri == null) continue
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmService, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                successfullyStarted = true
                android.util.Log.d("AlarmService", "Successfully playing alarm URI: $uri")
                break
            } catch (e: Exception) {
                android.util.Log.e("AlarmService", "Failed to play sound for URI $uri", e)
            }
        }
```

- [ ] **Step 3: Run the project tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (all JVM unit tests pass).

- [ ] **Step 4: Commit**

Run:
```bash
git add app/src/main/java/com/example/smartalarmer/scheduler/AlarmScheduler.kt app/src/main/java/com/example/smartalarmer/receiver/AlarmReceiver.kt app/src/main/java/com/example/smartalarmer/service/AlarmService.kt
git commit -m "feat: pass alarm label and custom ringtone URI from scheduler to service, implement service fallback playback"
```

---

### Task 3: Main UI, Sheet & ViewModel updates

**Files:**
- Modify: [MainViewModel.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt)
- Modify: [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt)
- Modify: [strings.xml](file:///home/notnako/smart_alarmer/app/src/main/res/values/strings.xml)
- Modify: [strings.xml (RU)](file:///home/notnako/smart_alarmer/app/src/main/res/values-ru/strings.xml)
- Modify: [strings.xml (DE)](file:///home/notnako/smart_alarmer/app/src/main/res/values-de/strings.xml)
- Modify: [strings.xml (ES)](file:///home/notnako/smart_alarmer/app/src/main/res/values-es/strings.xml)
- Test: [AlarmListScreenTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/ui/AlarmListScreenTest.kt)

- [ ] **Step 1: Add new string resources in values/strings.xml and localized variants**

In `app/src/main/res/values/strings.xml`:
```xml
    <string name="sound_label">Alarm Sound</string>
    <string name="sound_default">Default Alarm</string>
    <string name="label_placeholder">Alarm Label (e.g. Work)</string>
```
In `app/src/main/res/values-ru/strings.xml`:
```xml
    <string name="sound_label">Звук будильника</string>
    <string name="sound_default">По умолчанию</string>
    <string name="label_placeholder">Название будильника (например, Работа)</string>
```
In `app/src/main/res/values-de/strings.xml`:
```xml
    <string name="sound_label">Alarmton</string>
    <string name="sound_default">Standard-Alarm</string>
    <string name="label_placeholder">Alarm-Label (z. B. Arbeit)</string>
```
In `app/src/main/res/values-es/strings.xml`:
```xml
    <string name="sound_label">Sonido de alarma</string>
    <string name="sound_default">Alarma predeterminada</string>
    <string name="label_placeholder">Etiqueta de alarma (ej. Trabajo)</string>
```

- [ ] **Step 2: Update MainViewModel.kt saveAlarm signature**

Modify `saveAlarm` signature and constructor copying in `app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt`:
```kotlin
    fun saveAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        daysOfWeek: String,
        puzzlesList: String,
        puzzleCount: Int,
        isGradualVolume: Boolean,
        label: String,
        soundUri: String?
    ) {
        viewModelScope.launch {
            val current = _editingAlarm.value
            val scheduledAlarm = if (current != null) {
                val updated = current.copy(
                    hour = hour,
                    minute = minute,
                    daysOfWeek = daysOfWeek,
                    puzzlesList = puzzlesList,
                    puzzleCount = puzzleCount,
                    isGradualVolume = isGradualVolume,
                    label = label,
                    soundUri = soundUri,
                    isEnabled = true
                )
                alarmDao.updateAlarm(updated)
                AlarmScheduler.schedule(context, updated)
                updated
            } else {
                val newAlarm = Alarm(
                    hour = hour,
                    minute = minute,
                    daysOfWeek = daysOfWeek,
                    puzzlesList = puzzlesList,
                    puzzleCount = puzzleCount,
                    isGradualVolume = isGradualVolume,
                    label = label,
                    soundUri = soundUri,
                    isEnabled = true
                )
                val generatedId = alarmDao.insertAlarm(newAlarm).toInt()
                val scheduled = newAlarm.copy(id = generatedId)
                AlarmScheduler.schedule(context, scheduled)
                scheduled
            }
            // ... (keep the rest of toast/scheduling code unchanged)
```

- [ ] **Step 3: Register Activity Result Launcher for Ringtone Picker in MainActivity.kt**

Register a Launcher and declare state variables:
```kotlin
                var pickedSoundUri by remember { mutableStateOf<String?>(null) }
                var labelInput by remember { mutableStateOf("") }

                val ringtonePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                        pickedSoundUri = uri?.toString()
                    }
                }

                LaunchedEffect(editingAlarm) {
                    pickedSoundUri = editingAlarm?.soundUri
                    labelInput = editingAlarm?.label ?: ""
                }
```

- [ ] **Step 4: Update AlarmEditSheet to display Label Field & Sound Selector**

Modify `AlarmEditSheet` parameters to accept launcher click callback and selected name:
```kotlin
  @Composable
  fun AlarmEditSheet(
      alarm: Alarm?,
      onDismiss: () -> Unit,
      onSave: (hour: Int, minute: Int, daysOfWeek: String, puzzlesList: String, puzzleCount: Int, isGradualVolume: Boolean, label: String, soundUri: String?) -> Unit,
      onPickSound: () -> Unit,
      selectedSoundName: String,
      initialLabel: String
  )
```
Add components inside `AlarmEditSheet`:
```kotlin
              var label by remember { mutableStateOf(initialLabel) }
              OutlinedTextField(
                  value = label,
                  onValueChange = { label = it },
                  label = { Text(stringResource(com.example.smartalarmer.R.string.label_placeholder)) },
                  modifier = Modifier.fillMaxWidth(),
                  colors = OutlinedTextFieldDefaults.colors(
                      focusedTextColor = Color.White,
                      unfocusedTextColor = Color.White,
                      focusedBorderColor = IndigoPrimary,
                      unfocusedBorderColor = CardBorderGlass
                  )
              )
```
And sound picker selector row:
```kotlin
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clickable { onPickSound() }
                      .border(1.dp, CardBorderGlass, RoundedCornerShape(16.dp))
                      .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text(stringResource(com.example.smartalarmer.R.string.sound_label), color = Color.LightGray, fontSize = 16.sp)
                  Text(
                      text = selectedSoundName,
                      color = Color.White,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold
                  )
              }
```
Update sheet invocation in `MainActivity.kt`:
```kotlin
                          if (isSheetVisible) {
                              val resolvedSoundName = pickedSoundUri?.let { uriStr ->
                                  runCatching {
                                      RingtoneManager.getRingtone(context, Uri.parse(uriStr))?.getTitle(context)
                                  }.getOrNull()
                              } ?: stringResource(com.example.smartalarmer.R.string.sound_default)

                              AlarmEditSheet(
                                  alarm = editingAlarm,
                                  onDismiss = { viewModel.closeEditSheet() },
                                  onSave = { hour, minute, days, puzzles, count, isGradual, lbl, sound ->
                                      viewModel.saveAlarm(context, hour, minute, days, puzzles, count, isGradual, lbl, sound)
                                  },
                                  onPickSound = {
                                      val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                          putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                          putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                          putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                          putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                          pickedSoundUri?.let {
                                              putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                          }
                                      }
                                      ringtonePickerLauncher.launch(intent)
                                  },
                                  selectedSoundName = resolvedSoundName,
                                  initialLabel = labelInput
                              )
                          }
```

- [ ] **Step 5: Modify AlarmItemCard to display the label prominently**

Update `AlarmItemCard` details in `MainActivity.kt`:
```kotlin
              Column(modifier = Modifier.weight(1f)) {
                  if (alarm.label.isNotEmpty()) {
                      Text(
                          text = alarm.label,
                          fontSize = 20.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color.White
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                          text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                          fontSize = 18.sp,
                          fontWeight = FontWeight.Medium,
                          color = Color.LightGray
                      )
                  } else {
                      Text(
                          text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                          fontSize = 32.sp,
                          fontWeight = FontWeight.Bold,
                          color = Color.White
                      )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  val soundName = alarm.soundUri?.let { uriStr ->
                      runCatching {
                          RingtoneManager.getRingtone(context, Uri.parse(uriStr))?.getTitle(context)
                      }.getOrNull()
                  } ?: stringResource(com.example.smartalarmer.R.string.sound_default)
                  Text(
                      text = "$daysSummary • $puzzlesText (${alarm.puzzleCount} puzzles)$gradualText • $soundName",
                      fontSize = 13.sp,
                      color = Color.LightGray
                  )
              }
```
And propagate extras to the preview test launcher:
```kotlin
                                               onTest = {
                                                   val intent = Intent(context, AlarmDismissActivity::class.java).apply {
                                                       putExtra("PUZZLES_LIST", alarm.puzzlesList)
                                                       putExtra("PUZZLE_COUNT", alarm.puzzleCount)
                                                       putExtra("ALARM_LABEL", alarm.label)
                                                       putExtra("IS_PREVIEW", true)
                                                   }
                                                   context.startActivity(intent)
                                               }
```

- [ ] **Step 6: Update AlarmListScreenTest.kt to verify Label Layout rendering**

Update card instantiation in `AlarmListScreenTest.kt` to include label and soundUri fields:
```kotlin
    private fun testAlarm(
        hour: Int = 7,
        minute: Int = 30,
        daysOfWeek: String = "1,2,3,4,5",
        isEnabled: Boolean = true,
        puzzlesList: String = "MATH",
        puzzleCount: Int = 1,
        isGradualVolume: Boolean = false,
        label: String = "",
        soundUri: String? = null
    ) = Alarm(
        id = 1,
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek,
        isEnabled = isEnabled,
        puzzlesList = puzzlesList,
        puzzleCount = puzzleCount,
        isGradualVolume = isGradualVolume,
        label = label,
        soundUri = soundUri
    )
```
Add new tests asserting label layout behavior:
```kotlin
    @Test
    fun alarmCard_withLabel_showsLabelAsTitleAndTimeAsSubtitle() {
        setAlarmCard(alarm = testAlarm(hour = 7, minute = 30, label = "Morning Gym"))

        composeTestRule.onNodeWithText("Morning Gym").assertIsDisplayed()
        composeTestRule.onNodeWithText("07:30").assertIsDisplayed()
    }

    @Test
    fun alarmCard_withoutLabel_showsTimeAsTitleOnly() {
        setAlarmCard(alarm = testAlarm(hour = 7, minute = 30, label = ""))

        composeTestRule.onNodeWithText("07:30").assertIsDisplayed()
        // No text matching Morning Gym should exist
        composeTestRule.onNodeWithText("Morning Gym").assertDoesNotExist()
    }
```

- [ ] **Step 7: Run tests to verify success**

Run: `./gradlew connectedAndroidTest`
Expected: BUILD SUCCESSFUL (all dashboard tests pass).

- [ ] **Step 8: Commit**

Run:
```bash
git add app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt app/src/main/java/com/example/smartalarmer/ui/main/MainActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-es/strings.xml app/src/androidTest/java/com/example/smartalarmer/ui/AlarmListScreenTest.kt
git commit -m "feat: implement label input and ringtone picker in EditSheet, update card layout with label primary text, write UI tests"
```

---

### Task 4: Alarm Dismiss layout updates

**Files:**
- Modify: [AlarmDismissActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissActivity.kt)
- Modify: [AlarmDismissScreen.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt)
- Test: [AlarmDismissActivityTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissActivityTest.kt)
- Test: [AlarmDismissScreenTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissScreenTest.kt)

- [ ] **Step 1: Update AlarmDismissActivity to extract label and pass to Screen**

In `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissActivity.kt`:
```kotlin
        val puzzlesList = intent.getStringExtra("PUZZLES_LIST") ?: "MATH"
        val puzzleCount = intent.getIntExtra("PUZZLE_COUNT", 2)
        val alarmLabel = intent.getStringExtra("ALARM_LABEL") ?: ""

        setContent {
            SmartAlarmerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    AlarmDismissScreen(
                        puzzlesList = puzzlesList,
                        puzzleCount = puzzleCount,
                        alarmLabel = alarmLabel,
                        onDismissComplete = {
                            if (!isPreview) {
                                stopService(Intent(this, AlarmService::class.java))
                            }
                            finish()
                        }
                    )
                }
            }
        }
```

- [ ] **Step 2: Update AlarmDismissScreen to display label at the top**

Update signature of `AlarmDismissScreen` in `app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt`:
```kotlin
fun AlarmDismissScreen(
    puzzlesList: String,
    puzzleCount: Int,
    onDismissComplete: () -> Unit,
    alarmLabel: String = "",
    mathProvider: MathPuzzleProvider = MathEngine,
    typingProvider: TypingPuzzleProvider = TypingEngine,
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
    shakeProvider: ShakeSensorProvider = AndroidShakeSensorProvider(androidx.compose.ui.platform.LocalContext.current)
)
```
Add label rendering above the task progress text:
```kotlin
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBgScreen)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label Header (if present)
        if (alarmLabel.isNotEmpty()) {
            Text(
                text = alarmLabel,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Progress Header
        Text(
            text = stringResource(
                R.string.task_progress_format,
                currentTaskIndex + 1,
                puzzles.size
            ),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
```

- [ ] **Step 3: Update Dismiss Screen tests to assert label behavior**

Add tests verifying label behavior in `app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissScreenTest.kt`:
```kotlin
    @Test
    fun alarmDismissScreen_withLabel_showsLabelAtTop() {
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "MATH",
                puzzleCount = 1,
                onDismissComplete = {},
                alarmLabel = "Wake Up Gym",
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        composeTestRule.onNodeWithText("Wake Up Gym").assertIsDisplayed()
        composeTestRule.onNodeWithText("Task 1 of 1").assertIsDisplayed()
    }

    @Test
    fun alarmDismissScreen_withoutLabel_doesNotShowLabel() {
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "MATH",
                puzzleCount = 1,
                onDismissComplete = {},
                alarmLabel = "",
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        composeTestRule.onNodeWithText("Task 1 of 1").assertIsDisplayed()
        // No label node should be displayed
        composeTestRule.onNodeWithText("Wake Up Gym").assertDoesNotExist()
    }
```
Update launcher extras in `AlarmDismissActivityTest.kt`:
```kotlin
        val intent = Intent(context, AlarmDismissActivity::class.java).apply {
            putExtra("PUZZLES_LIST", "MATH")
            putExtra("PUZZLE_COUNT", 1)
            putExtra("IS_PREVIEW", true)
            putExtra("ALARM_LABEL", "Test Preview")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
```

- [ ] **Step 4: Run unit and instrumented tests to verify everything is passing**

Run:
```bash
./gradlew test
./gradlew connectedAndroidTest
```
Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 5: Commit**

Run:
```bash
git add app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissActivity.kt app/src/main/java/com/example/smartalarmer/ui/dismiss/AlarmDismissScreen.kt app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissActivityTest.kt app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissScreenTest.kt
git commit -m "feat: render alarm label on dismiss activity, write UI tests for labels"
```

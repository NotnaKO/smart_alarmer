# Multiple Alarm List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modify the application's settings UI to display a list of all saved alarms, allowing users to add custom alarms via a `TimePickerDialog`, toggle existing alarms on/off, and delete them.

**Architecture:** We will load the alarm list from the Room database using a Compose state flow. Toggling an alarm or deleting it will synchronize changes between the SQLite database and Android's `AlarmManager`. We will also fix a bug where alarms are scheduled using the default ID (`0`) instead of their auto-generated primary key.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, Android AlarmManager

---

### Task 1: Update AlarmDao to Return Generated Auto-Increment IDs

**Files:**
- Modify: [AlarmDao.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/data/AlarmDao.kt)

- [ ] **Step 1: Modify `insertAlarm` function signature in `AlarmDao.kt`**
  Update `insertAlarm` to return `Long` so we can capture the auto-generated SQLite primary key.
  
  ```kotlin
  package com.example.smartalarmer.data

  import androidx.room.*
  import kotlinx.coroutines.flow.Flow

  @Dao
  interface AlarmDao {
      @Query("SELECT * FROM alarms ORDER BY hour, minute")
      fun getAllAlarms(): Flow<List<Alarm>>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insertAlarm(alarm: Alarm): Long

      @Update
      suspend fun updateAlarm(alarm: Alarm)

      @Delete
      suspend fun deleteAlarm(alarm: Alarm)
  }
  ```

- [ ] **Step 2: Compile to ensure there are no compilation errors**
  Run: `./gradlew compileDebugKotlin`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/data/AlarmDao.kt
  git commit -m "database: update AlarmDao insert to return row ID"
  ```

---

### Task 2: Build the Alarm List Compose UI and TimePickerDialog Integration

**Files:**
- Modify: [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt)

- [ ] **Step 1: Update `MainActivity.kt` with Alarm List UI and Dialog Picker**
  Replace the contents of `MainActivity.kt` with a `LazyColumn` for displaying alarms and a FAB showing the `TimePickerDialog`.
  
  ```kotlin
  package com.example.smartalarmer

  import android.app.TimePickerDialog
  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Add
  import androidx.compose.material.icons.filled.Delete
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.unit.sp
  import com.example.smartalarmer.data.Alarm
  import com.example.smartalarmer.data.AlarmDatabase
  import com.example.smartalarmer.theme.SmartAlarmerTheme
  import kotlinx.coroutines.launch
  import java.util.Calendar

  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          val database = AlarmDatabase.getDatabase(this)
          val alarmDao = database.alarmDao()

          setContent {
              SmartAlarmerTheme {
                  val scope = rememberCoroutineScope()
                  val context = LocalContext.current
                  val alarms by alarmDao.getAllAlarms().collectAsState(initial = emptyList())

                  Scaffold(
                      floatingActionButton = {
                          FloatingActionButton(
                              onClick = {
                                  val calendar = Calendar.getInstance()
                                  TimePickerDialog(
                                      context,
                                      { _, selectedHour, selectedMinute ->
                                          scope.launch {
                                              val newAlarm = Alarm(
                                                  hour = selectedHour,
                                                  minute = selectedMinute,
                                                  daysOfWeek = "1,2,3,4,5",
                                                  isEnabled = true,
                                                  puzzlesList = "MATH,TYPING,MEMORY",
                                                  puzzleCount = 3
                                              )
                                              val generatedId = alarmDao.insertAlarm(newAlarm).toInt()
                                              val scheduledAlarm = newAlarm.copy(id = generatedId)
                                              AlarmScheduler.schedule(context, scheduledAlarm)
                                          }
                                      },
                                      calendar.get(Calendar.HOUR_OF_DAY),
                                      calendar.get(Calendar.MINUTE),
                                      true
                                  ).show()
                              },
                              containerColor = MaterialTheme.colorScheme.primary
                          ) {
                              Icon(Icons.Filled.Add, contentDescription = "Add Alarm", tint = Color.White)
                          }
                      }
                  ) { paddingValues ->
                      Column(
                          modifier = Modifier
                              .fillMaxSize()
                              .padding(paddingValues)
                              .padding(16.dp)
                      ) {
                          Text(
                              text = "Smart Alarmer Settings",
                              style = MaterialTheme.typography.headlineMedium,
                              fontWeight = FontWeight.Bold,
                              modifier = Modifier.padding(bottom = 16.dp)
                          )

                          if (alarms.isEmpty()) {
                              Box(
                                  modifier = Modifier.fillMaxSize().weight(1f),
                                  contentAlignment = Alignment.Center
                              ) {
                                  Text("No alarms scheduled. Tap + to add.", color = Color.Gray, fontSize = 16.sp)
                              }
                          } else {
                              LazyColumn(
                                  modifier = Modifier.fillMaxSize().weight(1f),
                                  verticalArrangement = Arrangement.spacedBy(12.dp)
                              ) {
                                  items(alarms, key = { it.id }) { alarm ->
                                      AlarmItemCard(
                                          alarm = alarm,
                                          onToggle = { isChecked ->
                                              scope.launch {
                                                  val updated = alarm.copy(isEnabled = isChecked)
                                                  alarmDao.updateAlarm(updated)
                                                  if (isChecked) {
                                                      AlarmScheduler.schedule(context, updated)
                                                  } else {
                                                      AlarmScheduler.cancel(context, updated)
                                                  }
                                              }
                                          },
                                          onDelete = {
                                              scope.launch {
                                                  AlarmScheduler.cancel(context, alarm)
                                                  alarmDao.deleteAlarm(alarm)
                                              }
                                          }
                                      )
                                  }
                              }
                          }
                      }
                  }
              }
          }
      }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun AlarmItemCard(
      alarm: Alarm,
      onToggle: (Boolean) -> Unit,
      onDelete: () -> Unit
  ) {
      Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
          ) {
              Column {
                  Text(
                      text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                      fontSize = 28.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "Required puzzles: Math, Memory, Typing",
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                  )
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                  Switch(
                      checked = alarm.isEnabled,
                      onCheckedChange = onToggle,
                      colors = SwitchDefaults.colors(
                          checkedThumbColor = MaterialTheme.colorScheme.primary,
                          checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                      )
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  IconButton(onClick = onDelete) {
                      Icon(
                          imageVector = Icons.Filled.Delete,
                          contentDescription = "Delete Alarm",
                          tint = MaterialTheme.colorScheme.error
                      )
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 2: Build the app to verify compilation succeeds**
  Run: `./gradlew assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**
  ```bash
  git add app/src/main/java/com/example/smartalarmer/MainActivity.kt
  git commit -m "ui: implement custom alarm list and FAB TimePickerDialog"
  ```

---

### Task 3: E2E Integration and Manual Verification on Emulator

**Files:**
- Verify: [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt)

- [ ] **Step 1: Install debug build to emulator**
  Run: `./gradlew installDebug`
  Expected: App is installed successfully on `emulator-5554`.

- [ ] **Step 2: Launch MainActivity**
  Run: `/home/notnako/Android/Sdk/platform-tools/adb shell am start -n com.example.smartalarmer/.MainActivity`
  Expected: App opens, showing "No alarms scheduled. Tap + to add." empty state.

- [ ] **Step 3: Add alarm**
  Click the FAB button (`+`) using adb or screen layout coordinates, select a time 1 minute in the future using the dialog, and verify a card with the scheduled time, Switch toggled to ON, and delete icon appears in the list.

- [ ] **Step 4: Verify trigger**
  Wait for the scheduled time, verify screen takeover of `AlarmDismissActivity` and volume lock. Complete the puzzles to silence it.

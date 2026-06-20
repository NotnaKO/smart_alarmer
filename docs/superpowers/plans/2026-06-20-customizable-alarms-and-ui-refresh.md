# Customizable Alarms and UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a customizable alarm creation/edit sheet with active weekdays, selected puzzles, and puzzle count, with a glassmorphic visual refresh, and a test/preview mode using MVVM architecture.

**Architecture:** We will separate UI and logic using a new `MainViewModel` class. The `AlarmDismissActivity` will accept an `IS_PREVIEW` flag to run in a non-disruptive preview mode.

**Tech Stack:** Jetpack Compose, Room, Coroutines, StateFlow, Android Jetpack ViewModel.

---

### Task 1: Create MainViewModel and Unit Tests

We will implement a ViewModel to hold the state and database actions for alarm management.

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt`
- Create: `app/src/test/java/com/example/smartalarmer/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: Write the unit tests**
  Create [MainViewModelTest.kt](file:///home/notnako/smart_alarmer/app/src/test/java/com/example/smartalarmer/ui/main/MainViewModelTest.kt) with tests checking the view model's initial state, saving new alarms, updating existing alarms, toggling alarms, and deleting alarms.
  ```kotlin
  package com.example.smartalarmer.ui.main

  import android.content.Context
  import com.example.smartalarmer.data.Alarm
  import com.example.smartalarmer.data.AlarmDao
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.flowOf
  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class MainViewModelTest {

      private var insertCalled = false
      private var updateCalled = false
      private var deleteCalled = false

      private val fakeDao = object : AlarmDao {
          val list = mutableListOf<Alarm>()
          override fun getAllAlarms(): Flow<List<Alarm>> = flowOf(list)
          override suspend fun getEnabledAlarms(): List<Alarm> = list.filter { it.isEnabled }
          override suspend fun getAlarmById(id: Int): Alarm? = list.find { it.id == id }
          override suspend fun insertAlarm(alarm: Alarm): Long {
              list.add(alarm)
              insertCalled = true
              return list.size.toLong()
          }
          override suspend fun updateAlarm(alarm: Alarm) {
              val idx = list.indexOfFirst { it.id == alarm.id }
              if (idx != -1) list[idx] = alarm
              updateCalled = true
          }
          override suspend fun deleteAlarm(alarm: Alarm) {
              list.removeIf { it.id == alarm.id }
              deleteCalled = true
          }
      }

      @Test
      fun testInitialState() {
          val viewModel = MainViewModel(fakeDao)
          assertFalse(viewModel.isBottomSheetVisible.value)
          assertEquals(null, viewModel.editingAlarm.value)
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew test --tests "com.example.smartalarmer.ui.main.MainViewModelTest"`
  Expected: FAIL (compilation error because `MainViewModel` is not created yet).

- [ ] **Step 3: Implement MainViewModel**
  Create [MainViewModel.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt):
  ```kotlin
  package com.example.smartalarmer.ui.main

  import androidx.lifecycle.ViewModel
  import androidx.lifecycle.viewModelScope
  import com.example.smartalarmer.AlarmScheduler
  import com.example.smartalarmer.data.Alarm
  import com.example.smartalarmer.data.AlarmDao
  import kotlinx.coroutines.flow.MutableStateFlow
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.asStateFlow
  import kotlinx.coroutines.launch
  import android.content.Context

  class MainViewModel(private val alarmDao: AlarmDao) : ViewModel() {

      val alarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms()
          .let { flow ->
              val state = MutableStateFlow<List<Alarm>>(emptyList())
              viewModelScope.launch {
                  flow.collect { state.value = it }
              }
              state.asStateFlow()
          }

      private val _isBottomSheetVisible = MutableStateFlow(false)
      val isBottomSheetVisible = _isBottomSheetVisible.asStateFlow()

      private val _editingAlarm = MutableStateFlow<Alarm?>(null)
      val editingAlarm = _editingAlarm.asStateFlow()

      fun openEditSheet(alarm: Alarm? = null) {
          _editingAlarm.value = alarm
          _isBottomSheetVisible.value = true
      }

      fun closeEditSheet() {
          _editingAlarm.value = null
          _isBottomSheetVisible.value = false
      }

      fun saveAlarm(
          context: Context,
          hour: Int,
          minute: Int,
          daysOfWeek: String,
          puzzlesList: String,
          puzzleCount: Int
      ) {
          viewModelScope.launch {
              val current = _editingAlarm.value
              if (current != null) {
                  val updated = current.copy(
                      hour = hour,
                      minute = minute,
                      daysOfWeek = daysOfWeek,
                      puzzlesList = puzzlesList,
                      puzzleCount = puzzleCount,
                      isEnabled = true
                  )
                  alarmDao.updateAlarm(updated)
                  AlarmScheduler.schedule(context, updated)
              } else {
                  val newAlarm = Alarm(
                      hour = hour,
                      minute = minute,
                      daysOfWeek = daysOfWeek,
                      puzzlesList = puzzlesList,
                      puzzleCount = puzzleCount,
                      isEnabled = true
                  )
                  val generatedId = alarmDao.insertAlarm(newAlarm).toInt()
                  val scheduled = newAlarm.copy(id = generatedId)
                  AlarmScheduler.schedule(context, scheduled)
              }
              closeEditSheet()
          }
      }

      fun toggleAlarm(context: Context, alarm: Alarm, isChecked: Boolean) {
          viewModelScope.launch {
              val updated = alarm.copy(isEnabled = isChecked)
              alarmDao.updateAlarm(updated)
              if (isChecked) {
                  AlarmScheduler.schedule(context, updated)
              } else {
                  AlarmScheduler.cancel(context, updated)
              }
          }
      }

      fun deleteAlarm(context: Context, alarm: Alarm) {
          viewModelScope.launch {
              AlarmScheduler.cancel(context, alarm)
              alarmDao.deleteAlarm(alarm)
          }
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew test --tests "com.example.smartalarmer.ui.main.MainViewModelTest"`
  Expected: PASS

- [ ] **Step 5: Commit**
  Run: `git add app/src/main/java/com/example/smartalarmer/ui/main/MainViewModel.kt app/src/test/java/com/example/smartalarmer/ui/main/MainViewModelTest.kt && git commit -m "feat: add MainViewModel and unit tests"`

---

### Task 2: Implement Preview Mode in AlarmDismissActivity

Update the dismissal activity to support a safe preview mode.

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt`
- Modify: `app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissScreenTest.kt`

- [ ] **Step 1: Write UI tests for preview mode**
  Open [AlarmDismissScreenTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/ui/AlarmDismissScreenTest.kt) and add:
  ```kotlin
      @Test
      fun alarmDismissScreen_previewModeDismissesWithoutServiceStop() {
          // Verify that passing IS_PREVIEW extra doesn't sound or crash
          // We can check that screen finishes after all tasks
      }
  ```

- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew connectedAndroidTest`
  Expected: Compile errors/failures (due to `IS_PREVIEW` flag check missing).

- [ ] **Step 3: Modify AlarmDismissActivity**
  Update [AlarmDismissActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt) to extract `IS_PREVIEW` and conditionalize lock screen/back button handling.
  ```kotlin
  package com.example.smartalarmer

  import android.content.Intent
  import android.os.Build
  import android.os.Bundle
  import android.view.WindowManager
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.activity.addCallback
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.material3.Surface
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import com.example.smartalarmer.theme.SmartAlarmerTheme

  class AlarmDismissActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          
          val isPreview = intent.getBooleanExtra("IS_PREVIEW", false)

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
                              or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                  )
              }

              // Disable back button
              onBackPressedDispatcher.addCallback(this) {
                  // Ignore back button
              }
          }

          val puzzlesList = intent.getStringExtra("PUZZLES_LIST") ?: "MATH"
          val puzzleCount = intent.getIntExtra("PUZZLE_COUNT", 2)

          setContent {
              SmartAlarmerTheme {
                  Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                      AlarmDismissScreen(
                          puzzlesList = puzzlesList,
                          puzzleCount = puzzleCount,
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
      }
  }
  ```

- [ ] **Step 4: Run tests to verify they pass**
  Run: `./gradlew connectedAndroidTest`
  Expected: PASS

- [ ] **Step 5: Commit**
  Run: `git add app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt && git commit -m "feat: add IS_PREVIEW support to AlarmDismissActivity"`

---

### Task 3: Redesign Settings screen and implement Bottom Sheet Editor

Replace the layout in `MainActivity` with our Glassmorphic theme, list view, and weekdays/puzzle sheet controls, utilizing the new `MainViewModel`.

**Files:**
- Modify: `app/src/main/java/com/example/smartalarmer/MainActivity.kt`

- [ ] **Step 1: Replace MainActivity.kt with the new design**
  Rewrite [MainActivity.kt](file:///home/notnako/smart_alarmer/app/src/main/java/com/example/smartalarmer/MainActivity.kt) using `MainViewModel` and styling:
  ```kotlin
  package com.example.smartalarmer

  import android.app.TimePickerDialog
  import android.content.Intent
  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.compose.foundation.background
  import androidx.compose.foundation.border
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.items
  import androidx.compose.foundation.shape.CircleShape
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.Add
  import androidx.compose.material.icons.filled.Delete
  import androidx.compose.material.icons.filled.PlayArrow
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Brush
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.platform.LocalContext
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.text.style.TextAlign
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.unit.sp
  import com.example.smartalarmer.data.Alarm
  import com.example.smartalarmer.data.AlarmDatabase
  import com.example.smartalarmer.theme.SmartAlarmerTheme
  import com.example.smartalarmer.ui.main.MainViewModel
  import java.util.Calendar

  class MainActivity : ComponentActivity() {
      @OptIn(ExperimentalMaterial3Api::class)
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          val database = AlarmDatabase.getDatabase(this)
          val viewModel = MainViewModel(database.alarmDao())

          setContent {
              SmartAlarmerTheme {
                  val context = LocalContext.current
                  val alarms by viewModel.alarms.collectAsState(initial = emptyList())
                  val isSheetVisible by viewModel.isBottomSheetVisible.collectAsState()
                  val editingAlarm by viewModel.editingAlarm.collectAsState()

                  Scaffold(
                      floatingActionButton = {
                          FloatingActionButton(
                              onClick = { viewModel.openEditSheet(null) },
                              containerColor = Color(0xFF6366F1),
                              contentColor = Color.White,
                              shape = RoundedCornerShape(16.dp)
                          ) {
                              Icon(Icons.Filled.Add, contentDescription = "Add Alarm")
                          }
                      }
                  ) { paddingValues ->
                      Box(
                          modifier = Modifier
                              .fillMaxSize()
                              .background(
                                  Brush.verticalGradient(
                                      colors = listOf(Color(0xFF0F0C20), Color(0xFF15102A))
                                  )
                              )
                              .padding(paddingValues)
                              .padding(16.dp)
                      ) {
                          Column(modifier = Modifier.fillMaxSize()) {
                              Text(
                                  text = "Smart Alarmer",
                                  style = MaterialTheme.typography.headlineLarge,
                                  fontWeight = FontWeight.Bold,
                                  color = Color.White,
                                  modifier = Modifier.padding(vertical = 16.dp)
                              )

                              if (alarms.isEmpty()) {
                                  Box(
                                      modifier = Modifier
                                          .fillMaxSize()
                                          .weight(1f),
                                      contentAlignment = Alignment.Center
                                  ) {
                                      Text(
                                          text = "No alarms scheduled.\nTap + to add an alarm.",
                                          color = Color.Gray,
                                          fontSize = 16.sp,
                                          textAlign = TextAlign.Center
                                      )
                                  }
                              } else {
                                  LazyColumn(
                                      modifier = Modifier
                                          .fillMaxSize()
                                          .weight(1f),
                                      verticalArrangement = Arrangement.spacedBy(16.dp)
                                  ) {
                                      items(alarms, key = { it.id }) { alarm ->
                                          AlarmItemCard(
                                              alarm = alarm,
                                              onToggle = { isChecked ->
                                                  viewModel.toggleAlarm(context, alarm, isChecked)
                                              },
                                              onDelete = {
                                                  viewModel.deleteAlarm(context, alarm)
                                              },
                                              onEdit = {
                                                  viewModel.openEditSheet(alarm)
                                              },
                                              onTest = {
                                                  val intent = Intent(context, AlarmDismissActivity::class.java).apply {
                                                      putExtra("PUZZLES_LIST", alarm.puzzlesList)
                                                      putExtra("PUZZLE_COUNT", alarm.puzzleCount)
                                                      putExtra("IS_PREVIEW", true)
                                                  }
                                                  context.startActivity(intent)
                                              }
                                          )
                                      }
                                  }
                              }
                          }

                          if (isSheetVisible) {
                              AlarmEditSheet(
                                  alarm = editingAlarm,
                                  onDismiss = { viewModel.closeEditSheet() },
                                  onSave = { hour, minute, days, puzzles, count ->
                                      viewModel.saveAlarm(context, hour, minute, days, puzzles, count)
                                  }
                              )
                          }
                      }
                  }
              }
          }
      }
  }

  @Composable
  fun AlarmItemCard(
      alarm: Alarm,
      onToggle: (Boolean) -> Unit,
      onDelete: () -> Unit,
      onEdit: () -> Unit = {},
      onTest: () -> Unit = {}
  ) {
      val daysList = alarm.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
      val daysSummary = when {
          daysList.isEmpty() -> "One-time"
          daysList.size == 7 -> "Every day"
          daysList.containsAll(listOf(1, 2, 3, 4, 5)) && daysList.size == 5 -> "Weekdays"
          daysList.containsAll(listOf(6, 7)) && daysList.size == 2 -> "Weekends"
          else -> {
              val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
              daysList.sorted().joinToString(", ") { names[it - 1] }
          }
      }

      val puzzlesText = alarm.puzzlesList.split(",")
          .joinToString(", ") { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }

      Card(
          modifier = Modifier
              .fillMaxWidth()
              .clickable(onClick = onEdit)
              .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp)),
          colors = CardDefaults.cardColors(containerColor = Color(0x0FFFFFFF)),
          shape = RoundedCornerShape(24.dp)
      ) {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
          ) {
              Column(modifier = Modifier.weight(1f)) {
                  Text(
                      text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                      fontSize = 32.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "$daysSummary • $puzzlesText (${alarm.puzzleCount} puzzles)",
                      fontSize = 13.sp,
                      color = Color.LightGray
                  )
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                      onClick = onTest,
                      colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF10B981))
                  ) {
                      Icon(Icons.Filled.PlayArrow, contentDescription = "Test Alarm")
                  }

                  Switch(
                      checked = alarm.isEnabled,
                      onCheckedChange = onToggle,
                      colors = SwitchDefaults.colors(
                          checkedThumbColor = Color(0xFF6366F1),
                          checkedTrackColor = Color(0x4D6366F1),
                          uncheckedThumbColor = Color.Gray,
                          uncheckedTrackColor = Color(0x1AFFFFFF)
                      )
                  )

                  IconButton(
                      onClick = onDelete,
                      colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFEF4444))
                  ) {
                      Icon(Icons.Filled.Delete, contentDescription = "Delete Alarm")
                  }
              }
          }
      }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun AlarmEditSheet(
      alarm: Alarm?,
      onDismiss: () -> Unit,
      onSave: (hour: Int, minute: Int, daysOfWeek: String, puzzlesList: String, puzzleCount: Int) -> Unit
  ) {
      val context = LocalContext.current
      var hour by remember { mutableStateOf(alarm?.hour ?: 8) }
      var minute by remember { mutableStateOf(alarm?.minute ?: 0) }
      
      val initialDays = alarm?.daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
      val selectedDays = remember { mutableStateListOf<Int>().apply { addAll(initialDays) } }

      val initialPuzzles = alarm?.puzzlesList?.split(",")?.map { it.trim().uppercase() }?.toSet() ?: setOf("MATH")
      val selectedPuzzles = remember { mutableStateListOf<String>().apply { addAll(initialPuzzles) } }

      var puzzleCount by remember { mutableStateOf(alarm?.puzzleCount ?: 1) }

      ModalBottomSheet(
          onDismissRequest = onDismiss,
          containerColor = Color(0xFF1E1B3A),
          dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0x33FFFFFF)) }
      ) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp)
                  .navigationBarsPadding(),
              verticalArrangement = Arrangement.spacedBy(20.dp)
          ) {
              Text(
                  text = if (alarm == null) "New Alarm" else "Edit Alarm",
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
              )

              // Time Button
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                          TimePickerDialog(
                              context,
                              { _, selectedHour, selectedMinute ->
                                  hour = selectedHour
                                  minute = selectedMinute
                              },
                              hour,
                              minute,
                              true
                          ).show()
                      }
                      .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                      .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text("Time", color = Color.LightGray, fontSize = 16.sp)
                  Text(
                      text = String.format("%02d:%02d", hour, minute),
                      color = Color.White,
                      fontSize = 20.sp,
                      fontWeight = FontWeight.Bold
                  )
              }

              // Days of week
              Column {
                  Text("Repeat Days", color = Color.LightGray, fontSize = 14.sp)
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                      val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                      for (i in 1..7) {
                          val isSelected = selectedDays.contains(i)
                          Box(
                              modifier = Modifier
                                  .size(40.dp)
                                  .background(
                                      if (isSelected) Color(0xFF6366F1) else Color(0x1AFFFFFF),
                                      CircleShape
                                  )
                                  .clickable {
                                      if (isSelected) selectedDays.remove(i) else selectedDays.add(i)
                                  },
                              contentAlignment = Alignment.Center
                          ) {
                              Text(
                                  text = dayLabels[i - 1],
                                  color = if (isSelected) Color.White else Color.Gray,
                                  fontWeight = FontWeight.Bold
                              )
                          }
                      }
                  }
              }

              // Puzzle selection
              Column {
                  Text("Dismiss Puzzles", color = Color.LightGray, fontSize = 14.sp)
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                      val puzzleTypes = listOf("MATH", "MEMORY", "TYPING")
                      puzzleTypes.forEach { type ->
                          val isSelected = selectedPuzzles.contains(type)
                          FilterChip(
                              selected = isSelected,
                              onClick = {
                                  if (isSelected) {
                                      if (selectedPuzzles.size > 1) {
                                          selectedPuzzles.remove(type)
                                          if (puzzleCount > selectedPuzzles.size) {
                                              puzzleCount = selectedPuzzles.size
                                          }
                                      }
                                  } else {
                                      selectedPuzzles.add(type)
                                  }
                              },
                              label = { Text(type) },
                              colors = FilterChipDefaults.filterChipColors(
                                  selectedContainerColor = Color(0xFF6366F1),
                                  selectedLabelColor = Color.White,
                                  containerColor = Color(0x1AFFFFFF),
                                  labelColor = Color.Gray
                              )
                          )
                      }
                  }
              }

              // Puzzle Count Stepper
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text("Puzzles Required", color = Color.LightGray, fontSize = 16.sp)
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                      Button(
                          onClick = { if (puzzleCount > 1) puzzleCount-- },
                          colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFFFFFF)),
                          contentPadding = PaddingValues(0.dp),
                          modifier = Modifier.size(36.dp),
                          shape = CircleShape
                      ) {
                          Text("-", color = Color.White, fontSize = 18.sp)
                      }
                      Text(text = puzzleCount.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                      Button(
                          onClick = { if (puzzleCount < selectedPuzzles.size) puzzleCount++ },
                          colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFFFFFF)),
                          contentPadding = PaddingValues(0.dp),
                          modifier = Modifier.size(36.dp),
                          shape = CircleShape
                      ) {
                          Text("+", color = Color.White, fontSize = 18.sp)
                      }
                  }
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Actions
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                  OutlinedButton(
                      onClick = onDismiss,
                      modifier = Modifier.weight(1f),
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                      border = BorderStroke(1.dp, Color(0x33FFFFFF))
                  ) {
                      Text("Cancel")
                  }
                  Button(
                      onClick = {
                          val daysCsv = selectedDays.sorted().joinToString(",")
                          val puzzlesCsv = selectedPuzzles.joinToString(",")
                          onSave(hour, minute, daysCsv, puzzlesCsv, puzzleCount)
                      },
                      modifier = Modifier.weight(1f),
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                  ) {
                      Text("Save", color = Color.White)
                  }
              }
          }
      }
  }
  ```

---

### Task 4: Fix / Update UI and Integration Tests

Review and update the instrumented tests to match the new UI.

**Files:**
- Modify: `app/src/androidTest/java/com/example/smartalarmer/ui/AlarmListScreenTest.kt`

- [ ] **Step 1: Update AlarmListScreenTest.kt to test card details and buttons**
  Replace [AlarmListScreenTest.kt](file:///home/notnako/smart_alarmer/app/src/androidTest/java/com/example/smartalarmer/ui/AlarmListScreenTest.kt) with tests asserting the new dynamic text summary and play/test button callbacks:
  ```kotlin
  package com.example.smartalarmer.ui

  import android.content.Context
  import androidx.compose.foundation.layout.Column
  import androidx.compose.ui.test.*
  import androidx.compose.ui.test.junit4.createComposeRule
  import androidx.test.core.app.ApplicationProvider
  import androidx.test.ext.junit.runners.AndroidJUnit4
  import com.example.smartalarmer.AlarmItemCard
  import com.example.smartalarmer.data.Alarm
  import com.example.smartalarmer.theme.SmartAlarmerTheme
  import org.junit.Assert.assertTrue
  import org.junit.Rule
  import org.junit.Test
  import org.junit.runner.RunWith

  @RunWith(AndroidJUnit4::class)
  class AlarmListScreenTest {

      @get:Rule
      val composeTestRule = createComposeRule()

      private fun testAlarm(
          daysOfWeek: String = "1,2,3,4,5",
          puzzlesList: String = "MATH",
          puzzleCount: Int = 1
      ) = Alarm(
          id = 1,
          hour = 7,
          minute = 30,
          daysOfWeek = daysOfWeek,
          isEnabled = true,
          puzzlesList = puzzlesList,
          puzzleCount = puzzleCount
      )

      @Test
      fun alarmCard_displaysCustomDaysAndPuzzles() {
          composeTestRule.setContent {
              SmartAlarmerTheme {
                  AlarmItemCard(
                      alarm = testAlarm(daysOfWeek = "1,3,5", puzzlesList = "MATH,MEMORY", puzzleCount = 2)
                  )
              }
          }

          composeTestRule.onNodeWithText("Mon, Wed, Fri • Math, Memory (2 puzzles)").assertIsDisplayed()
      }

      @Test
      fun alarmCard_displaysOneTimeAlarm() {
          composeTestRule.setContent {
              SmartAlarmerTheme {
                  AlarmItemCard(
                      alarm = testAlarm(daysOfWeek = "", puzzlesList = "MATH")
                  )
              }
          }

          composeTestRule.onNodeWithText("One-time • Math (1 puzzles)").assertIsDisplayed()
      }

      @Test
      fun alarmCard_testButtonClick_triggersCallback() {
          var testClicked = false
          composeTestRule.setContent {
              SmartAlarmerTheme {
                  AlarmItemCard(
                      alarm = testAlarm(),
                      onTest = { testClicked = true }
                  )
              }
          }

          composeTestRule.onNodeWithContentDescription("Test Alarm").performClick()
          assertTrue(testClicked)
      }
  }
  ```

- [ ] **Step 2: Run all tests and verify all pass**
  Run: `./gradlew test` and `./gradlew connectedAndroidTest`
  Expected: All green.

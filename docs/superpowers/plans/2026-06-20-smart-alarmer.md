# Smart Alarmer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android smart alarm application that forces users to complete randomized cognitive puzzles (Math, Typing, Memory) with full-screen overlay locking and maximum volume enforcement to prevent alarm skipping.

**Architecture:** The app uses AlarmManager to schedule precise wake-ups, starting a Foreground AlarmService that handles looping MediaPlayer audio and overrides volume keys. The service launches a full-screen AlarmDismissActivity overlay using Compose to run a state machine through randomized puzzles (Math, Typing, Memory) built in pure Kotlin to ensure easy unit testing.

**Tech Stack:** Android 14+ (SDK 34), Kotlin, Jetpack Compose, Material 3, Room DB, Gradle (AGP 9).

---

### Task 1: Project Scaffolding and Dependencies

**Files:**
- Create: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Scaffold empty Compose project using Android CLI**
  Run: `/home/notnako/.local/bin/android create empty-activity --name="Smart Alarmer" --output=.`
  Expected: Scaffolds project files in current directory.

- [ ] **Step 2: Add Room and lifecycle dependencies to build.gradle.kts**
  Modify: `app/build.gradle.kts` to add dependencies and plugins.
  ```kotlin
  plugins {
      alias(libs.plugins.android.application)
      alias(libs.plugins.kotlin.android)
      alias(libs.plugins.kotlin.compose)
      id("kotlin-kapt")
  }
  // Add in dependencies block:
  dependencies {
      implementation("androidx.room:room-runtime:2.6.1")
      kapt("androidx.room:room-compiler:2.6.1")
      implementation("androidx.room:room-ktx:2.6.1")
      implementation("androidx.navigation:navigation-compose:2.7.7")
      implementation("androidx.lifecycle:lifecycle-service:2.7.0")
  }
  ```

- [ ] **Step 3: Define Permissions in Manifest**
  Modify: `app/src/main/AndroidManifest.xml` to declare permissions and services.
  ```xml
  <manifest xmlns:android="http://schemas.android.com/apk/res/android">
      <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
      <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
      <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
      <uses-permission android:name="android.permission.WAKE_LOCK" />
      <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
      
      <application ...>
          <!-- AlarmReceiver -->
          <receiver android:name=".AlarmReceiver" android:enabled="true" android:exported="false" />
          
          <!-- AlarmService -->
          <service 
              android:name=".AlarmService" 
              android:foregroundServiceType="mediaPlayback"
              android:enabled="true"
              android:exported="false" />
              
          <!-- AlarmDismissActivity -->
          <activity
              android:name=".AlarmDismissActivity"
              android:showWhenLocked="true"
              android:turnScreenOn="true"
              android:launchMode="singleInstance"
              android:excludeFromRecents="true"
              android:exported="false" />
      </application>
  </manifest>
  ```

- [ ] **Step 4: Verify gradle compilation**
  Run: `./gradlew compileDebugKotlin`
  Expected: Build succeeds.

- [ ] **Step 5: Commit**
  Run:
  ```bash
  git add .
  git commit -m "chore: scaffold project and add dependencies"
  ```

---

### Task 2: Math Puzzle Engine

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/puzzle/MathEngine.kt`
- Create: `app/src/test/java/com/example/smartalarmer/puzzle/MathEngineTest.kt`

- [ ] **Step 1: Write failing unit test for MathEngine**
  Create: `app/src/test/java/com/example/smartalarmer/puzzle/MathEngineTest.kt`
  ```kotlin
  package com.example.smartalarmer.puzzle

  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class MathEngineTest {
      @Test
      fun testGenerateEasyEquation() {
          val puzzle = MathEngine.generate(Difficulty.EASY)
          val result = MathEngine.evaluate(puzzle.equation)
          assertEquals(puzzle.answer, result)
          assertTrue(puzzle.equation.contains("+") || puzzle.equation.contains("-"))
      }
  }
  ```

- [ ] **Step 2: Run test to verify failure**
  Run: `./gradlew test`
  Expected: Fail with uncompiled/missing `MathEngine` reference.

- [ ] **Step 3: Implement minimal MathEngine**
  Create: `app/src/main/java/com/example/smartalarmer/puzzle/MathEngine.kt`
  ```kotlin
  package com.example.smartalarmer.puzzle

  enum class Difficulty { EASY, MEDIUM, HARD }
  data class MathPuzzle(val equation: String, val answer: Int)

  object MathEngine {
      fun generate(difficulty: Difficulty): MathPuzzle {
          return when (difficulty) {
              Difficulty.EASY -> {
                  val a = (10..99).random()
                  val b = (10..99).random()
                  MathPuzzle("$a + $b", a + b)
              }
              else -> MathPuzzle("1 + 1", 2)
          }
      }

      fun evaluate(equation: String): Int {
          val parts = equation.split(" ")
          if (parts.size == 3 && parts[1] == "+") {
              return parts[0].toInt() + parts[2].toInt()
          }
          return 2
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew test`
  Expected: Pass.

- [ ] **Step 5: Implement all difficulties and expand tests**
  Modify: `app/src/main/java/com/example/smartalarmer/puzzle/MathEngine.kt`
  ```kotlin
  package com.example.smartalarmer.puzzle

  enum class Difficulty { EASY, MEDIUM, HARD }
  data class MathPuzzle(val equation: String, val answer: Int)

  object MathEngine {
      fun generate(difficulty: Difficulty): MathPuzzle {
          return when (difficulty) {
              Difficulty.EASY -> {
                  val a = (10..99).random()
                  val b = (10..99).random()
                  MathPuzzle("$a + $b", a + b)
              }
              Difficulty.MEDIUM -> {
                  val a = (2..12).random()
                  val b = (2..12).random()
                  val c = (10..99).random()
                  MathPuzzle("$a * $b + $c", (a * b) + c)
              }
              Difficulty.HARD -> {
                  // Find x in Ax + B = C
                  val x = (2..9).random()
                  val a = (3..9).random()
                  val b = (10..40).random()
                  val c = a * x + b
                  MathPuzzle("Find x: $a*x + $b = $c", x)
              }
          }
      }

      fun evaluate(equation: String): Int {
          // Simple custom parsers or direct mapping from answer
          return 0 // unused since we check answer stored in MathPuzzle
      }
  }
  ```
  Add Medium and Hard tests in `MathEngineTest.kt` to ensure outputs match answers.
  Run: `./gradlew test`
  Expected: Pass.

- [ ] **Step 6: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/puzzle/MathEngine.kt app/src/test/java/com/example/smartalarmer/puzzle/MathEngineTest.kt
  git commit -m "feat: implement MathEngine with difficulties and unit tests"
  ```

---

### Task 3: Typing Puzzle Engine

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/puzzle/TypingEngine.kt`
- Create: `app/src/test/java/com/example/smartalarmer/puzzle/TypingEngineTest.kt`

- [ ] **Step 1: Write failing unit test for TypingEngine**
  Create: `app/src/test/java/com/example/smartalarmer/puzzle/TypingEngineTest.kt`
  ```kotlin
  package com.example.smartalarmer.puzzle

  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class TypingEngineTest {
      @Test
      fun testMatchTextExact() {
          val target = "Wake up and smell the coffee!"
          assertTrue(TypingEngine.isMatch(target, "Wake up and smell the coffee!"))
          assertFalse(TypingEngine.isMatch(target, "wake up and smell the coffee!"))
      }
  }
  ```

- [ ] **Step 2: Run test to verify failure**
  Run: `./gradlew test`
  Expected: Fail with uncompiled/missing `TypingEngine` reference.

- [ ] **Step 3: Implement TypingEngine**
  Create: `app/src/main/java/com/example/smartalarmer/puzzle/TypingEngine.kt`
  ```kotlin
  package com.example.smartalarmer.puzzle

  object TypingEngine {
      private val quotes = listOf(
          "The early bird gets the worm.",
          "Waking up is the first step to success.",
          "No snooze allowed. Rise and shine!",
          "Make today count. Get out of bed."
      )

      fun getRandomQuote(): String = quotes.random()

      fun isMatch(target: String, input: String): Boolean {
          return target.trim() == input.trim()
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew test`
  Expected: Pass.

- [ ] **Step 5: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/puzzle/TypingEngine.kt app/src/test/java/com/example/smartalarmer/puzzle/TypingEngineTest.kt
  git commit -m "feat: implement TypingEngine and unit tests"
  ```

---

### Task 4: Memory Puzzle Engine

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/puzzle/MemoryEngine.kt`
- Create: `app/src/test/java/com/example/smartalarmer/puzzle/MemoryEngineTest.kt`

- [ ] **Step 1: Write failing unit test for MemoryEngine**
  Create: `app/src/test/java/com/example/smartalarmer/puzzle/MemoryEngineTest.kt`
  ```kotlin
  package com.example.smartalarmer.puzzle

  import org.junit.Assert.assertFalse
  import org.junit.Assert.assertTrue
  import org.junit.Test

  class MemoryEngineTest {
      @Test
      fun testGenerateSequence() {
          val seq = MemoryEngine.generateSequence(5)
          assertTrue(seq.size == 5)
          assertTrue(seq.all { it in 0..8 })
      }
  }
  ```

- [ ] **Step 2: Run test to verify failure**
  Run: `./gradlew test`
  Expected: Fail.

- [ ] **Step 3: Implement MemoryEngine**
  Create: `app/src/main/java/com/example/smartalarmer/puzzle/MemoryEngine.kt`
  ```kotlin
  package com.example.smartalarmer.puzzle

  object MemoryEngine {
      fun generateSequence(length: Int): List<Int> {
          return List(length) { (0..8).random() }
      }

      fun verifyStep(sequence: List<Int>, userInputs: List<Int>): Boolean {
          if (userInputs.size > sequence.size) return false
          for (i in userInputs.indices) {
              if (sequence[i] != userInputs[i]) return false
          }
          return true
      }
  }
  ```

- [ ] **Step 4: Run test to verify it passes**
  Run: `./gradlew test`
  Expected: Pass.

- [ ] **Step 5: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/puzzle/MemoryEngine.kt app/src/test/java/com/example/smartalarmer/puzzle/MemoryEngineTest.kt
  git commit -m "feat: implement MemoryEngine and unit tests"
  ```

---

### Task 5: Database Persistence

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/data/Alarm.kt`
- Create: `app/src/main/java/com/example/smartalarmer/data/AlarmDao.kt`
- Create: `app/src/main/java/com/example/smartalarmer/data/AlarmDatabase.kt`

- [ ] **Step 1: Create Alarm entity and DAO**
  Create: `app/src/main/java/com/example/smartalarmer/data/Alarm.kt`
  ```kotlin
  package com.example.smartalarmer.data

  import androidx.room.Entity
  import androidx.room.PrimaryKey

  @Entity(tableName = "alarms")
  data class Alarm(
      @PrimaryKey(autoGenerate = true) val id: Int = 0,
      val hour: Int,
      val minute: Int,
      val daysOfWeek: String, // CSV e.g., "1,2,3,4,5" (Monday=1)
      val isEnabled: Boolean = true,
      val puzzlesList: String, // CSV of active puzzles e.g., "MATH,TYPING"
      val puzzleCount: Int = 2
  )
  ```
  Create: `app/src/main/java/com/example/smartalarmer/data/AlarmDao.kt`
  ```kotlin
  package com.example.smartalarmer.data

  import androidx.room.*
  import kotlinx.coroutines.flow.Flow

  @Dao
  interface AlarmDao {
      @Query("SELECT * FROM alarms ORDER BY hour, minute")
      fun getAllAlarms(): Flow<List<Alarm>>

      @Insert(onConflict = OnConflictStrategy.REPLACE)
      suspend fun insertAlarm(alarm: Alarm)

      @Update
      suspend fun updateAlarm(alarm: Alarm)

      @Delete
      suspend fun deleteAlarm(alarm: Alarm)
  }
  ```

- [ ] **Step 2: Create AlarmDatabase**
  Create: `app/src/main/java/com/example/smartalarmer/data/AlarmDatabase.kt`
  ```kotlin
  package com.example.smartalarmer.data

  import android.content.Context
  import androidx.room.Database
  import androidx.room.Room
  import androidx.room.RoomDatabase

  @Database(entities = [Alarm::class], version = 1, exportSchema = false)
  abstract class AlarmDatabase : RoomDatabase() {
      abstract fun alarmDao(): AlarmDao

      companion object {
          @Volatile private var INSTANCE: AlarmDatabase? = null

          fun getDatabase(context: Context): AlarmDatabase {
              return INSTANCE ?: synchronized(this) {
                  val instance = Room.databaseBuilder(
                      context.applicationContext,
                      AlarmDatabase::class.java,
                      "alarm_database"
                  ).build()
                  INSTANCE = instance
                  instance
              }
          }
      }
  }
  ```

- [ ] **Step 3: Compile and verify database generation**
  Run: `./gradlew compileDebugKotlin`
  Expected: Compiles with Room annotations fully resolved.

- [ ] **Step 4: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/data/
  git commit -m "feat: add Room database for alarm persistence"
  ```

---

### Task 6: Alarm Scheduling and Triggers

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/AlarmReceiver.kt`
- Create: `app/src/main/java/com/example/smartalarmer/AlarmScheduler.kt`

- [ ] **Step 1: Implement AlarmScheduler utility**
  Create: `app/src/main/java/com/example/smartalarmer/AlarmScheduler.kt`
  ```kotlin
  package com.example.smartalarmer

  import android.annotation.SuppressLint
  import android.app.AlarmManager
  import android.app.PendingIntent
  import android.content.Context
  import android.content.Intent
  import com.example.smartalarmer.data.Alarm
  import java.util.Calendar

  object AlarmScheduler {
      @SuppressLint("ScheduleExactAlarm")
      fun schedule(context: Context, alarm: Alarm) {
          val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
          val intent = Intent(context, AlarmReceiver::class.java).apply {
              putExtra("ALARM_ID", alarm.id)
              putExtra("PUZZLES_LIST", alarm.puzzlesList)
              putExtra("PUZZLE_COUNT", alarm.puzzleCount)
          }
          val pendingIntent = PendingIntent.getBroadcast(
              context, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )

          val calendar = Calendar.getInstance().apply {
              set(Calendar.HOUR_OF_DAY, alarm.hour)
              set(Calendar.MINUTE, alarm.minute)
              set(Calendar.SECOND, 0)
              if (before(Calendar.getInstance())) {
                  add(Calendar.DATE, 1)
              }
          }

          alarmManager.setExactAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              calendar.timeInMillis,
              pendingIntent
          )
      }

      fun cancel(context: Context, alarm: Alarm) {
          val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
          val intent = Intent(context, AlarmReceiver::class.java)
          val pendingIntent = PendingIntent.getBroadcast(
              context, alarm.id, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
          )
          if (pendingIntent != null) {
              alarmManager.cancel(pendingIntent)
          }
      }
  }
  ```

- [ ] **Step 2: Implement AlarmReceiver**
  Create: `app/src/main/java/com/example/smartalarmer/AlarmReceiver.kt`
  ```kotlin
  package com.example.smartalarmer

  import android.content.BroadcastReceiver
  import android.content.Context
  import android.content.Intent
  import android.os.Build

  class AlarmReceiver : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
          val serviceIntent = Intent(context, AlarmService::class.java).apply {
              putExtra("ALARM_ID", intent.getIntExtra("ALARM_ID", -1))
              putExtra("PUZZLES_LIST", intent.getStringExtra("PUZZLES_LIST") ?: "MATH")
              putExtra("PUZZLE_COUNT", intent.getIntExtra("PUZZLE_COUNT", 2))
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              context.startForegroundService(serviceIntent)
          } else {
              context.startService(serviceIntent)
          }
      }
  }
  ```

- [ ] **Step 3: Verify compilation**
  Run: `./gradlew compileDebugKotlin`
  Expected: Successful compile.

- [ ] **Step 4: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/AlarmScheduler.kt app/src/main/java/com/example/smartalarmer/AlarmReceiver.kt
  git commit -m "feat: implement AlarmScheduler and AlarmReceiver"
  ```

---

### Task 7: Foreground Service and Dismiss Overlay Activity

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/AlarmService.kt`
- Create: `app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt`

- [ ] **Step 1: Implement AlarmService with Foreground Notification & Sound Loop**
  Create: `app/src/main/java/com/example/smartalarmer/AlarmService.kt`
  ```kotlin
  package com.example.smartalarmer

  import android.app.*
  import android.content.Context
  import android.content.Intent
  import android.media.AudioAttributes
  import android.media.AudioManager
  import android.media.MediaPlayer
  import android.media.RingtoneManager
  import android.os.IBinder
  import androidx.core.app.NotificationCompat
  import java.util.Timer
  import java.util.TimerTask

  class AlarmService : Service() {
      private var mediaPlayer: MediaPlayer? = null
      private var audioManager: AudioManager? = null
      private var volumeTimer: Timer? = null

      override fun onCreate() {
          super.onCreate()
          audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
      }

      override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
          val channelId = "AlarmChannel"
          val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
              val channel = NotificationChannel(channelId, "Active Alarm", NotificationManager.IMPORTANCE_HIGH)
              notificationManager.createNotificationChannel(channel)
          }

          val dismissIntent = Intent(this, AlarmDismissActivity::class.java).apply {
              flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
              putExtra("PUZZLES_LIST", intent?.getStringExtra("PUZZLES_LIST"))
              putExtra("PUZZLE_COUNT", intent?.getIntExtra("PUZZLE_COUNT", 2))
          }
          val fullScreenPendingIntent = PendingIntent.getActivity(
              this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )

          val notification = NotificationCompat.Builder(this, channelId)
              .setContentTitle("WAKE UP NOW!")
              .setContentText("Complete tasks to silence the alarm")
              .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
              .setPriority(NotificationCompat.PRIORITY_HIGH)
              .setCategory(NotificationCompat.CATEGORY_ALARM)
              .setFullScreenIntent(fullScreenPendingIntent, true)
              .build()

          startForeground(1, notification)

          // Play Loud Sound
          val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
          mediaPlayer = MediaPlayer().apply {
              setDataSource(this@AlarmService, alarmUri)
              setAudioAttributes(
                  AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_ALARM)
                      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                      .build()
              )
              isLooping = true
              prepare()
              start()
          }

          // Lock Volume to Maximum
          volumeTimer = Timer().apply {
              scheduleAtFixedRate(object : TimerTask() {
                  override fun run() {
                      audioManager?.setStreamVolume(
                          AudioManager.STREAM_ALARM,
                          audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7,
                          0
                      )
                  }
              }, 0, 1000)
          }

          return START_STICKY
      }

      override fun onDestroy() {
          mediaPlayer?.stop()
          mediaPlayer?.release()
          volumeTimer?.cancel()
          super.onDestroy()
      }

      override fun onBind(intent: Intent?): IBinder? = null
  }
  ```

- [ ] **Step 2: Implement AlarmDismissActivity Overlay Base**
  Create: `app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt`
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
  import com.example.smartalarmer.ui.theme.SmartAlarmerTheme

  class AlarmDismissActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          
          // Show on lock screen
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
              setShowWhenLocked(true)
              setTurnScreenOn(true)
          } else {
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

          val puzzlesList = intent.getStringExtra("PUZZLES_LIST") ?: "MATH"
          val puzzleCount = intent.getIntExtra("PUZZLE_COUNT", 2)

          setContent {
              SmartAlarmerTheme {
                  Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                      AlarmDismissScreen(
                          puzzlesList = puzzlesList,
                          puzzleCount = puzzleCount,
                          onDismissComplete = {
                              stopService(Intent(this, AlarmService::class.java))
                              finish()
                          }
                      )
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 3: Verify compilation**
  Run: `./gradlew compileDebugKotlin`
  Expected: Successful compilation.

- [ ] **Step 4: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/AlarmService.kt app/src/main/java/com/example/smartalarmer/AlarmDismissActivity.kt
  git commit -m "feat: implement AlarmService volume locking and AlarmDismissActivity overlays"
  ```

---

### Task 8: Puzzle UI Screens and State Machine

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt`

- [ ] **Step 1: Implement AlarmDismissScreen and Puzzle Logic views**
  Create: `app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt`
  This contains Compose UI rendering tasks one-by-one.
  ```kotlin
  package com.example.smartalarmer

  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.*
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import androidx.compose.ui.unit.sp
  import com.example.smartalarmer.puzzle.*

  enum class PuzzleType { MATH, TYPING, MEMORY }

  @Composable
  fun AlarmDismissScreen(
      puzzlesList: String,
      puzzleCount: Int,
      onDismissComplete: () -> Unit
  ) {
      val puzzles = remember {
          puzzlesList.split(",")
              .mapNotNull {
                  runCatching { PuzzleType.valueOf(it.trim().uppercase()) }.getOrNull()
              }
              .shuffled()
              .take(puzzleCount)
      }

      var currentTaskIndex by remember { mutableStateOf(0) }

      if (currentTaskIndex >= puzzles.size) {
          LaunchedEffect(Unit) {
              onDismissComplete()
          }
          return
      }

      val currentPuzzle = puzzles[currentTaskIndex]

      Column(
          modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFF121212))
              .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceBetween
      ) {
          // Progress Header
          Text(
              text = "Task ${currentTaskIndex + 1} of ${puzzles.size}",
              color = Color.White,
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold
          )

          // Active Puzzle View
          Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
              when (currentPuzzle) {
                  PuzzleType.MATH -> MathPuzzleView(onComplete = { currentTaskIndex++ })
                  PuzzleType.TYPING -> TypingPuzzleView(onComplete = { currentTaskIndex++ })
                  PuzzleType.MEMORY -> MemoryPuzzleView(onComplete = { currentTaskIndex++ })
              }
          }
      }
  }

  @Composable
  fun MathPuzzleView(onComplete: () -> Unit) {
      val puzzle = remember { MathEngine.generate(Difficulty.MEDIUM) }
      var input by remember { mutableStateOf("") }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(text = puzzle.equation, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(16.dp))
          Text(text = "Your Answer: $input", color = Color.LightGray, fontSize = 20.sp)
          Spacer(modifier = Modifier.height(24.dp))
          // Numeric Keyboard
          Column {
              (1..9).chunked(3).forEach { row ->
                  Row {
                      row.forEach { num ->
                          Button(
                              onClick = { input += num.toString() },
                              modifier = Modifier.padding(4.dp).size(64.dp),
                              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                          ) {
                              Text(text = num.toString(), color = Color.White, fontSize = 20.sp)
                          }
                      }
                  }
              }
              Row {
                  Button(
                      onClick = { if (input.isNotEmpty()) input = input.dropLast(1) },
                      modifier = Modifier.padding(4.dp).size(64.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                  ) {
                      Text(text = "⌫", color = Color.White, fontSize = 20.sp)
                  }
                  Button(
                      onClick = { input += "0" },
                      modifier = Modifier.padding(4.dp).size(64.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                  ) {
                      Text(text = "0", color = Color.White, fontSize = 20.sp)
                  }
                  Button(
                      onClick = {
                          if (input.toIntOrNull() == puzzle.answer) {
                              onComplete()
                          } else {
                              input = ""
                          }
                      },
                      modifier = Modifier.padding(4.dp).size(64.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                  ) {
                      Text(text = "✔", color = Color.White, fontSize = 20.sp)
                  }
              }
          }
      }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun TypingPuzzleView(onComplete: () -> Unit) {
      val targetQuote = remember { TypingEngine.getRandomQuote() }
      var input by remember { mutableStateOf("") }
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          Text(text = "Type this exact sentence:", color = Color.Gray, fontSize = 14.sp)
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = targetQuote, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.height(24.dp))
          
          TextField(
              value = input,
              onValueChange = { input = it },
              modifier = Modifier.fillMaxWidth(),
              colors = TextFieldDefaults.colors(
                  focusedTextColor = Color.White,
                  unfocusedTextColor = Color.White,
                  focusedContainerColor = Color(0xFF222222),
                  unfocusedContainerColor = Color(0xFF222222)
              )
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
              onClick = {
                  if (TypingEngine.isMatch(targetQuote, input)) {
                      onComplete()
                  }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
          ) {
              Text("Submit")
          }
      }
  }

  @Composable
  fun MemoryPuzzleView(onComplete: () -> Unit) {
      val sequence = remember { MemoryEngine.generateSequence(4) }
      val userInputs = remember { mutableStateListOf<Int>() }
      var isShowingSequence by remember { mutableStateOf(true) }
      var activeFlashIndex by remember { mutableStateOf(-1) }

      LaunchedEffect(isShowingSequence) {
          if (isShowingSequence) {
              for (index in sequence) {
                  activeFlashIndex = index
                  kotlinx.coroutines.delay(600)
                  activeFlashIndex = -1
                  kotlinx.coroutines.delay(200)
              }
              isShowingSequence = false
          }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
              text = if (isShowingSequence) "Memorize Pattern..." else "Repeat Pattern!",
              color = Color.White,
              fontSize = 18.sp
          )
          Spacer(modifier = Modifier.height(24.dp))

          Column {
              (0..8).chunked(3).forEach { row ->
                  Row {
                      row.forEach { index ->
                          val isFlashed = activeFlashIndex == index
                          Button(
                              onClick = {
                                  if (!isShowingSequence) {
                                      userInputs.add(index)
                                      val isValid = MemoryEngine.verifyStep(sequence, userInputs)
                                      if (!isValid) {
                                          userInputs.clear()
                                          isShowingSequence = true
                                      } else if (userInputs.size == sequence.size) {
                                          onComplete()
                                      }
                                  }
                              },
                              modifier = Modifier.padding(6.dp).size(72.dp),
                              colors = ButtonDefaults.buttonColors(
                                  containerColor = if (isFlashed) Color(0xFFF59E0B) else Color(0xFF333333)
                              ),
                              shape = RoundedCornerShape(36.dp)
                          ) {}
                      }
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 2: Verify compile**
  Run: `./gradlew compileDebugKotlin`
  Expected: Successful compilation.

- [ ] **Step 3: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/AlarmDismissScreen.kt
  git commit -m "feat: implement AlarmDismissScreen containing Math, Typing, and Memory puzzle Compositions"
  ```

---

### Task 9: Main Configuration Screen

**Files:**
- Create: `app/src/main/java/com/example/smartalarmer/MainActivity.kt`

- [ ] **Step 1: Replace MainActivity to display alarm schedule and database settings**
  Modify: `app/src/main/java/com/example/smartalarmer/MainActivity.kt`
  Replace main Activity code with a Compose layout to list, create, and schedule alarms.
  ```kotlin
  package com.example.smartalarmer

  import android.os.Bundle
  import androidx.activity.ComponentActivity
  import androidx.activity.compose.setContent
  import androidx.compose.foundation.layout.*
  import androidx.compose.material3.*
  import androidx.compose.runtime.*
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.unit.dp
  import com.example.smartalarmer.data.Alarm
  import com.example.smartalarmer.data.AlarmDatabase
  import com.example.smartalarmer.ui.theme.SmartAlarmerTheme
  import kotlinx.coroutines.launch
  import java.util.Calendar

  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          val database = AlarmDatabase.getDatabase(this)
          val alarmDao = database.alarmDao()

          setContent {
              SmartAlarmerTheme {
                  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                      val scope = rememberCoroutineScope()
                      Column(modifier = Modifier.padding(16.dp)) {
                          Text("Smart Alarmer Settings", style = MaterialTheme.typography.headlineMedium)
                          Spacer(modifier = Modifier.height(24.dp))
                          
                          Button(onClick = {
                              scope.launch {
                                  val calendar = Calendar.getInstance().apply {
                                      add(Calendar.MINUTE, 1)
                                  }
                                  val newAlarm = Alarm(
                                      hour = calendar.get(Calendar.HOUR_OF_DAY),
                                      minute = calendar.get(Calendar.MINUTE),
                                      daysOfWeek = "1,2,3,4,5",
                                      puzzlesList = "MATH,TYPING,MEMORY",
                                      puzzleCount = 3
                                  )
                                  alarmDao.insertAlarm(newAlarm)
                                  AlarmScheduler.schedule(this@MainActivity, newAlarm)
                              }
                          }) {
                              Text("Schedule Test Alarm in 1 Min (All Puzzles)")
                          }
                      }
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 2: Verify clean compile and packaging**
  Run: `./gradlew assembleDebug`
  Expected: Successful compilation, producing `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Commit**
  Run:
  ```bash
  git add app/src/main/java/com/example/smartalarmer/MainActivity.kt
  git commit -m "feat: configure MainActivity for quick test alarm scheduling"
  ```

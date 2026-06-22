package com.example.smartalarmer

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.theme.SmartAlarmerTheme
import com.example.smartalarmer.ui.main.MainViewModel

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

                var hasNotificationPermission by remember { mutableStateOf(true) }
                var hasExactAlarmPermission by remember { mutableStateOf(true) }
                var hasFullScreenIntentPermission by remember { mutableStateOf(true) }
                val sharedPrefs = remember { context.getSharedPreferences("smart_alarmer_prefs", Context.MODE_PRIVATE) }
                var isXiaomiDismissed by remember { mutableStateOf(sharedPrefs.getBoolean("xiaomi_warning_dismissed", false)) }
                var isIgnoringBatteryOptimizations by remember { mutableStateOf(DeviceUtils.isIgnoringBatteryOptimizations(context)) }
                val isXiaomiDevice = remember { DeviceUtils.isXiaomi() }

                val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { hasNotificationPermission = it }
                )

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            } else {
                                true
                            }

                            hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                alarmManager.canScheduleExactAlarms()
                            } else {
                                true
                            }

                            hasFullScreenIntentPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                notificationManager.canUseFullScreenIntent()
                            } else {
                                true
                            }
                            
                            isIgnoringBatteryOptimizations = DeviceUtils.isIgnoringBatteryOptimizations(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

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
                            val showXiaomiWarning = isXiaomiDevice && !isXiaomiDismissed
                            if (!isIgnoringBatteryOptimizations || showXiaomiWarning) {
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
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFF59E0B),
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Disable Battery Limits", fontSize = 11.sp)
                                                }
                                            }
                                            if (showXiaomiWarning) {
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
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFF59E0B),
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Xiaomi Settings", fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        sharedPrefs.edit().putBoolean("xiaomi_warning_dismissed", true).apply()
                                                        isXiaomiDismissed = true
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Dismiss", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (!hasNotificationPermission || !hasExactAlarmPermission || !hasFullScreenIntentPermission) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Permissions Required",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Please enable all permissions below to ensure alarms wake up your device and display properly over the lock screen.",
                                            color = Color.LightGray,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (!hasNotificationPermission) {
                                                Button(
                                                    onClick = {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Allow Notifications", fontSize = 11.sp)
                                                }
                                            }
                                            if (!hasExactAlarmPermission) {
                                                Button(
                                                    onClick = {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                                data = Uri.parse("package:${context.packageName}")
                                                            }
                                                            context.startActivity(intent)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Allow Alarms", fontSize = 11.sp)
                                                }
                                            }
                                            if (!hasFullScreenIntentPermission) {
                                                Button(
                                                    onClick = {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                                data = Uri.parse("package:${context.packageName}")
                                                            }
                                                            context.startActivity(intent)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Allow Lockscreen Display", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

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
                  OutlinedButton(
                      onClick = onTest,
                      modifier = Modifier.padding(end = 8.dp),
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                      border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                  ) {
                      Icon(
                          imageVector = Icons.Filled.PlayArrow,
                          contentDescription = "Test Alarm",
                          modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                      val puzzleTypes = listOf("MATH", "MEMORY", "TYPING", "SHAKE")
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

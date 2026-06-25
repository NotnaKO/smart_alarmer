package com.example.smartalarmer.ui.main

import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
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
import com.example.smartalarmer.ui.theme.*
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import com.example.smartalarmer.utils.DeviceUtils

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
                            containerColor = IndigoPrimary,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.add_alarm_desc))
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(DarkBgStart, DarkBgEnd)
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
                                        .border(1.dp, OrangeWarning.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = OrangeWarningSemi),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.bg_execution_settings),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.bg_execution_desc),
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
                                                        containerColor = OrangeWarning,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.disable_battery_limits), fontSize = 11.sp)
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
                                                        containerColor = OrangeWarning,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.xiaomi_settings), fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        sharedPrefs.edit().putBoolean("xiaomi_warning_dismissed", true).apply()
                                                        isXiaomiDismissed = true
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeWarning),
                                                    border = BorderStroke(1.dp, OrangeWarning.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.dismiss), fontSize = 11.sp)
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
                                        .border(1.dp, RedError.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = RedErrorSemi),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.permissions_required),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.permissions_desc),
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = RedError),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.allow_notifications), fontSize = 11.sp)
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = RedError),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.allow_alarms), fontSize = 11.sp)
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = RedError),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.allow_lockscreen), fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.app_name),
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
                                          text = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.no_alarms_scheduled),
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
                                                      putExtra("ALARM_LABEL", alarm.label)
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
                                  initialLabel = labelInput,
                                  pickedSoundUri = pickedSoundUri
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
      val context = LocalContext.current
      val daysList = alarm.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
      val daysSummary = when {
          daysList.isEmpty() -> stringResource(com.example.smartalarmer.R.string.one_time)
          daysList.size == 7 -> stringResource(com.example.smartalarmer.R.string.every_day)
          daysList.containsAll(listOf(1, 2, 3, 4, 5)) && daysList.size == 5 -> stringResource(com.example.smartalarmer.R.string.weekdays)
          daysList.containsAll(listOf(6, 7)) && daysList.size == 2 -> stringResource(com.example.smartalarmer.R.string.weekends)
          else -> {
              val names = listOf(
                  stringResource(com.example.smartalarmer.R.string.day_mon),
                  stringResource(com.example.smartalarmer.R.string.day_tue),
                  stringResource(com.example.smartalarmer.R.string.day_wed),
                  stringResource(com.example.smartalarmer.R.string.day_thu),
                  stringResource(com.example.smartalarmer.R.string.day_fri),
                  stringResource(com.example.smartalarmer.R.string.day_sat),
                  stringResource(com.example.smartalarmer.R.string.day_sun)
              )
              daysList.sorted().joinToString(", ") { names[it - 1] }
          }
      }

      val puzzlesText = alarm.puzzlesList.split(",")
          .map { puzzleId ->
              val resId = when (puzzleId.trim().uppercase()) {
                  "MATH" -> com.example.smartalarmer.R.string.puzzle_math
                  "MEMORY" -> com.example.smartalarmer.R.string.puzzle_memory
                  "TYPING" -> com.example.smartalarmer.R.string.puzzle_typing
                  "SHAKE" -> com.example.smartalarmer.R.string.puzzle_shake
                  else -> com.example.smartalarmer.R.string.puzzle_math
              }
              stringResource(resId)
          }
          .joinToString(", ")
      val gradualText = if (alarm.isGradualVolume) " • " + stringResource(com.example.smartalarmer.R.string.gradual_volume) else ""

      Card(
          modifier = Modifier
              .fillMaxWidth()
              .clickable(onClick = onEdit)
              .border(1.dp, CardBorderGlass, RoundedCornerShape(24.dp)),
          colors = CardDefaults.cardColors(containerColor = CardBgGlass),
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
              Row(verticalAlignment = Alignment.CenterVertically) {
                  OutlinedButton(
                      onClick = onTest,
                      modifier = Modifier.padding(end = 8.dp),
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess),
                      border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.5f)),
                      shape = RoundedCornerShape(12.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                  ) {
                      Icon(
                          imageVector = Icons.Filled.PlayArrow,
                          contentDescription = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.test_btn),
                          modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.test_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }

                  Switch(
                      checked = alarm.isEnabled,
                      onCheckedChange = onToggle,
                      colors = SwitchDefaults.colors(
                          checkedThumbColor = IndigoPrimary,
                          checkedTrackColor = IndigoPrimary.copy(alpha = 0.3f),
                          uncheckedThumbColor = Color.Gray,
                          uncheckedTrackColor = CardBorderGlass
                      )
                  )

                  IconButton(
                      onClick = onDelete,
                      colors = IconButtonDefaults.iconButtonColors(contentColor = RedError)
                  ) {
                      Icon(Icons.Filled.Delete, contentDescription = androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.delete_alarm_desc))
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
      onSave: (hour: Int, minute: Int, daysOfWeek: String, puzzlesList: String, puzzleCount: Int, isGradualVolume: Boolean, label: String, soundUri: String?) -> Unit,
      onPickSound: () -> Unit,
      selectedSoundName: String,
      initialLabel: String,
      pickedSoundUri: String?
  ) {
      val context = LocalContext.current
      var hour by remember { mutableStateOf(alarm?.hour ?: 8) }
      var minute by remember { mutableStateOf(alarm?.minute ?: 0) }
      
      val initialDays = alarm?.daysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
      val selectedDays = remember { mutableStateListOf<Int>().apply { addAll(initialDays) } }

      val initialPuzzles = alarm?.puzzlesList?.split(",")?.map { it.trim().uppercase() }?.toSet() ?: setOf("MATH")
      val selectedPuzzles = remember { mutableStateListOf<String>().apply { addAll(initialPuzzles) } }

      var puzzleCount by remember { mutableStateOf(alarm?.puzzleCount ?: 1) }
      var isGradualVolume by remember { mutableStateOf(alarm?.isGradualVolume ?: true) }

      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

      ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
          containerColor = BottomSheetBg,
          dragHandle = { BottomSheetDefaults.DragHandle(color = BottomSheetDrag) }
      ) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(24.dp)
                  .navigationBarsPadding(),
              verticalArrangement = Arrangement.spacedBy(20.dp)
          ) {
              Text(
                  text = if (alarm == null) androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.new_alarm) else androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.edit_alarm),
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
              )

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
                      .border(1.dp, CardBorderGlass, RoundedCornerShape(16.dp))
                      .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.time_label), color = Color.LightGray, fontSize = 16.sp)
                  Text(
                      text = String.format("%02d:%02d", hour, minute),
                      color = Color.White,
                      fontSize = 20.sp,
                      fontWeight = FontWeight.Bold
                  )
              }

              // Sound Button
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

              // Days of week
              Column {
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.repeat_days_label), color = Color.LightGray, fontSize = 14.sp)
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                      val dayLabels = listOf(
                          stringResource(com.example.smartalarmer.R.string.day_m),
                          stringResource(com.example.smartalarmer.R.string.day_t),
                          stringResource(com.example.smartalarmer.R.string.day_w),
                          stringResource(com.example.smartalarmer.R.string.day_th),
                          stringResource(com.example.smartalarmer.R.string.day_f),
                          stringResource(com.example.smartalarmer.R.string.day_sa),
                          stringResource(com.example.smartalarmer.R.string.day_su)
                      )
                      for (i in 1..7) {
                          val isSelected = selectedDays.contains(i)
                          Box(
                              modifier = Modifier
                                  .size(40.dp)
                                  .background(
                                      if (isSelected) IndigoPrimary else KeyButtonBg,
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
                           // Puzzle selection
              Column {
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.dismiss_puzzles_label), color = Color.LightGray, fontSize = 14.sp)
                  Spacer(modifier = Modifier.height(8.dp))
                  FlowRow(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                      val puzzleTypes = listOf("MATH", "MEMORY", "TYPING", "SHAKE")
                      puzzleTypes.forEach { type ->
                          val isSelected = selectedPuzzles.contains(type)
                          val displayName = when (type) {
                              "MATH" -> stringResource(com.example.smartalarmer.R.string.puzzle_math)
                              "MEMORY" -> stringResource(com.example.smartalarmer.R.string.puzzle_memory)
                              "TYPING" -> stringResource(com.example.smartalarmer.R.string.puzzle_typing)
                              "SHAKE" -> stringResource(com.example.smartalarmer.R.string.puzzle_shake)
                              else -> type
                          }
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
                              label = { Text(displayName) },
                              colors = FilterChipDefaults.filterChipColors(
                                  selectedContainerColor = IndigoPrimary,
                                  selectedLabelColor = Color.White,
                                  containerColor = KeyButtonBg,
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
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.puzzles_required), color = Color.LightGray, fontSize = 16.sp)
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                      Button(
                          onClick = { if (puzzleCount > 1) puzzleCount-- },
                          colors = ButtonDefaults.buttonColors(containerColor = KeyButtonBg),
                          contentPadding = PaddingValues(0.dp),
                          modifier = Modifier.size(36.dp),
                          shape = CircleShape
                      ) {
                          Text("-", color = Color.White, fontSize = 18.sp)
                      }
                      Text(text = puzzleCount.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                      Button(
                          onClick = { if (puzzleCount < selectedPuzzles.size) puzzleCount++ },
                          colors = ButtonDefaults.buttonColors(containerColor = KeyButtonBg),
                          contentPadding = PaddingValues(0.dp),
                          modifier = Modifier.size(36.dp),
                          shape = CircleShape
                      ) {
                          Text("+", color = Color.White, fontSize = 18.sp)
                      }
                  }
              }

              // Gradual Volume toggle
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Column {
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.gradual_volume), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.gradual_volume_desc), color = Color.LightGray, fontSize = 12.sp)
                  }
                  Switch(
                      checked = isGradualVolume,
                      onCheckedChange = { isGradualVolume = it },
                      colors = SwitchDefaults.colors(
                          checkedThumbColor = IndigoPrimary,
                          checkedTrackColor = IndigoPrimary.copy(alpha = 0.3f),
                          uncheckedThumbColor = Color.Gray,
                          uncheckedTrackColor = CardBorderGlass
                      )
                  )
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
                      border = BorderStroke(1.dp, BottomSheetDrag)
                  ) {
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.cancel))
                  }
                  Button(
                      onClick = {
                          val daysCsv = selectedDays.sorted().joinToString(",")
                          val puzzlesCsv = selectedPuzzles.joinToString(",")
                          onSave(hour, minute, daysCsv, puzzlesCsv, puzzleCount, isGradualVolume, label, pickedSoundUri)
                      },
                      modifier = Modifier.weight(1f),
                      colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                  ) {
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.save), color = Color.White)
                  }
              }
          }
      }
  }
}

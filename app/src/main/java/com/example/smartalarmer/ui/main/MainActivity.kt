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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.RoomAlarmRepository
import com.example.smartalarmer.domain.AlarmDay
import com.example.smartalarmer.domain.AlarmDays
import com.example.smartalarmer.domain.PuzzleSelection
import com.example.smartalarmer.domain.PuzzleType
import com.example.smartalarmer.domain.puzzleSelection
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.puzzle.AndroidShakeSensorProvider
import com.example.smartalarmer.scheduler.AndroidAlarmSchedulingGateway
import com.example.smartalarmer.ui.theme.*
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import com.example.smartalarmer.utils.DeviceUtils
import java.util.Locale

private fun handleMainUiEvent(context: Context, event: MainUiEvent) {
    when (event) {
        is MainUiEvent.AlarmScheduled -> showScheduledToast(context, event.triggerAtMillis)
        MainUiEvent.ExactAlarmPermissionRequired -> {
            Toast.makeText(
                context,
                com.example.smartalarmer.R.string.exact_alarm_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
        is MainUiEvent.AlarmScheduleFailed -> {
            android.util.Log.e("MainActivity", "Unable to schedule alarm", event.exception)
            Toast.makeText(
                context,
                com.example.smartalarmer.R.string.alarm_schedule_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun showScheduledToast(context: Context, triggerAtMillis: Long) {
    val diffMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val hours = diffMs / (3600 * 1000)
    val minutes = (diffMs % (3600 * 1000)) / (60 * 1000)

    val hoursText = if (hours > 0) {
        context.resources.getQuantityString(
            com.example.smartalarmer.R.plurals.hours_plural,
            hours.toInt(),
            hours.toInt()
        )
    } else {
        ""
    }
    val minutesText = context.resources.getQuantityString(
        com.example.smartalarmer.R.plurals.minutes_plural,
        minutes.toInt(),
        minutes.toInt()
    )
    val timeText = if (hours > 0) {
        context.getString(
            com.example.smartalarmer.R.string.hours_and_minutes_connector,
            hoursText,
            minutesText
        )
    } else {
        minutesText
    }

    val message = context.getString(com.example.smartalarmer.R.string.alarm_set_toast, timeText)
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            alarmRepository = RoomAlarmRepository(
                AlarmDatabase.getDatabase(applicationContext).alarmDao()
            ),
            alarmScheduler = AndroidAlarmSchedulingGateway(applicationContext)
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmartAlarmerTheme {
                val context = LocalContext.current
                val selectAlarmSoundTitle = stringResource(com.example.smartalarmer.R.string.select_alarm_sound)
                val alarms by viewModel.alarms.collectAsStateWithLifecycle()
                val isSheetVisible by viewModel.isBottomSheetVisible.collectAsStateWithLifecycle()
                val editingAlarm by viewModel.editingAlarm.collectAsStateWithLifecycle()

                var hasNotificationPermission by rememberSaveable { mutableStateOf(true) }
                var hasExactAlarmPermission by rememberSaveable { mutableStateOf(true) }
                var hasFullScreenIntentPermission by rememberSaveable { mutableStateOf(true) }
                val sharedPrefs = remember { context.getSharedPreferences("smart_alarmer_prefs", Context.MODE_PRIVATE) }
                var isXiaomiDismissed by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("xiaomi_warning_dismissed", false)) }
                var isIgnoringBatteryOptimizations by rememberSaveable { mutableStateOf(DeviceUtils.isIgnoringBatteryOptimizations(context)) }
                val isXiaomiDevice = remember { DeviceUtils.isXiaomi() }

                val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { hasNotificationPermission = it }
                )

                var pickedSoundUri by rememberSaveable { mutableStateOf<String?>(null) }
                var labelInput by rememberSaveable { mutableStateOf("") }

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

                LaunchedEffect(viewModel) {
                    viewModel.uiEvents.collect { event ->
                        handleMainUiEvent(context, event)
                    }
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
                                        FlowRow(
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
                                        FlowRow(
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
                                                  viewModel.toggleAlarm(alarm, isChecked)
                                              },
                                              onDelete = {
                                                  viewModel.deleteAlarm(alarm)
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
                                      viewModel.saveAlarm(hour, minute, days, puzzles, count, isGradual, lbl, sound)
                                  },
                                  onPickSound = {
                                      val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                          putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                          putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, selectAlarmSoundTitle)
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

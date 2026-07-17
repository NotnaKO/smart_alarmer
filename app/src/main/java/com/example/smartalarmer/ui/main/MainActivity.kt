package com.example.smartalarmer.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.RoomAlarmRepository
import com.example.smartalarmer.domain.WakeUpCheckCoordinator
import com.example.smartalarmer.scheduler.AndroidAlarmSchedulingGateway
import com.example.smartalarmer.scheduler.AndroidWakeUpCheckSchedulingGateway
import com.example.smartalarmer.service.ActiveAlarmRecovery
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import com.example.smartalarmer.ui.theme.*
import com.example.smartalarmer.utils.AlarmCapabilityChecker
import com.example.smartalarmer.utils.AlarmTimeFormatter
import com.example.smartalarmer.utils.AndroidAlarmActivationGate
import com.example.smartalarmer.utils.DeviceUtils

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val database = AlarmDatabase.getDatabase(applicationContext)
        val repository = RoomAlarmRepository(database.alarmDao())
        MainViewModel.Factory(
            alarmRepository = repository,
            alarmScheduler = AndroidAlarmSchedulingGateway(applicationContext),
            activationGate = AndroidAlarmActivationGate(applicationContext),
            wakeUpCheckCoordinator =
            WakeUpCheckCoordinator(
                alarmRepository = repository,
                sessionDao = database.wakeUpCheckDao(),
                scheduler = AndroidWakeUpCheckSchedulingGateway(applicationContext)
            ),
            wakeUpCheckSessionFlow = database.wakeUpCheckDao().observeAllSessions()
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
                val wakeUpCheckSessions by viewModel.wakeUpCheckSessions.collectAsStateWithLifecycle()
                val isSheetVisible by viewModel.isBottomSheetVisible.collectAsStateWithLifecycle()
                val editingAlarm by viewModel.editingAlarm.collectAsStateWithLifecycle()

                var capabilities by remember { mutableStateOf(AlarmCapabilityChecker.check(context)) }
                var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
                var pendingDelete by remember { mutableStateOf<Alarm?>(null) }
                var pendingWakeUpCheckCancel by remember { mutableStateOf<Alarm?>(null) }
                val sharedPrefs = remember { context.getSharedPreferences("smart_alarmer_prefs", Context.MODE_PRIVATE) }
                var isXiaomiDismissed by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("xiaomi_warning_dismissed", false)) }
                val isXiaomiDevice = remember { DeviceUtils.isXiaomi() }

                val requestNotificationPermissionLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { capabilities = AlarmCapabilityChecker.check(context) }
                    )

                var pickedSoundUri by rememberSaveable { mutableStateOf<String?>(null) }
                var labelInput by rememberSaveable { mutableStateOf("") }

                val ringtonePickerLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK) {
                            val uri =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    result.data?.getParcelableExtra(
                                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                        Uri::class.java
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                                }
                            pickedSoundUri = uri?.toString()
                        }
                    }

                LaunchedEffect(editingAlarm) {
                    pickedSoundUri = editingAlarm?.soundUri
                    labelInput = editingAlarm?.label ?: ""
                }

                LaunchedEffect(viewModel) {
                    viewModel.reconcileEnabledAlarms()
                    viewModel.reconcileWakeUpChecks()
                    viewModel.uiEvents.collect { event ->
                        handleMainUiEvent(context, event)
                    }
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer =
                        androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                capabilities = AlarmCapabilityChecker.check(context)
                                ActiveAlarmRecovery.createIntent(context)?.let(context::startActivity)
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
                            Icon(
                                Icons.Filled.Add,
                                contentDescription =
                                androidx.compose.ui.res.stringResource(
                                    com.example.smartalarmer.R.string.add_alarm_desc
                                )
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(DarkBgStart, DarkBgEnd)
                                )
                            ).padding(paddingValues)
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val showXiaomiWarning = isXiaomiDevice && !isXiaomiDismissed
                            if (showXiaomiWarning) {
                                XiaomiExecutionWarningCard(
                                    onOpenSettings = {
                                        val intent = DeviceUtils.getMiuiPermissionIntent(context)
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            context.startActivity(DeviceUtils.getStandardAppInfoIntent(context))
                                        }
                                    },
                                    onDismiss = {
                                        sharedPrefs.edit().putBoolean("xiaomi_warning_dismissed", true).apply()
                                        isXiaomiDismissed = true
                                    }
                                )
                            }

                            if (!capabilities.notificationDeliveryReady ||
                                !capabilities.exactAlarmAccess ||
                                !capabilities.fullScreenIntentAccess
                            ) {
                                AlarmCapabilityWarningCard(
                                    capabilities = capabilities,
                                    onRequestNotifications = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    onOpenNotificationSettings = {
                                        val settingsIntent =
                                            if (
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                                capabilities.notificationsEnabled
                                            ) {
                                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                    putExtra(
                                                        Settings.EXTRA_CHANNEL_ID,
                                                        com.example.smartalarmer.service.AlarmNotification.CHANNEL_ID
                                                    )
                                                }
                                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                }
                                            } else {
                                                DeviceUtils.getStandardAppInfoIntent(context)
                                            }
                                        context.startActivity(settingsIntent)
                                    },
                                    onRequestExactAlarmAccess = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                            )
                                        }
                                    },
                                    onRequestFullScreenAccess = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                            )
                                        }
                                    }
                                )
                            }

                            MainScreenHeader(onPrivacyPolicyClick = { showPrivacyPolicy = true })

                            if (alarms.isEmpty()) {
                                Box(
                                    modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text =
                                        androidx.compose.ui.res.stringResource(
                                            com.example.smartalarmer.R.string.no_alarms_scheduled
                                        ),
                                        color = Color.Gray,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(alarms, key = { it.id }) { alarm ->
                                        AlarmItemCard(
                                            alarm = alarm,
                                            wakeUpCheckSession =
                                            wakeUpCheckSessions.firstOrNull { it.alarmId == alarm.id },
                                            onToggle = { isChecked ->
                                                viewModel.toggleAlarm(alarm, isChecked)
                                            },
                                            onDelete = {
                                                pendingDelete = alarm
                                            },
                                            onEdit = {
                                                viewModel.openEditSheet(alarm)
                                            },
                                            onTest = {
                                                val intent =
                                                    AlarmIntentContract.write(
                                                        Intent(context, AlarmDismissActivity::class.java),
                                                        AlarmLaunchPayload.fromAlarm(alarm, isPreview = true)
                                                    )
                                                context.startActivity(intent)
                                            },
                                            onCancelWakeUpChecks = {
                                                pendingWakeUpCheckCancel = alarm
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (isSheetVisible) {
                            val resolvedSoundName =
                                pickedSoundUri?.let { uriStr ->
                                    runCatching {
                                        RingtoneManager.getRingtone(context, Uri.parse(uriStr))?.getTitle(context)
                                    }.getOrNull()
                                } ?: stringResource(com.example.smartalarmer.R.string.sound_default)

                            AlarmEditSheet(
                                alarm = editingAlarm,
                                onDismiss = { viewModel.closeEditSheet() },
                                onSave = viewModel::saveAlarm,
                                onPickSound = {
                                    val intent =
                                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(
                                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE
                                            )
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

                        if (showPrivacyPolicy) {
                            PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
                        }

                        pendingDelete?.let { alarm ->
                            AlertDialog(
                                onDismissRequest = { pendingDelete = null },
                                title = { Text(stringResource(com.example.smartalarmer.R.string.delete_alarm_title)) },
                                text = {
                                    Text(
                                        stringResource(
                                            com.example.smartalarmer.R.string.delete_alarm_confirmation,
                                            alarm.label.ifBlank {
                                                AlarmTimeFormatter.formatTime(context, alarm.hour, alarm.minute)
                                            }
                                        )
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.deleteAlarm(alarm)
                                            pendingDelete = null
                                        }
                                    ) {
                                        Text(
                                            stringResource(com.example.smartalarmer.R.string.delete),
                                            color = RedError
                                        )
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingDelete = null }) {
                                        Text(stringResource(com.example.smartalarmer.R.string.cancel))
                                    }
                                }
                            )
                        }

                        pendingWakeUpCheckCancel?.let { alarm ->
                            AlertDialog(
                                onDismissRequest = { pendingWakeUpCheckCancel = null },
                                title = {
                                    Text(stringResource(com.example.smartalarmer.R.string.stop_wake_up_checks_title))
                                },
                                text = {
                                    Text(stringResource(com.example.smartalarmer.R.string.stop_wake_up_checks_confirmation))
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.cancelWakeUpChecks(alarm.id)
                                            pendingWakeUpCheckCancel = null
                                        }
                                    ) {
                                        Text(stringResource(com.example.smartalarmer.R.string.stop_wake_up_checks))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingWakeUpCheckCancel = null }) {
                                        Text(stringResource(com.example.smartalarmer.R.string.cancel))
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

@Composable
internal fun MainScreenHeader(onPrivacyPolicyClick: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag(MAIN_HEADER_TAG)) {
        val compact = maxWidth < 480.dp
        if (compact) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MainScreenTitle()
                TextButton(
                    onClick = onPrivacyPolicyClick,
                    modifier = Modifier.align(Alignment.End).testTag(MAIN_HEADER_PRIVACY_TAG)
                ) {
                    Text(stringResource(com.example.smartalarmer.R.string.privacy_policy))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainScreenTitle(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onPrivacyPolicyClick,
                    modifier = Modifier.testTag(MAIN_HEADER_PRIVACY_TAG)
                ) {
                    Text(stringResource(com.example.smartalarmer.R.string.privacy_policy))
                }
            }
        }
    }
}

@Composable
private fun MainScreenTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(com.example.smartalarmer.R.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp).testTag(MAIN_HEADER_TITLE_TAG)
    )
}

internal const val MAIN_HEADER_TAG = "main_header"
internal const val MAIN_HEADER_TITLE_TAG = "main_header_title"
internal const val MAIN_HEADER_PRIVACY_TAG = "main_header_privacy"

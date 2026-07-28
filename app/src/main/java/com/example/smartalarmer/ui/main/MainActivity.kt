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
import androidx.compose.material.icons.filled.Info
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
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartalarmer.alarm.AlarmSoundResolver
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.RoomAlarmRepository
import com.example.smartalarmer.domain.WakeUpCheckCoordinator
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.scheduler.AndroidAlarmSchedulingGateway
import com.example.smartalarmer.scheduler.AndroidDeliveryTestSchedulingGateway
import com.example.smartalarmer.scheduler.AndroidWakeUpCheckSchedulingGateway
import com.example.smartalarmer.service.ActiveAlarmRecovery
import com.example.smartalarmer.ui.theme.*
import com.example.smartalarmer.utils.AlarmCapabilityChecker
import com.example.smartalarmer.utils.AlarmTimeFormatter
import com.example.smartalarmer.utils.AndroidAlarmActivationGate
import com.example.smartalarmer.utils.DeviceUtils
import java.time.Instant
import java.time.ZoneId

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
            wakeUpCheckSessionFlow = database.wakeUpCheckDao().observeAllSessions(),
            deliveryTestScheduler = AndroidDeliveryTestSchedulingGateway(applicationContext)
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmartAlarmerTheme {
                val context = LocalContext.current
                val selectAlarmSoundTitle = stringResource(com.example.smartalarmer.R.string.select_alarm_sound)
                val alarmCards by viewModel.alarmCards.collectAsStateWithLifecycle()
                val isSheetVisible by viewModel.isBottomSheetVisible.collectAsStateWithLifecycle()
                val editingAlarm by viewModel.editingAlarm.collectAsStateWithLifecycle()
                val pendingDeliveryTest by viewModel.pendingDeliveryTest.collectAsStateWithLifecycle()

                var capabilities by remember { mutableStateOf(AlarmCapabilityChecker.check(context)) }
                var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
                var pendingDelete by remember { mutableStateOf<Alarm?>(null) }
                var pendingWakeUpCheckCancel by remember { mutableStateOf<Alarm?>(null) }
                var pendingDisableChoice by remember { mutableStateOf<Alarm?>(null) }
                var pendingPauseDate by remember { mutableStateOf<Alarm?>(null) }
                var pendingDeliveryTestConfirmation by remember { mutableStateOf<Alarm?>(null) }
                val sharedPrefs = remember { context.getSharedPreferences("smart_alarmer_prefs", Context.MODE_PRIVATE) }
                var isXiaomiDismissed by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("xiaomi_warning_dismissed", false)) }
                val isXiaomiDevice = remember { DeviceUtils.isXiaomi() }

                val requestNotificationPermissionLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { capabilities = AlarmCapabilityChecker.check(context) }
                    )

                var pickedSoundUri by rememberSaveable { mutableStateOf<String?>(null) }
                var previewRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }
                var isSoundPreviewPlaying by remember { mutableStateOf(false) }
                val stopSoundPreview = {
                    runCatching { previewRingtone?.stop() }
                    previewRingtone = null
                    isSoundPreviewPlaying = false
                }

                val ringtonePickerLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK) {
                            val uri = AlarmSoundResolver.readPickerSelection(result.data)
                            uri?.let { AlarmSoundResolver.retainReadAccessIfOffered(context, result.data, it) }
                            pickedSoundUri = uri?.toString()
                        }
                    }

                LaunchedEffect(isSheetVisible, editingAlarm?.id) {
                    stopSoundPreview()
                    if (isSheetVisible) {
                        pickedSoundUri = editingAlarm?.soundUri
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        previewRingtone?.stop()
                    }
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
                                        sharedPrefs.edit { putBoolean("xiaomi_warning_dismissed", true) }
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
                                            if (capabilities.notificationsEnabled) {
                                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                    putExtra(
                                                        Settings.EXTRA_CHANNEL_ID,
                                                        com.example.smartalarmer.service.AlarmNotification.CHANNEL_ID
                                                    )
                                                }
                                            } else {
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                }
                                            }
                                        context.startActivity(settingsIntent)
                                    },
                                    onRequestExactAlarmAccess = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                    data = "package:${context.packageName}".toUri()
                                                }
                                            )
                                        }
                                    },
                                    onRequestFullScreenAccess = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                    data = "package:${context.packageName}".toUri()
                                                }
                                            )
                                        }
                                    }
                                )
                            }

                            MainScreenHeader(onPrivacyPolicyClick = { showPrivacyPolicy = true })

                            if (alarmCards.isEmpty()) {
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
                                        color = SecondaryText,
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
                                    items(alarmCards, key = { it.alarm.id }) { cardState ->
                                        val alarm = cardState.alarm
                                        AlarmItemCard(
                                            alarm = alarm,
                                            wakeUpCheckSession = cardState.wakeUpCheckSession,
                                            onToggle = { isChecked ->
                                                if (
                                                    !isChecked &&
                                                    alarm.isEnabled &&
                                                    !alarm.repeatDays.isOneTime
                                                ) {
                                                    pendingDisableChoice = alarm
                                                } else {
                                                    viewModel.toggleAlarm(alarm, isChecked)
                                                }
                                            },
                                            onDelete = {
                                                pendingDelete = alarm
                                            },
                                            onEdit = {
                                                viewModel.openEditSheet(alarm)
                                            },
                                            onTest = {
                                                if (pendingDeliveryTest?.alarmId == alarm.id) {
                                                    viewModel.cancelDeliveryTest()
                                                } else {
                                                    pendingDeliveryTestConfirmation = alarm
                                                }
                                            },
                                            isDeliveryTestPending = pendingDeliveryTest?.alarmId == alarm.id,
                                            deliveryTestEnabled =
                                            pendingDeliveryTest == null ||
                                                pendingDeliveryTest?.alarmId == alarm.id,
                                            onCancelWakeUpChecks = {
                                                pendingWakeUpCheckCancel = alarm
                                            },
                                            onRestoreSchedule = {
                                                viewModel.restoreSuppressedOccurrences(alarm)
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
                                        RingtoneManager.getRingtone(context, uriStr.toUri())?.getTitle(context)
                                    }.getOrNull()
                                } ?: stringResource(com.example.smartalarmer.R.string.sound_default)

                            AlarmEditSheet(
                                alarm = editingAlarm,
                                onDismiss = {
                                    stopSoundPreview()
                                    viewModel.closeEditSheet()
                                },
                                onSave = { draft ->
                                    stopSoundPreview()
                                    viewModel.saveAlarm(draft)
                                },
                                onPickSound = {
                                    stopSoundPreview()
                                    val intent =
                                        AlarmSoundResolver.pickerIntent(
                                            title = selectAlarmSoundTitle,
                                            selectedUri = pickedSoundUri?.toUri()
                                        )
                                    ringtonePickerLauncher.launch(intent)
                                },
                                onPreviewSound = {
                                    if (isSoundPreviewPlaying) {
                                        stopSoundPreview()
                                    } else {
                                        stopSoundPreview()
                                        val ringtone =
                                            runCatching {
                                                val previewUri =
                                                    AlarmSoundResolver
                                                        .playbackCandidates(
                                                            context,
                                                            pickedSoundUri?.let(Uri::parse)
                                                        ).first()
                                                RingtoneManager.getRingtone(context, previewUri)?.also { it.play() }
                                            }.getOrNull()
                                        previewRingtone = ringtone
                                        isSoundPreviewPlaying = ringtone != null
                                    }
                                },
                                isSoundPreviewPlaying = isSoundPreviewPlaying,
                                onResetSound = {
                                    stopSoundPreview()
                                    pickedSoundUri = null
                                },
                                selectedSoundName = resolvedSoundName,
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
                                            color = RedErrorContent
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

                        pendingDeliveryTestConfirmation?.let { alarm ->
                            AlertDialog(
                                onDismissRequest = { pendingDeliveryTestConfirmation = null },
                                title = {
                                    Text(stringResource(com.example.smartalarmer.R.string.delivery_test_confirm_title))
                                },
                                text = {
                                    Text(stringResource(com.example.smartalarmer.R.string.delivery_test_confirm_description))
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.scheduleDeliveryTest(alarm)
                                            pendingDeliveryTestConfirmation = null
                                        }
                                    ) {
                                        Text(stringResource(com.example.smartalarmer.R.string.test_btn))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingDeliveryTestConfirmation = null }) {
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

                        pendingDisableChoice?.let { alarm ->
                            val canSkip =
                                !alarm.repeatDays.isOneTime &&
                                    alarm.scheduledTriggerAtMillis != null
                            val hasActiveChecks =
                                alarmCards.any {
                                    it.alarm.id == alarm.id && it.wakeUpCheckSession != null
                                }
                            AlarmDisableChoiceDialog(
                                alarm = alarm,
                                hasActiveChecks = hasActiveChecks,
                                onSkip =
                                if (canSkip) {
                                    {
                                        viewModel.skipNextOccurrence(alarm)
                                        pendingDisableChoice = null
                                    }
                                } else {
                                    null
                                },
                                onPauseThroughDate =
                                if (canSkip) {
                                    {
                                        pendingPauseDate = alarm
                                        pendingDisableChoice = null
                                    }
                                } else {
                                    null
                                },
                                onDisable = {
                                    viewModel.toggleAlarm(alarm, false)
                                    pendingDisableChoice = null
                                },
                                onDismiss = { pendingDisableChoice = null }
                            )
                        }

                        pendingPauseDate?.let { alarm ->
                            alarm.scheduledTriggerAtMillis?.let { triggerAtMillis ->
                                AlarmPauseDateDialog(
                                    minimumEpochDay =
                                    Instant
                                        .ofEpochMilli(triggerAtMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                        .toEpochDay(),
                                    onConfirm = { epochDay ->
                                        viewModel.pauseThroughDate(alarm, epochDay)
                                        pendingPauseDate = null
                                    },
                                    onDismiss = { pendingPauseDate = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlarmDisableChoiceDialog(
    alarm: Alarm,
    hasActiveChecks: Boolean,
    onSkip: (() -> Unit)?,
    onPauseThroughDate: (() -> Unit)?,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (onSkip != null) {
                        com.example.smartalarmer.R.string.skip_or_disable_alarm_title
                    } else {
                        com.example.smartalarmer.R.string.disable_alarm_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alarm.scheduledTriggerAtMillis?.let { triggerAtMillis ->
                    Text(
                        stringResource(
                            com.example.smartalarmer.R.string.next_alarm_format,
                            AlarmTimeFormatter.formatNextTrigger(context, triggerAtMillis)
                        )
                    )
                }
                if (hasActiveChecks) {
                    Text(
                        stringResource(com.example.smartalarmer.R.string.skip_disable_cancels_checks),
                        color = OrangeWarning
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                onSkip?.let { skip ->
                    Button(
                        onClick = skip,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                        ButtonDefaults.buttonColors(
                            containerColor = IndigoPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(com.example.smartalarmer.R.string.skip_this_occurrence))
                    }
                }
                onPauseThroughDate?.let { pause ->
                    OutlinedButton(
                        onClick = pause,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = IndigoContent
                        ),
                        border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            IndigoContent.copy(alpha = 0.65f)
                        )
                    ) {
                        Text(stringResource(com.example.smartalarmer.R.string.pause_through_date))
                    }
                }
                OutlinedButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = RedErrorContent
                    ),
                    border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        RedErrorContent.copy(alpha = 0.65f)
                    )
                ) {
                    Text(stringResource(com.example.smartalarmer.R.string.turn_alarm_off))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(com.example.smartalarmer.R.string.cancel))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmPauseDateDialog(
    minimumEpochDay: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val selectableDates =
        remember(minimumEpochDay) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = Math.floorDiv(
                    utcTimeMillis,
                    MILLIS_PER_DAY
                ) >= minimumEpochDay
            }
        }
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = minimumEpochDay * MILLIS_PER_DAY,
            selectableDates = selectableDates
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                        onConfirm(Math.floorDiv(selectedDateMillis, MILLIS_PER_DAY))
                    }
                },
                enabled = datePickerState.selectedDateMillis != null
            ) {
                Text(stringResource(com.example.smartalarmer.R.string.pause_alarm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.example.smartalarmer.R.string.cancel))
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = stringResource(com.example.smartalarmer.R.string.pause_alarm_date_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            }
        )
    }
}

@Composable
internal fun MainScreenHeader(onPrivacyPolicyClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(MAIN_HEADER_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainScreenTitle(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onPrivacyPolicyClick,
            modifier = Modifier.testTag(MAIN_HEADER_PRIVACY_TAG)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(com.example.smartalarmer.R.string.privacy_policy)
            )
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
private const val MILLIS_PER_DAY = 86_400_000L

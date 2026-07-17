package com.example.smartalarmer.ui.dismiss

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.R
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.alarm.AlarmProgressContract
import com.example.smartalarmer.alarm.AlarmProgressEvent
import com.example.smartalarmer.alarm.AlarmProgressEventType
import com.example.smartalarmer.receiver.WakeUpCheckCommandReceiver
import com.example.smartalarmer.service.ActiveAlarmRecovery
import com.example.smartalarmer.service.AlarmService
import com.example.smartalarmer.ui.theme.SmartAlarmerTheme

class AlarmDismissActivity : ComponentActivity() {
    private data class DismissConfig(
        val alarmId: Int = -1,
        val puzzlesList: String = "MATH",
        val puzzleCount: Int = 1,
        val alarmLabel: String = "",
        val isPreview: Boolean = false,
        val launchType: AlarmLaunchType = AlarmLaunchType.MAIN,
        val wakeUpCheckNumber: Int = 0,
        val wakeUpCheckTotal: Int = 0,
        val wakeUpCheckToken: String = "",
        val wakeUpChecksEnabled: Boolean = false,
        val wakeUpCheckIntervalMinutes: Int = 5
    )

    private data class CompletionConfig(
        val isWakeUpCheck: Boolean,
        val checkNumber: Int,
        val totalChecks: Int,
        val intervalMinutes: Int
    )

    private var dismissConfig by mutableStateOf(DismissConfig())
    private var completionConfig by mutableStateOf<CompletionConfig?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateConfig(intent)
        applyWindowMode()

        onBackPressedDispatcher.addCallback(this) {
            if (dismissConfig.isPreview) {
                finish()
            }
        }

        setContent {
            val config = dismissConfig
            SmartAlarmerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    val completion = completionConfig
                    if (completion != null) {
                        AlarmCompletionView(completion) { finish() }
                    } else {
                        key(
                            config.alarmId,
                            config.puzzlesList,
                            config.puzzleCount,
                            config.launchType,
                            config.wakeUpCheckNumber
                        ) {
                            AlarmDismissScreen(
                                puzzlesList = config.puzzlesList,
                                puzzleCount = config.puzzleCount,
                                alarmLabel = config.alarmLabel,
                                isWakeUpCheck = config.launchType == AlarmLaunchType.WAKE_UP_CHECK,
                                wakeUpCheckNumber = config.wakeUpCheckNumber,
                                wakeUpCheckTotal = config.wakeUpCheckTotal,
                                onVerifiedProgress = { taskIndex, progress ->
                                    reportProgress(
                                        config = config,
                                        taskIndex = taskIndex,
                                        type = AlarmProgressEventType.VERIFIED_PROGRESS,
                                        progress = progress
                                    )
                                },
                                onIntermediateTaskCompleted = { taskIndex ->
                                    reportProgress(
                                        config = config,
                                        taskIndex = taskIndex,
                                        type = AlarmProgressEventType.INTERMEDIATE_TASK_COMPLETED
                                    )
                                },
                                onDismissComplete = {
                                    if (!dismissConfig.isPreview) {
                                        val commandIntent =
                                            if (dismissConfig.launchType == AlarmLaunchType.WAKE_UP_CHECK) {
                                                WakeUpCheckCommandReceiver.completeIntent(
                                                    this,
                                                    dismissConfig.alarmId,
                                                    dismissConfig.wakeUpCheckToken,
                                                    dismissConfig.wakeUpCheckNumber
                                                )
                                            } else {
                                                WakeUpCheckCommandReceiver.startIntent(this, dismissConfig.alarmId)
                                            }
                                        sendBroadcast(commandIntent)
                                        runCatching {
                                            ActiveAlarmRecovery.markDismissRequested(this, dismissConfig.alarmId)
                                        }
                                        stopService(Intent(this, AlarmService::class.java))
                                    }
                                    if (dismissConfig.isPreview) {
                                        finish()
                                    } else if (
                                        dismissConfig.launchType == AlarmLaunchType.WAKE_UP_CHECK ||
                                        dismissConfig.wakeUpChecksEnabled
                                    ) {
                                        completionConfig =
                                            CompletionConfig(
                                                isWakeUpCheck =
                                                dismissConfig.launchType == AlarmLaunchType.WAKE_UP_CHECK,
                                                checkNumber = dismissConfig.wakeUpCheckNumber,
                                                totalChecks = dismissConfig.wakeUpCheckTotal,
                                                intervalMinutes = dismissConfig.wakeUpCheckIntervalMinutes
                                            )
                                    } else {
                                        finish()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun reportProgress(
        config: DismissConfig,
        taskIndex: Int,
        type: AlarmProgressEventType,
        progress: Float = 0f
    ) {
        if (config.isPreview || config.alarmId < 0) return
        sendBroadcast(
            AlarmProgressContract.intent(
                this,
                AlarmProgressEvent(
                    alarmId = config.alarmId,
                    taskIndex = taskIndex,
                    type = type,
                    progress = progress
                )
            )
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateConfig(intent)
        applyWindowMode()
    }

    private fun updateConfig(intent: Intent) {
        completionConfig = null
        val payload = AlarmIntentContract.read(intent)
        dismissConfig =
            DismissConfig(
                alarmId = payload.alarmId,
                puzzlesList = payload.puzzlesList,
                puzzleCount = payload.puzzleCount,
                alarmLabel = payload.alarmLabel,
                isPreview = payload.isPreview,
                launchType = payload.launchType,
                wakeUpCheckNumber = payload.wakeUpCheckNumber,
                wakeUpCheckTotal = payload.wakeUpCheckTotal,
                wakeUpCheckToken = payload.wakeUpCheckToken,
                wakeUpChecksEnabled = payload.wakeUpChecksEnabled,
                wakeUpCheckIntervalMinutes = payload.wakeUpCheckIntervalMinutes
            )
    }

    private fun applyWindowMode() {
        if (dismissConfig.isPreview) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(false)
                setTurnScreenOn(false)
            } else {
                @Suppress("DEPRECATION")
                window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Composable
    private fun AlarmCompletionView(
        config: CompletionConfig,
        onFinished: () -> Unit
    ) {
        LaunchedEffect(config) {
            kotlinx.coroutines.delay(1_200)
            onFinished()
        }
        val message =
            when {
                !config.isWakeUpCheck ->
                    stringResource(R.string.first_wake_up_check_in_minutes, config.intervalMinutes)
                config.checkNumber >= config.totalChecks ->
                    stringResource(R.string.all_wake_up_checks_complete)
                else -> stringResource(R.string.next_wake_up_check_in_minutes, config.intervalMinutes)
            }
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("✓", color = Color(0xFF4ADE80), fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

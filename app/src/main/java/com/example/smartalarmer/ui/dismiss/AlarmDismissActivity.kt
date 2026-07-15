package com.example.smartalarmer.ui.dismiss

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmProgressContract
import com.example.smartalarmer.alarm.AlarmProgressEvent
import com.example.smartalarmer.alarm.AlarmProgressEventType
import com.example.smartalarmer.service.AlarmService
import com.example.smartalarmer.ui.theme.SmartAlarmerTheme

class AlarmDismissActivity : ComponentActivity() {
    private data class DismissConfig(
        val alarmId: Int = -1,
        val puzzlesList: String = "MATH",
        val puzzleCount: Int = 1,
        val alarmLabel: String = "",
        val isPreview: Boolean = false
    )

    private var dismissConfig by mutableStateOf(DismissConfig())

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
                    key(config.alarmId, config.puzzlesList, config.puzzleCount) {
                        AlarmDismissScreen(
                            puzzlesList = config.puzzlesList,
                            puzzleCount = config.puzzleCount,
                            alarmLabel = config.alarmLabel,
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
        val payload = AlarmIntentContract.read(intent)
        dismissConfig =
            DismissConfig(
                alarmId = payload.alarmId,
                puzzlesList = payload.puzzlesList,
                puzzleCount = payload.puzzleCount,
                alarmLabel = payload.alarmLabel,
                isPreview = payload.isPreview
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
}

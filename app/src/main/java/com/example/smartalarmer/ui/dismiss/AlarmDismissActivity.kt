package com.example.smartalarmer.ui.dismiss

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
import com.example.smartalarmer.service.AlarmService

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
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Disable back button on real alarm, finish on preview
        onBackPressedDispatcher.addCallback(this) {
            if (isPreview) {
                finish()
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

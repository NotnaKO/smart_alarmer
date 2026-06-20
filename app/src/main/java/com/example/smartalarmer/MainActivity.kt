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
import com.example.smartalarmer.theme.SmartAlarmerTheme
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
                    var statusText by remember { mutableStateOf("No test alarm scheduled yet.") }
                    
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Smart Alarmer Settings", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
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
                                    statusText = "Alarm scheduled for ${String.format("%02d:%02d", newAlarm.hour, newAlarm.minute)}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Schedule Test Alarm in 1 Min (All Puzzles)")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

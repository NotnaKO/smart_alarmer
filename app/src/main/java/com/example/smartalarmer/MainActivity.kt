package com.example.smartalarmer

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val alarms by alarmDao.getAllAlarms().collectAsState(initial = emptyList())

                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        scope.launch {
                                            val newAlarm = Alarm(
                                                hour = selectedHour,
                                                minute = selectedMinute,
                                                daysOfWeek = "1,2,3,4,5",
                                                isEnabled = true,
                                                puzzlesList = "MATH,TYPING,MEMORY",
                                                puzzleCount = 3
                                            )
                                            val generatedId = alarmDao.insertAlarm(newAlarm).toInt()
                                            val scheduledAlarm = newAlarm.copy(id = generatedId)
                                            AlarmScheduler.schedule(context, scheduledAlarm)
                                        }
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Alarm", tint = Color.White)
                        }
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Smart Alarmer Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (alarms.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No alarms scheduled. Tap + to add.", color = Color.Gray, fontSize = 16.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(alarms, key = { it.id }) { alarm ->
                                    AlarmItemCard(
                                        alarm = alarm,
                                        onToggle = { isChecked ->
                                            scope.launch {
                                                val updated = alarm.copy(isEnabled = isChecked)
                                                alarmDao.updateAlarm(updated)
                                                if (isChecked) {
                                                    AlarmScheduler.schedule(context, updated)
                                                } else {
                                                    AlarmScheduler.cancel(context, updated)
                                                }
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                AlarmScheduler.cancel(context, alarm)
                                                alarmDao.deleteAlarm(alarm)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmItemCard(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Required puzzles: Math, Memory, Typing",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                      )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Alarm",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

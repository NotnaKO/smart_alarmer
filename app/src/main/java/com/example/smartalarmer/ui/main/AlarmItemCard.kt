package com.example.smartalarmer.ui.main

import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.data.AlarmScheduleStatus
import com.example.smartalarmer.data.scheduleHealth
import com.example.smartalarmer.domain.AlarmDay
import com.example.smartalarmer.domain.PuzzleType
import com.example.smartalarmer.domain.puzzleSelection
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.scheduler.AlarmTimeCalculator
import com.example.smartalarmer.ui.theme.*
import com.example.smartalarmer.utils.AlarmTimeFormatter
import java.time.Clock
import java.time.ZoneId

@Composable
fun AlarmItemCard(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onTest: () -> Unit = {}
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val daysList = alarm.repeatDays.values.sortedBy(AlarmDay::isoValue)
    val daysSummary =
        when {
            daysList.isEmpty() -> stringResource(com.example.smartalarmer.R.string.one_time)
            daysList.size == 7 -> stringResource(com.example.smartalarmer.R.string.every_day)
            daysList.containsAll(
                AlarmDay.entries.take(5)
            ) &&
                daysList.size == 5 -> stringResource(com.example.smartalarmer.R.string.weekdays)
            daysList.containsAll(
                AlarmDay.entries.takeLast(2)
            ) &&
                daysList.size == 2 -> stringResource(com.example.smartalarmer.R.string.weekends)
            else -> {
                val names =
                    listOf(
                        stringResource(com.example.smartalarmer.R.string.day_mon),
                        stringResource(com.example.smartalarmer.R.string.day_tue),
                        stringResource(com.example.smartalarmer.R.string.day_wed),
                        stringResource(com.example.smartalarmer.R.string.day_thu),
                        stringResource(com.example.smartalarmer.R.string.day_fri),
                        stringResource(com.example.smartalarmer.R.string.day_sat),
                        stringResource(com.example.smartalarmer.R.string.day_sun)
                    )
                daysList.joinToString(", ") { names[it.isoValue - 1] }
            }
        }

    val puzzlesText =
        alarm.puzzleSelection.values
            .map { puzzle ->
                val resId =
                    when (puzzle) {
                        PuzzleType.MATH -> com.example.smartalarmer.R.string.puzzle_math
                        PuzzleType.MEMORY -> com.example.smartalarmer.R.string.puzzle_memory
                        PuzzleType.TYPING -> com.example.smartalarmer.R.string.puzzle_typing
                        PuzzleType.SHAKE -> com.example.smartalarmer.R.string.puzzle_shake
                    }
                stringResource(resId)
            }.joinToString(", ")
    val puzzleCountText =
        resources.getQuantityString(
            com.example.smartalarmer.R.plurals.puzzles_plural,
            alarm.puzzleCount,
            alarm.puzzleCount
        )

    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag(ALARM_CARD_TAG)
            .border(1.dp, CardBorderGlass, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBgGlass),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (alarm.label.isNotEmpty()) {
                    Text(
                        text = alarm.label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AlarmTimeFormatter.formatTime(context, alarm.hour, alarm.minute),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.LightGray
                    )
                } else {
                    Text(
                        text = AlarmTimeFormatter.formatTime(context, alarm.hour, alarm.minute),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val soundName =
                    alarm.soundUri?.let { uriStr ->
                        runCatching {
                            RingtoneManager.getRingtone(context, Uri.parse(uriStr))?.getTitle(context)
                        }.getOrNull()
                    } ?: stringResource(com.example.smartalarmer.R.string.sound_default)
                Text(
                    text = "$daysSummary • $puzzlesText ($puzzleCountText) • $soundName",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    modifier = Modifier.testTag(ALARM_CARD_SUMMARY_TAG)
                )
                if (alarm.isEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    when (alarm.scheduleHealth) {
                        AlarmScheduleStatus.SCHEDULED -> {
                            val nextTrigger =
                                alarm.scheduledTriggerAtMillis ?: remember(alarm) {
                                    runCatching {
                                        AlarmTimeCalculator(Clock.systemUTC(), ZoneId.systemDefault())
                                            .nextTrigger(alarm)
                                            .toEpochMilli()
                                    }.getOrNull()
                                }
                            nextTrigger?.let { triggerAtMillis ->
                                Text(
                                    text =
                                    stringResource(
                                        com.example.smartalarmer.R.string.next_alarm_format,
                                        AlarmTimeFormatter.formatNextTrigger(context, triggerAtMillis)
                                    ),
                                    fontSize = 12.sp,
                                    color = GreenSuccess
                                )
                            }
                        }
                        AlarmScheduleStatus.PERMISSION_REQUIRED ->
                            ScheduleHealthText(com.example.smartalarmer.R.string.schedule_permission_required)
                        AlarmScheduleStatus.FAILED ->
                            ScheduleHealthText(com.example.smartalarmer.R.string.schedule_failed)
                        AlarmScheduleStatus.UNKNOWN,
                        AlarmScheduleStatus.DISABLED
                        -> ScheduleHealthText(com.example.smartalarmer.R.string.schedule_unverified, OrangeWarning)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag(ALARM_CARD_ACTIONS_TAG),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess),
                    border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription =
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.test_btn),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.test_btn),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors =
                    SwitchDefaults.colors(
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
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription =
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.delete_alarm_desc)
                    )
                }
            }
        }
    }
}

internal const val ALARM_CARD_TAG = "alarm_card"
internal const val ALARM_CARD_SUMMARY_TAG = "alarm_card_summary"
internal const val ALARM_CARD_ACTIONS_TAG = "alarm_card_actions"

@Composable
private fun ScheduleHealthText(
    textResource: Int,
    color: Color = RedError
) {
    Text(
        text = stringResource(textResource),
        fontSize = 12.sp,
        color = color,
        fontWeight = FontWeight.SemiBold
    )
}

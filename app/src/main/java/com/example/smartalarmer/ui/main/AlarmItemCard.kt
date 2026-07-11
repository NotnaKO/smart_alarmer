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

  @Composable
  fun AlarmItemCard(
      alarm: Alarm,
      onToggle: (Boolean) -> Unit,
      onDelete: () -> Unit,
      onEdit: () -> Unit = {},
      onTest: () -> Unit = {}
  ) {
      val context = LocalContext.current
      val daysList = alarm.repeatDays.values.sortedBy(AlarmDay::isoValue)
      val daysSummary = when {
          daysList.isEmpty() -> stringResource(com.example.smartalarmer.R.string.one_time)
          daysList.size == 7 -> stringResource(com.example.smartalarmer.R.string.every_day)
          daysList.containsAll(AlarmDay.entries.take(5)) && daysList.size == 5 -> stringResource(com.example.smartalarmer.R.string.weekdays)
          daysList.containsAll(AlarmDay.entries.takeLast(2)) && daysList.size == 2 -> stringResource(com.example.smartalarmer.R.string.weekends)
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
              daysList.joinToString(", ") { names[it.isoValue - 1] }
          }
      }

      val puzzlesText = alarm.puzzleSelection.values
          .map { puzzle ->
              val resId = when (puzzle) {
                  PuzzleType.MATH -> com.example.smartalarmer.R.string.puzzle_math
                  PuzzleType.MEMORY -> com.example.smartalarmer.R.string.puzzle_memory
                  PuzzleType.TYPING -> com.example.smartalarmer.R.string.puzzle_typing
                  PuzzleType.SHAKE -> com.example.smartalarmer.R.string.puzzle_shake
              }
              stringResource(resId)
          }
          .joinToString(", ")
      val gradualText = if (alarm.isGradualVolume) " • " + stringResource(com.example.smartalarmer.R.string.gradual_volume) else ""
      val puzzleCountText = context.resources.getQuantityString(
          com.example.smartalarmer.R.plurals.puzzles_plural,
          alarm.puzzleCount,
          alarm.puzzleCount
      )

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
                          text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                          fontSize = 18.sp,
                          fontWeight = FontWeight.Medium,
                          color = Color.LightGray
                      )
                  } else {
                      Text(
                          text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
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
                      text = "$daysSummary • $puzzlesText ($puzzleCountText)$gradualText • $soundName",
                      fontSize = 13.sp,
                      color = Color.LightGray
                  )
              }
              FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
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


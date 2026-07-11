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

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun AlarmEditSheet(
      alarm: Alarm?,
      onDismiss: () -> Unit,
      onSave: (hour: Int, minute: Int, daysOfWeek: String, puzzlesList: String, puzzleCount: Int, isGradualVolume: Boolean, label: String, soundUri: String?) -> Unit,
      onPickSound: () -> Unit,
      selectedSoundName: String,
      initialLabel: String,
      pickedSoundUri: String?,
      shakeSensorAvailable: Boolean = AndroidShakeSensorProvider(LocalContext.current).isAvailable
  ) {
      val context = LocalContext.current
      var hour by rememberSaveable(alarm?.id) { mutableStateOf(alarm?.hour ?: 8) }
      var minute by rememberSaveable(alarm?.id) { mutableStateOf(alarm?.minute ?: 0) }
      
      val initialDays = alarm?.repeatDays?.values.orEmpty()
      val selectedDays = rememberSaveable(
          alarm?.id,
          saver = listSaver(
              save = { days -> days.map(AlarmDay::name) },
              restore = { names -> mutableStateListOf<AlarmDay>().apply { addAll(names.map(AlarmDay::valueOf)) } }
          )
      ) { mutableStateListOf<AlarmDay>().apply { addAll(initialDays) } }

      val puzzleTypes = remember(shakeSensorAvailable) {
          buildList {
              addAll(listOf(PuzzleType.MATH, PuzzleType.MEMORY, PuzzleType.TYPING))
              if (shakeSensorAvailable) add(PuzzleType.SHAKE)
          }
      }
      val initialPuzzles = alarm?.puzzleSelection?.values
          ?.filter { it in puzzleTypes }
          ?.toSet()
          .orEmpty()
          .ifEmpty { setOf(PuzzleType.MATH) }
      val selectedPuzzles = rememberSaveable(
          alarm?.id,
          saver = listSaver(
              save = { puzzles -> puzzles.map(PuzzleType::name) },
              restore = { names -> mutableStateListOf<PuzzleType>().apply { addAll(names.map(PuzzleType::valueOf)) } }
          )
      ) { mutableStateListOf<PuzzleType>().apply { addAll(initialPuzzles) } }

      var puzzleCount by rememberSaveable(alarm?.id) {
          mutableStateOf((alarm?.puzzleCount ?: 1).coerceIn(1, initialPuzzles.size))
      }
      var isGradualVolume by rememberSaveable(alarm?.id) { mutableStateOf(alarm?.isGradualVolume ?: true) }

      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

      ModalBottomSheet(
          onDismissRequest = onDismiss,
          sheetState = sheetState,
          containerColor = BottomSheetBg,
          dragHandle = { BottomSheetDefaults.DragHandle(color = BottomSheetDrag) }
      ) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
                  .padding(24.dp)
                  .navigationBarsPadding(),
              verticalArrangement = Arrangement.spacedBy(20.dp)
          ) {
              Text(
                  text = if (alarm == null) androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.new_alarm) else androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.edit_alarm),
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
              )

              var label by rememberSaveable(alarm?.id) { mutableStateOf(initialLabel) }
              OutlinedTextField(
                  value = label,
                  onValueChange = { label = it },
                  label = { Text(stringResource(com.example.smartalarmer.R.string.label_placeholder)) },
                  modifier = Modifier.fillMaxWidth(),
                  colors = OutlinedTextFieldDefaults.colors(
                      focusedTextColor = Color.White,
                      unfocusedTextColor = Color.White,
                      focusedBorderColor = IndigoPrimary,
                      unfocusedBorderColor = CardBorderGlass
                  )
              )

              // Time Button
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                          TimePickerDialog(
                              context,
                              { _, selectedHour, selectedMinute ->
                                  hour = selectedHour
                                  minute = selectedMinute
                              },
                              hour,
                              minute,
                              true
                          ).show()
                      }
                      .border(1.dp, CardBorderGlass, RoundedCornerShape(16.dp))
                      .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.time_label), color = Color.LightGray, fontSize = 16.sp)
                  Text(
                      text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                      color = Color.White,
                      fontSize = 20.sp,
                      fontWeight = FontWeight.Bold
                  )
              }

              // Sound Button
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clickable { onPickSound() }
                      .border(1.dp, CardBorderGlass, RoundedCornerShape(16.dp))
                      .padding(16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text(stringResource(com.example.smartalarmer.R.string.sound_label), color = Color.LightGray, fontSize = 16.sp)
                  Text(
                      text = selectedSoundName,
                      color = Color.White,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold
                  )
              }

              // Days of week
              Column {
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.repeat_days_label), color = Color.LightGray, fontSize = 14.sp)
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                      val dayLabels = listOf(
                          stringResource(com.example.smartalarmer.R.string.day_m),
                          stringResource(com.example.smartalarmer.R.string.day_t),
                          stringResource(com.example.smartalarmer.R.string.day_w),
                          stringResource(com.example.smartalarmer.R.string.day_th),
                          stringResource(com.example.smartalarmer.R.string.day_f),
                          stringResource(com.example.smartalarmer.R.string.day_sa),
                          stringResource(com.example.smartalarmer.R.string.day_su)
                      )
                      val dayNames = listOf(
                          stringResource(com.example.smartalarmer.R.string.day_mon),
                          stringResource(com.example.smartalarmer.R.string.day_tue),
                          stringResource(com.example.smartalarmer.R.string.day_wed),
                          stringResource(com.example.smartalarmer.R.string.day_thu),
                          stringResource(com.example.smartalarmer.R.string.day_fri),
                          stringResource(com.example.smartalarmer.R.string.day_sat),
                          stringResource(com.example.smartalarmer.R.string.day_sun)
                      )
                      AlarmDay.entries.forEachIndexed { index, day ->
                          val isSelected = selectedDays.contains(day)
                          Box(
                              modifier = Modifier
                                  .size(48.dp)
                                  .background(
                                      if (isSelected) IndigoPrimary else KeyButtonBg,
                                      CircleShape
                                  )
                                  .clickable {
                                      if (isSelected) selectedDays.remove(day) else selectedDays.add(day)
                                  }
                                  .semantics {
                                      contentDescription = dayNames[index]
                                      selected = isSelected
                                      role = Role.Checkbox
                                  },
                              contentAlignment = Alignment.Center
                          ) {
                              Text(
                                  text = dayLabels[index],
                                  color = if (isSelected) Color.White else Color.Gray,
                                  fontWeight = FontWeight.Bold
                              )
                          }
                      }
                  }
                           // Puzzle selection
              Column {
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.dismiss_puzzles_label), color = Color.LightGray, fontSize = 14.sp)
                  Spacer(modifier = Modifier.height(8.dp))
                  FlowRow(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                      puzzleTypes.forEach { type ->
                          val isSelected = selectedPuzzles.contains(type)
                          val displayName = when (type) {
                              PuzzleType.MATH -> stringResource(com.example.smartalarmer.R.string.puzzle_math)
                              PuzzleType.MEMORY -> stringResource(com.example.smartalarmer.R.string.puzzle_memory)
                              PuzzleType.TYPING -> stringResource(com.example.smartalarmer.R.string.puzzle_typing)
                              PuzzleType.SHAKE -> stringResource(com.example.smartalarmer.R.string.puzzle_shake)
                          }
                          FilterChip(
                              selected = isSelected,
                              onClick = {
                                  if (isSelected) {
                                      if (selectedPuzzles.size > 1) {
                                          selectedPuzzles.remove(type)
                                          if (puzzleCount > selectedPuzzles.size) {
                                              puzzleCount = selectedPuzzles.size
                                          }
                                      }
                                  } else {
                                      selectedPuzzles.add(type)
                                  }
                              },
                              label = { Text(displayName) },
                              colors = FilterChipDefaults.filterChipColors(
                                  selectedContainerColor = IndigoPrimary,
                                  selectedLabelColor = Color.White,
                                  containerColor = KeyButtonBg,
                                  labelColor = Color.Gray
                              )
                          )
                      }
                  }
              }

              // Puzzle Count Stepper
              val decreasePuzzleCountDescription = stringResource(com.example.smartalarmer.R.string.decrease_puzzle_count)
              val increasePuzzleCountDescription = stringResource(com.example.smartalarmer.R.string.increase_puzzle_count)
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.puzzles_required), color = Color.LightGray, fontSize = 16.sp)
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                      Button(
                          onClick = { if (puzzleCount > 1) puzzleCount-- },
                          colors = ButtonDefaults.buttonColors(containerColor = KeyButtonBg),
                          contentPadding = PaddingValues(0.dp),
                          modifier = Modifier.size(48.dp).semantics {
                              contentDescription = decreasePuzzleCountDescription
                          },
                          shape = CircleShape
                      ) {
                          Text("-", color = Color.White, fontSize = 18.sp)
                      }
                      Text(text = puzzleCount.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                      Button(
                          onClick = { if (puzzleCount < selectedPuzzles.size) puzzleCount++ },
                          colors = ButtonDefaults.buttonColors(containerColor = KeyButtonBg),
                          contentPadding = PaddingValues(0.dp),
                          modifier = Modifier.size(48.dp).semantics {
                              contentDescription = increasePuzzleCountDescription
                          },
                          shape = CircleShape
                      ) {
                          Text("+", color = Color.White, fontSize = 18.sp)
                      }
                  }
              }

              // Gradual Volume toggle
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
              ) {
                  Column {
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.gradual_volume), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.gradual_volume_desc), color = Color.LightGray, fontSize = 12.sp)
                  }
                  Switch(
                      checked = isGradualVolume,
                      onCheckedChange = { isGradualVolume = it },
                      colors = SwitchDefaults.colors(
                          checkedThumbColor = IndigoPrimary,
                          checkedTrackColor = IndigoPrimary.copy(alpha = 0.3f),
                          uncheckedThumbColor = Color.Gray,
                          uncheckedTrackColor = CardBorderGlass
                      )
                  )
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Actions
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                  OutlinedButton(
                      onClick = onDismiss,
                      modifier = Modifier.weight(1f),
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                      border = BorderStroke(1.dp, BottomSheetDrag)
                  ) {
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.cancel))
                  }
                  Button(
                      onClick = {
                          val daysCsv = AlarmDays.of(selectedDays).encoded
                          val puzzlesCsv = PuzzleSelection.of(selectedPuzzles).encoded
                          onSave(hour, minute, daysCsv, puzzlesCsv, puzzleCount, isGradualVolume, label, pickedSoundUri)
                      },
                      modifier = Modifier.weight(1f),
                      colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                  ) {
                      Text(androidx.compose.ui.res.stringResource(com.example.smartalarmer.R.string.save), color = Color.White)
                  }
              }
          }
      }
  }
}

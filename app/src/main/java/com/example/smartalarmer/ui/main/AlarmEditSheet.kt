package com.example.smartalarmer.ui.main

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.domain.AlarmDay
import com.example.smartalarmer.domain.AlarmDays
import com.example.smartalarmer.domain.AlarmDraft
import com.example.smartalarmer.domain.PuzzleSelection
import com.example.smartalarmer.domain.PuzzleType
import com.example.smartalarmer.domain.puzzleSelection
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.puzzle.AndroidShakeSensorProvider
import com.example.smartalarmer.ui.theme.*
import com.example.smartalarmer.utils.AlarmTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheet(
    alarm: Alarm?,
    onDismiss: () -> Unit,
    onSave: (AlarmDraft) -> Unit,
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
    val selectedDays =
        rememberSaveable(
            alarm?.id,
            saver =
            listSaver(
                save = { days -> days.map(AlarmDay::name) },
                restore = { names -> mutableStateListOf<AlarmDay>().apply { addAll(names.map(AlarmDay::valueOf)) } }
            )
        ) { mutableStateListOf<AlarmDay>().apply { addAll(initialDays) } }

    val puzzleTypes =
        remember(shakeSensorAvailable) {
            buildList {
                addAll(listOf(PuzzleType.MATH, PuzzleType.MEMORY, PuzzleType.TYPING))
                if (shakeSensorAvailable) add(PuzzleType.SHAKE)
            }
        }
    val initialPuzzles =
        alarm
            ?.puzzleSelection
            ?.values
            ?.filter { it in puzzleTypes }
            ?.toSet()
            .orEmpty()
            .ifEmpty { setOf(PuzzleType.MATH) }
    val selectedPuzzles =
        rememberSaveable(
            alarm?.id,
            saver =
            listSaver(
                save = { puzzles -> puzzles.map(PuzzleType::name) },
                restore = { names -> mutableStateListOf<PuzzleType>().apply { addAll(names.map(PuzzleType::valueOf)) } }
            )
        ) { mutableStateListOf<PuzzleType>().apply { addAll(initialPuzzles) } }

    var puzzleCount by rememberSaveable(alarm?.id) {
        mutableStateOf((alarm?.puzzleCount ?: 1).coerceIn(1, initialPuzzles.size))
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BottomSheetBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BottomSheetDrag) }
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text =
                if (alarm ==
                    null
                ) {
                    androidx.compose.ui.res
                        .stringResource(com.example.smartalarmer.R.string.new_alarm)
                } else {
                    androidx.compose.ui.res
                        .stringResource(com.example.smartalarmer.R.string.edit_alarm)
                },
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
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = IndigoPrimary,
                    unfocusedBorderColor = CardBorderGlass
                )
            )

            // Time Button
            Row(
                modifier =
                Modifier
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
                            android.text.format.DateFormat
                                .is24HourFormat(context)
                        ).show()
                    }.border(1.dp, CardBorderGlass, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    androidx.compose.ui.res
                        .stringResource(com.example.smartalarmer.R.string.time_label),
                    color = Color.LightGray,
                    fontSize = 16.sp
                )
                Text(
                    text = AlarmTimeFormatter.formatTime(context, hour, minute),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sound Button
            Row(
                modifier =
                Modifier
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
                Text(
                    androidx.compose.ui.res
                        .stringResource(com.example.smartalarmer.R.string.repeat_days_label),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dayLabels =
                        listOf(
                            stringResource(com.example.smartalarmer.R.string.day_m),
                            stringResource(com.example.smartalarmer.R.string.day_t),
                            stringResource(com.example.smartalarmer.R.string.day_w),
                            stringResource(com.example.smartalarmer.R.string.day_th),
                            stringResource(com.example.smartalarmer.R.string.day_f),
                            stringResource(com.example.smartalarmer.R.string.day_sa),
                            stringResource(com.example.smartalarmer.R.string.day_su)
                        )
                    val dayNames =
                        listOf(
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
                            modifier =
                            Modifier
                                .size(48.dp)
                                .background(
                                    if (isSelected) IndigoPrimary else KeyButtonBg,
                                    CircleShape
                                ).clickable {
                                    if (isSelected) selectedDays.remove(day) else selectedDays.add(day)
                                }.semantics {
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
                    Text(
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.dismiss_puzzles_label),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        puzzleTypes.forEach { type ->
                            val isSelected = selectedPuzzles.contains(type)
                            val displayName =
                                when (type) {
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
                                colors =
                                FilterChipDefaults.filterChipColors(
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
                    Text(
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.puzzles_required),
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { if (puzzleCount > 1) puzzleCount-- },
                            colors = ButtonDefaults.buttonColors(containerColor = KeyButtonBg),
                            contentPadding = PaddingValues(0.dp),
                            modifier =
                            Modifier.size(48.dp).semantics {
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
                            modifier =
                            Modifier.size(48.dp).semantics {
                                contentDescription = increasePuzzleCountDescription
                            },
                            shape = CircleShape
                        ) {
                            Text("+", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }

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
                        Text(
                            androidx.compose.ui.res
                                .stringResource(com.example.smartalarmer.R.string.cancel)
                        )
                    }
                    Button(
                        onClick = {
                            val puzzleSelection = PuzzleSelection.of(selectedPuzzles)
                            onSave(
                                AlarmDraft(
                                    hour = hour,
                                    minute = minute,
                                    repeatDays = AlarmDays.of(selectedDays),
                                    puzzleSelection = puzzleSelection,
                                    puzzleCount = puzzleCount.coerceIn(1, puzzleSelection.values.size),
                                    label = label,
                                    soundUri = pickedSoundUri
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text(
                            androidx.compose.ui.res
                                .stringResource(com.example.smartalarmer.R.string.save),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

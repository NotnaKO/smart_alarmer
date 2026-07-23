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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.domain.AlarmDay
import com.example.smartalarmer.domain.AlarmDays
import com.example.smartalarmer.domain.AlarmDraft
import com.example.smartalarmer.domain.AlarmVolumeRamp
import com.example.smartalarmer.domain.AlarmWeekParity
import com.example.smartalarmer.domain.PuzzleSelection
import com.example.smartalarmer.domain.PuzzleType
import com.example.smartalarmer.domain.WakeUpCheckConfig
import com.example.smartalarmer.domain.puzzleSelection
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.domain.repeatWeekParity
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
    onPreviewSound: () -> Unit = {},
    isSoundPreviewPlaying: Boolean = false,
    onResetSound: () -> Unit = {},
    selectedSoundName: String,
    initialLabel: String,
    pickedSoundUri: String?,
    shakeSensorAvailable: Boolean = AndroidShakeSensorProvider(LocalContext.current).isAvailable
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var hour by rememberSaveable(alarm?.id) { mutableIntStateOf(alarm?.hour ?: 8) }
    var minute by rememberSaveable(alarm?.id) { mutableIntStateOf(alarm?.minute ?: 0) }
    val labelLimit = maxOf(ALARM_LABEL_MAX_LENGTH, initialLabel.length)
    var label by rememberSaveable(alarm?.id) { mutableStateOf(initialLabel) }

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
    var repeatEnabled by rememberSaveable(alarm?.id) {
        mutableStateOf(initialDays.isNotEmpty())
    }
    var repeatWeekParity by rememberSaveable(alarm?.id) {
        mutableStateOf(alarm?.repeatWeekParity ?: AlarmWeekParity.EVERY)
    }

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
        mutableIntStateOf((alarm?.puzzleCount ?: 1).coerceIn(1, initialPuzzles.size))
    }
    var volumeRampSeconds by rememberSaveable(alarm?.id) {
        mutableIntStateOf(AlarmVolumeRamp.sanitize(alarm?.volumeRampSeconds ?: AlarmVolumeRamp.DEFAULT_SECONDS))
    }
    var wakeUpChecksEnabled by rememberSaveable(alarm?.id) {
        mutableStateOf(alarm?.wakeUpChecksEnabled ?: false)
    }
    var wakeUpCheckCount by rememberSaveable(alarm?.id) {
        mutableIntStateOf(
            (alarm?.wakeUpCheckCount ?: WakeUpCheckConfig.DEFAULT_COUNT)
                .coerceIn(WakeUpCheckConfig.COUNT_RANGE)
        )
    }
    var wakeUpCheckIntervalMinutes by rememberSaveable(alarm?.id) {
        mutableIntStateOf(
            alarm?.wakeUpCheckIntervalMinutes
                ?.takeIf { it in WakeUpCheckConfig.INTERVAL_OPTIONS_MINUTES }
                ?: WakeUpCheckConfig.DEFAULT_INTERVAL_MINUTES
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val saveDraft = {
        val puzzleSelection = PuzzleSelection.of(selectedPuzzles)
        onSave(
            AlarmDraft(
                hour = hour,
                minute = minute,
                repeatDays = if (repeatEnabled) AlarmDays.of(selectedDays) else AlarmDays.ONE_TIME,
                repeatWeekParity = if (repeatEnabled) repeatWeekParity else AlarmWeekParity.EVERY,
                puzzleSelection = puzzleSelection,
                puzzleCount = puzzleCount.coerceIn(1, puzzleSelection.values.size),
                label = label,
                soundUri = pickedSoundUri,
                wakeUpChecksEnabled = wakeUpChecksEnabled,
                wakeUpCheckCount = wakeUpCheckCount,
                wakeUpCheckIntervalMinutes = wakeUpCheckIntervalMinutes,
                volumeRampSeconds = volumeRampSeconds
            )
        )
    }

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
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .testTag(ALARM_EDITOR_CONTENT_TAG)
        ) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
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

                OutlinedTextField(
                    value = label,
                    onValueChange = { updated ->
                        label = updated.take(labelLimit)
                    },
                    label = { Text(stringResource(com.example.smartalarmer.R.string.label_placeholder)) },
                    supportingText = {
                        Text(
                            stringResource(
                                com.example.smartalarmer.R.string.label_character_count,
                                label.length,
                                labelLimit
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = IndigoContent,
                        focusedBorderColor = IndigoContent,
                        unfocusedBorderColor = CardBorderGlass,
                        focusedLabelColor = IndigoContent,
                        unfocusedLabelColor = SecondaryText,
                        focusedSupportingTextColor = SecondaryText,
                        unfocusedSupportingTextColor = SecondaryText
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
                        .padding(16.dp)
                        .testTag(ALARM_EDITOR_TIME_ROW_TAG),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.time_label),
                        color = SecondaryText,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
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
                        .padding(16.dp)
                        .testTag(ALARM_EDITOR_SOUND_ROW_TAG),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(com.example.smartalarmer.R.string.sound_label),
                        color = SecondaryText,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Text(
                        text = selectedSoundName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onPreviewSound,
                        colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = IndigoContent,
                            disabledContentColor = DisabledControlText
                        )
                    ) {
                        Text(
                            stringResource(
                                if (isSoundPreviewPlaying) {
                                    com.example.smartalarmer.R.string.stop_sound_preview
                                } else {
                                    com.example.smartalarmer.R.string.preview_sound
                                }
                            )
                        )
                    }
                    TextButton(
                        onClick = onResetSound,
                        enabled = pickedSoundUri != null,
                        colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = IndigoContent,
                            disabledContentColor = DisabledControlText
                        )
                    ) {
                        Text(stringResource(com.example.smartalarmer.R.string.use_default_sound))
                    }
                }

                // Repeat schedule
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, CardBorderGlass, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .testTag(ALARM_EDITOR_REPEAT_TAG),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(com.example.smartalarmer.R.string.repeat_days_label),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(com.example.smartalarmer.R.string.repeat_alarm_description),
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = repeatEnabled,
                            onCheckedChange = { enabled ->
                                repeatEnabled = enabled
                                if (enabled && selectedDays.isEmpty()) {
                                    selectedDays.addAll(AlarmDay.entries)
                                }
                                if (!enabled) {
                                    repeatWeekParity = AlarmWeekParity.EVERY
                                }
                            },
                            colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = IndigoPrimary,
                                checkedTrackColor = IndigoPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = CardBorderGlass
                            )
                        )
                    }

                    if (repeatEnabled) {
                        HorizontalDivider(color = CardBorderGlass)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                stringResource(com.example.smartalarmer.R.string.weekdays) to
                                    AlarmDay.entries.take(5).toSet(),
                                stringResource(com.example.smartalarmer.R.string.weekends) to
                                    AlarmDay.entries.takeLast(2).toSet(),
                                stringResource(com.example.smartalarmer.R.string.every_day) to
                                    AlarmDay.entries.toSet()
                            ).forEach { (label, days) ->
                                FilterChip(
                                    selected = selectedDays.toSet() == days,
                                    onClick = {
                                        selectedDays.clear()
                                        selectedDays.addAll(days)
                                    },
                                    label = { Text(label) },
                                    colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = KeyButtonBg,
                                        labelColor = InactiveControlText
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().testTag(ALARM_EDITOR_DAYS_TAG),
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
                                        .weight(1f)
                                        .height(48.dp)
                                        .clickable {
                                            if (isSelected) {
                                                selectedDays.remove(day)
                                                if (selectedDays.isEmpty()) {
                                                    repeatEnabled = false
                                                    repeatWeekParity = AlarmWeekParity.EVERY
                                                }
                                            } else {
                                                selectedDays.add(day)
                                            }
                                        }.semantics {
                                            contentDescription = dayNames[index]
                                            selected = isSelected
                                            role = Role.Checkbox
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier =
                                        Modifier
                                            .size(40.dp)
                                            .background(
                                                if (isSelected) IndigoPrimary else KeyButtonBg,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayLabels[index],
                                            color = if (isSelected) Color.White else InactiveControlText,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        if (selectedDays.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(com.example.smartalarmer.R.string.repeat_week_pattern_label),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth().testTag(ALARM_EDITOR_WEEK_PARITY_TAG)
                            ) {
                                AlarmWeekParity.entries.forEachIndexed { index, parity ->
                                    SegmentedButton(
                                        selected = repeatWeekParity == parity,
                                        onClick = { repeatWeekParity = parity },
                                        shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = AlarmWeekParity.entries.size
                                        ),
                                        colors =
                                        SegmentedButtonDefaults.colors(
                                            activeContainerColor = IndigoPrimary,
                                            activeContentColor = Color.White,
                                            activeBorderColor = IndigoPrimary,
                                            inactiveContainerColor = KeyButtonBg,
                                            inactiveContentColor = Color.White,
                                            inactiveBorderColor = CardBorderGlass
                                        ),
                                        icon = {}
                                    ) {
                                        Text(
                                            stringResource(
                                                when (parity) {
                                                    AlarmWeekParity.EVERY ->
                                                        com.example.smartalarmer.R.string.repeat_week_every
                                                    AlarmWeekParity.ODD ->
                                                        com.example.smartalarmer.R.string.repeat_week_odd
                                                    AlarmWeekParity.EVEN ->
                                                        com.example.smartalarmer.R.string.repeat_week_even
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Puzzle selection
                Column {
                    Text(
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.dismiss_puzzles_label),
                        color = SecondaryText,
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
                                    labelColor = InactiveControlText
                                )
                            )
                        }
                    }
                }

                // Puzzle Count Stepper
                val decreasePuzzleCountDescription = stringResource(com.example.smartalarmer.R.string.decrease_puzzle_count)
                val increasePuzzleCountDescription = stringResource(com.example.smartalarmer.R.string.increase_puzzle_count)
                Row(
                    modifier = Modifier.fillMaxWidth().testTag(ALARM_EDITOR_PUZZLE_COUNT_TAG),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        androidx.compose.ui.res
                            .stringResource(com.example.smartalarmer.R.string.puzzles_required),
                        color = SecondaryText,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, CardBorderGlass, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                            .testTag(ALARM_EDITOR_WAKE_UP_CHECKS_TAG),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(com.example.smartalarmer.R.string.wake_up_checks_label),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(com.example.smartalarmer.R.string.wake_up_checks_description),
                                    color = SecondaryText,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = wakeUpChecksEnabled,
                                onCheckedChange = { wakeUpChecksEnabled = it },
                                colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = IndigoPrimary,
                                    checkedTrackColor = IndigoPrimary.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = CardBorderGlass
                                )
                            )
                        }

                        if (wakeUpChecksEnabled) {
                            HorizontalDivider(color = CardBorderGlass)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(com.example.smartalarmer.R.string.wake_up_check_count_label),
                                    color = SecondaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (wakeUpCheckCount > WakeUpCheckConfig.COUNT_RANGE.first) {
                                                wakeUpCheckCount--
                                            }
                                        },
                                        modifier = Modifier.size(48.dp),
                                        colors =
                                        IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = KeyButtonBg,
                                            contentColor = Color.White
                                        )
                                    ) { Text("−") }
                                    Text(
                                        wakeUpCheckCount.toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (wakeUpCheckCount < WakeUpCheckConfig.COUNT_RANGE.last) {
                                                wakeUpCheckCount++
                                            }
                                        },
                                        modifier = Modifier.size(48.dp),
                                        colors =
                                        IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = KeyButtonBg,
                                            contentColor = Color.White
                                        )
                                    ) { Text("+") }
                                }
                            }

                            Text(
                                text = stringResource(com.example.smartalarmer.R.string.wake_up_check_interval_label),
                                color = SecondaryText
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WakeUpCheckConfig.INTERVAL_OPTIONS_MINUTES.forEach { minutes ->
                                    FilterChip(
                                        selected = wakeUpCheckIntervalMinutes == minutes,
                                        onClick = { wakeUpCheckIntervalMinutes = minutes },
                                        label = {
                                            Text(
                                                stringResource(
                                                    com.example.smartalarmer.R.string.wake_up_check_minutes_format,
                                                    minutes
                                                )
                                            )
                                        },
                                        colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = IndigoPrimary,
                                            selectedLabelColor = Color.White,
                                            containerColor = KeyButtonBg,
                                            labelColor = InactiveControlText
                                        )
                                    )
                                }
                            }
                            Text(
                                text = stringResource(com.example.smartalarmer.R.string.wake_up_check_easy_task_description),
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(com.example.smartalarmer.R.string.volume_ramp_duration),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(com.example.smartalarmer.R.string.volume_ramp_duration_desc),
                            color = SecondaryText,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AlarmVolumeRamp.OPTIONS_SECONDS.forEach { seconds ->
                                FilterChip(
                                    selected = volumeRampSeconds == seconds,
                                    onClick = { volumeRampSeconds = seconds },
                                    label = {
                                        val durationText =
                                            if (seconds < 60) {
                                                stringResource(
                                                    com.example.smartalarmer.R.string.volume_ramp_seconds_format,
                                                    seconds
                                                )
                                            } else {
                                                stringResource(
                                                    com.example.smartalarmer.R.string.volume_ramp_minutes_format,
                                                    seconds / 60
                                                )
                                            }
                                        Text(durationText)
                                    },
                                    colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = KeyButtonBg,
                                        labelColor = InactiveControlText
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BottomSheetBg,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
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
                        onClick = saveDraft,
                        modifier = Modifier.weight(1f).testTag(ALARM_EDITOR_SAVE_TAG),
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

internal const val ALARM_EDITOR_CONTENT_TAG = "alarm_editor_content"
internal const val ALARM_EDITOR_TIME_ROW_TAG = "alarm_editor_time_row"
internal const val ALARM_EDITOR_SOUND_ROW_TAG = "alarm_editor_sound_row"
internal const val ALARM_EDITOR_REPEAT_TAG = "alarm_editor_repeat"
internal const val ALARM_EDITOR_DAYS_TAG = "alarm_editor_days"
internal const val ALARM_EDITOR_WEEK_PARITY_TAG = "alarm_editor_week_parity"
internal const val ALARM_EDITOR_PUZZLE_COUNT_TAG = "alarm_editor_puzzle_count"
internal const val ALARM_EDITOR_WAKE_UP_CHECKS_TAG = "alarm_editor_wake_up_checks"
internal const val ALARM_EDITOR_SAVE_TAG = "alarm_editor_save"
internal const val ALARM_LABEL_MAX_LENGTH = 60

package com.example.smartalarmer.ui.main

import android.app.TimePickerDialog
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.R
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    pickedSoundUri: String?,
    shakeSensorAvailable: Boolean = AndroidShakeSensorProvider(LocalContext.current).isAvailable
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    var hour by rememberSaveable(alarm?.id) { mutableIntStateOf(alarm?.hour ?: 8) }
    var minute by rememberSaveable(alarm?.id) { mutableIntStateOf(alarm?.minute ?: 0) }
    val initialLabel = alarm?.label.orEmpty()
    val labelLimit = maxOf(ALARM_LABEL_MAX_LENGTH, initialLabel.length)
    var label by rememberSaveable(alarm?.id) { mutableStateOf(initialLabel) }
    var labelFocused by remember { mutableStateOf(false) }

    val initialDays = alarm?.repeatDays?.values.orEmpty()
    val selectedDays =
        rememberSaveable(
            alarm?.id,
            saver =
            listSaver(
                save = { days -> days.map(AlarmDay::name) },
                restore = { names ->
                    mutableStateListOf<AlarmDay>().apply {
                        addAll(names.map(AlarmDay::valueOf))
                    }
                }
            )
        ) {
            mutableStateListOf<AlarmDay>().apply { addAll(initialDays) }
        }
    var repeatEnabled by rememberSaveable(alarm?.id) {
        mutableStateOf(initialDays.isNotEmpty())
    }
    var repeatWeekParity by rememberSaveable(alarm?.id) {
        mutableStateOf(alarm?.repeatWeekParity ?: AlarmWeekParity.EVERY)
    }
    var oneTimeDateEpochDay by rememberSaveable(alarm?.id) {
        mutableStateOf(alarm?.oneTimeDateEpochDay)
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
                restore = { names ->
                    mutableStateListOf<PuzzleType>().apply {
                        addAll(names.map(PuzzleType::valueOf))
                    }
                }
            )
        ) {
            mutableStateListOf<PuzzleType>().apply { addAll(initialPuzzles) }
        }

    var puzzleCount by rememberSaveable(alarm?.id) {
        mutableIntStateOf((alarm?.puzzleCount ?: 1).coerceIn(1, initialPuzzles.size))
    }
    var volumeRampSeconds by rememberSaveable(alarm?.id) {
        mutableIntStateOf(
            AlarmVolumeRamp.sanitize(
                alarm?.volumeRampSeconds ?: AlarmVolumeRamp.DEFAULT_SECONDS
            )
        )
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
    var expandedSection by rememberSaveable(alarm?.id) {
        mutableStateOf<String?>(null)
    }

    val repeatSummary =
        repeatSummary(
            repeatEnabled = repeatEnabled,
            selectedDays = selectedDays,
            repeatWeekParity = repeatWeekParity,
            oneTimeDateEpochDay = oneTimeDateEpochDay
        )
    val hasInvalidSpecificDate =
        !repeatEnabled &&
            oneTimeDateEpochDay?.let { epochDay ->
                !LocalDate
                    .ofEpochDay(epochDay)
                    .atTime(hour, minute)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .isAfter(Instant.now())
            } == true
    val puzzleNames =
        selectedPuzzles.joinToString(", ") { puzzle ->
            resources.getString(puzzle.nameResource())
        }
    val puzzleCountText =
        resources.getQuantityString(
            R.plurals.puzzles_plural,
            puzzleCount,
            puzzleCount
        )
    val puzzleSummary =
        if (puzzleCount == selectedPuzzles.size) {
            puzzleNames
        } else {
            "$puzzleNames · $puzzleCountText"
        }
    val rampSummary = volumeRampSummary(volumeRampSeconds)
    val soundSummary = "$selectedSoundName · $rampSummary"
    val wakeUpCheckSummary =
        if (wakeUpChecksEnabled) {
            stringResource(
                R.string.wake_up_check_card_summary,
                wakeUpCheckCount,
                wakeUpCheckIntervalMinutes
            )
        } else {
            stringResource(R.string.editor_off)
        }

    val saveDraft = {
        val puzzleSelection = PuzzleSelection.of(selectedPuzzles)
        onSave(
            AlarmDraft(
                hour = hour,
                minute = minute,
                repeatDays =
                if (repeatEnabled) {
                    AlarmDays.of(selectedDays)
                } else {
                    AlarmDays.ONE_TIME
                },
                repeatWeekParity =
                if (repeatEnabled) {
                    repeatWeekParity
                } else {
                    AlarmWeekParity.EVERY
                },
                puzzleSelection = puzzleSelection,
                puzzleCount = puzzleCount.coerceIn(1, puzzleSelection.values.size),
                label = label,
                soundUri = pickedSoundUri,
                wakeUpChecksEnabled = wakeUpChecksEnabled,
                wakeUpCheckCount = wakeUpCheckCount,
                wakeUpCheckIntervalMinutes = wakeUpCheckIntervalMinutes,
                volumeRampSeconds = volumeRampSeconds,
                oneTimeDateEpochDay = oneTimeDateEpochDay.takeUnless { repeatEnabled }
            )
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = BottomSheetBg,
        dragHandle = null,
        sheetGesturesEnabled = false
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .testTag(ALARM_EDITOR_CONTENT_TAG)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BottomSheetBg,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                            tint = Color.White
                        )
                    }
                    Text(
                        text =
                        stringResource(
                            if (alarm == null) R.string.new_alarm else R.string.edit_alarm
                        ),
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 88.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag(ALARM_EDITOR_SCROLL_TAG)
                        .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
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
                                    android.text.format.DateFormat.is24HourFormat(context)
                                ).show()
                            }.testTag(ALARM_EDITOR_TIME_ROW_TAG),
                        color = KeyButtonBg,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.time_label),
                                color = SecondaryText,
                                fontSize = 13.sp
                            )
                            Text(
                                text = AlarmTimeFormatter.formatTime(context, hour, minute),
                                color = Color.White,
                                fontSize = 44.sp,
                                lineHeight = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = label,
                        onValueChange = { updated ->
                            label = updated.take(labelLimit)
                        },
                        label = { Text(stringResource(R.string.label_placeholder)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { labelFocused = it.isFocused },
                        colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = IndigoContent,
                            focusedBorderColor = IndigoContent,
                            unfocusedBorderColor = CardBorderGlass,
                            focusedLabelColor = IndigoContent,
                            unfocusedLabelColor = SecondaryText
                        )
                    )
                    if (labelFocused) {
                        Text(
                            text = stringResource(R.string.label_character_count, label.length, labelLimit),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            color = SecondaryText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End
                        )
                    }

                    EditorSection(
                        title = stringResource(R.string.editor_section_schedule),
                        summary = repeatSummary,
                        expanded = expandedSection == EDITOR_SECTION_SCHEDULE,
                        onToggle = {
                            expandedSection =
                                expandedSection.toggled(EDITOR_SECTION_SCHEDULE)
                        },
                        modifier = Modifier.testTag(ALARM_EDITOR_REPEAT_TAG)
                    ) {
                        RepeatEditor(
                            repeatEnabled = repeatEnabled,
                            onRepeatEnabledChange = { enabled ->
                                repeatEnabled = enabled
                                if (enabled && selectedDays.isEmpty()) {
                                    selectedDays.addAll(AlarmDay.entries)
                                }
                                if (!enabled) {
                                    repeatWeekParity = AlarmWeekParity.EVERY
                                }
                            },
                            selectedDays = selectedDays,
                            repeatWeekParity = repeatWeekParity,
                            onRepeatWeekParityChange = { repeatWeekParity = it },
                            oneTimeDateEpochDay = oneTimeDateEpochDay,
                            onOneTimeDateChange = { oneTimeDateEpochDay = it },
                            hasInvalidSpecificDate = hasInvalidSpecificDate
                        )
                    }

                    EditorSection(
                        title = stringResource(R.string.editor_section_challenge),
                        summary = puzzleSummary,
                        expanded = expandedSection == EDITOR_SECTION_CHALLENGE,
                        onToggle = {
                            expandedSection =
                                expandedSection.toggled(EDITOR_SECTION_CHALLENGE)
                        },
                        modifier = Modifier.testTag(ALARM_EDITOR_CHALLENGE_TAG)
                    ) {
                        PuzzleEditor(
                            puzzleTypes = puzzleTypes,
                            selectedPuzzles = selectedPuzzles,
                            puzzleCount = puzzleCount,
                            onPuzzleCountChange = { puzzleCount = it }
                        )
                    }

                    EditorSection(
                        title = stringResource(R.string.editor_section_sound),
                        summary = soundSummary,
                        expanded = expandedSection == EDITOR_SECTION_SOUND,
                        onToggle = {
                            expandedSection =
                                expandedSection.toggled(EDITOR_SECTION_SOUND)
                        },
                        modifier = Modifier.testTag(ALARM_EDITOR_SOUND_SECTION_TAG)
                    ) {
                        SoundEditor(
                            selectedSoundName = selectedSoundName,
                            pickedSoundUri = pickedSoundUri,
                            onPickSound = onPickSound,
                            onPreviewSound = onPreviewSound,
                            isSoundPreviewPlaying = isSoundPreviewPlaying,
                            onResetSound = onResetSound,
                            volumeRampSeconds = volumeRampSeconds,
                            onVolumeRampChange = { volumeRampSeconds = it }
                        )
                    }

                    EditorSection(
                        title = stringResource(R.string.editor_section_after_dismissal),
                        summary = wakeUpCheckSummary,
                        expanded = expandedSection == EDITOR_SECTION_AFTER_DISMISSAL,
                        onToggle = {
                            expandedSection =
                                expandedSection.toggled(EDITOR_SECTION_AFTER_DISMISSAL)
                        },
                        modifier = Modifier.testTag(ALARM_EDITOR_WAKE_UP_CHECKS_TAG)
                    ) {
                        WakeUpChecksEditor(
                            enabled = wakeUpChecksEnabled,
                            onEnabledChange = { wakeUpChecksEnabled = it },
                            count = wakeUpCheckCount,
                            onCountChange = { wakeUpCheckCount = it },
                            intervalMinutes = wakeUpCheckIntervalMinutes,
                            onIntervalChange = { wakeUpCheckIntervalMinutes = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = saveDraft,
                    enabled = !hasInvalidSpecificDate,
                    modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .fillMaxWidth(0.72f)
                        .height(56.dp)
                        .testTag(ALARM_EDITOR_SAVE_TAG),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    elevation =
                    ButtonDefaults.buttonElevation(
                        defaultElevation = 10.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val expandedDescription =
        stringResource(
            if (expanded) R.string.accessibility_expanded else R.string.accessibility_collapsed
        )
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (expanded) IndigoContent.copy(alpha = 0.55f) else CardBorderGlass,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .semantics {
                    role = Role.Button
                    stateDescription = expandedDescription
                }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    color = SecondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector =
                if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = IndigoContent
            )
        }
        if (expanded) {
            HorizontalDivider(color = CardBorderGlass)
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun RepeatEditor(
    repeatEnabled: Boolean,
    onRepeatEnabledChange: (Boolean) -> Unit,
    selectedDays: MutableList<AlarmDay>,
    repeatWeekParity: AlarmWeekParity,
    onRepeatWeekParityChange: (AlarmWeekParity) -> Unit,
    oneTimeDateEpochDay: Long?,
    onOneTimeDateChange: (Long?) -> Unit,
    hasInvalidSpecificDate: Boolean
) {
    val context = LocalContext.current
    var showOneTimeDatePicker by rememberSaveable { mutableStateOf(false) }
    val onState = stringResource(R.string.accessibility_on)
    val offState = stringResource(R.string.accessibility_off)
    val repeatDescription = stringResource(R.string.repeat_days_label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = stringResource(R.string.repeat_days_label),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.repeat_alarm_description),
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = repeatEnabled,
            onCheckedChange = onRepeatEnabledChange,
            modifier =
            Modifier.semantics {
                contentDescription = repeatDescription
                stateDescription = if (repeatEnabled) onState else offState
            },
            colors = editorSwitchColors()
        )
    }

    if (!repeatEnabled) {
        val selectedDateText =
            oneTimeDateEpochDay?.let { AlarmTimeFormatter.formatDate(context, it) }
                ?: stringResource(R.string.one_time_next_occurrence)
        val dateLabel = stringResource(R.string.one_time_date_label)
        Surface(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable { showOneTimeDatePicker = true }
                .semantics {
                    contentDescription = "$dateLabel: $selectedDateText"
                    role = Role.Button
                }
                .testTag(ALARM_EDITOR_ONE_TIME_DATE_TAG),
            color = KeyButtonBg,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.one_time_date_label),
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = selectedDateText,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.one_time_choose_date),
                    color = IndigoContent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (oneTimeDateEpochDay != null) {
            TextButton(onClick = { onOneTimeDateChange(null) }) {
                Text(stringResource(R.string.one_time_clear_date))
            }
        }
        if (hasInvalidSpecificDate) {
            Text(
                text = stringResource(R.string.one_time_date_must_be_future),
                color = RedErrorContent,
                fontSize = 12.sp
            )
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                stringResource(R.string.weekdays) to AlarmDay.entries.take(5).toSet(),
                stringResource(R.string.weekends) to AlarmDay.entries.takeLast(2).toSet(),
                stringResource(R.string.every_day) to AlarmDay.entries.toSet()
            ).forEach { (label, days) ->
                FilterChip(
                    selected = selectedDays.toSet() == days,
                    onClick = {
                        selectedDays.clear()
                        selectedDays.addAll(days)
                    },
                    label = { Text(label) },
                    colors = editorFilterChipColors()
                )
            }
        }

        val dayLabels =
            listOf(
                stringResource(R.string.day_m),
                stringResource(R.string.day_t),
                stringResource(R.string.day_w),
                stringResource(R.string.day_th),
                stringResource(R.string.day_f),
                stringResource(R.string.day_sa),
                stringResource(R.string.day_su)
            )
        val dayNames =
            listOf(
                stringResource(R.string.day_mon),
                stringResource(R.string.day_tue),
                stringResource(R.string.day_wed),
                stringResource(R.string.day_thu),
                stringResource(R.string.day_fri),
                stringResource(R.string.day_sat),
                stringResource(R.string.day_sun)
            )
        Row(
            modifier = Modifier.fillMaxWidth().testTag(ALARM_EDITOR_DAYS_TAG),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
                                    onRepeatEnabledChange(false)
                                    onRepeatWeekParityChange(AlarmWeekParity.EVERY)
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

        Text(
            text = stringResource(R.string.repeat_week_pattern_label),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().testTag(ALARM_EDITOR_WEEK_PARITY_TAG)
        ) {
            AlarmWeekParity.entries.forEachIndexed { index, parity ->
                SegmentedButton(
                    selected = repeatWeekParity == parity,
                    onClick = { onRepeatWeekParityChange(parity) },
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
                        text =
                        stringResource(
                            when (parity) {
                                AlarmWeekParity.EVERY -> R.string.repeat_week_every
                                AlarmWeekParity.ODD -> R.string.repeat_week_odd
                                AlarmWeekParity.EVEN -> R.string.repeat_week_even
                            }
                        )
                    )
                }
            }
        }
    }

    if (showOneTimeDatePicker) {
        val todayEpochDay = LocalDate.now().toEpochDay()
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                (oneTimeDateEpochDay ?: todayEpochDay) * MILLIS_PER_DAY,
                selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) >= todayEpochDay
                }
            )
        DatePickerDialog(
            onDismissRequest = { showOneTimeDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            onOneTimeDateChange(Math.floorDiv(selectedMillis, MILLIS_PER_DAY))
                        }
                        showOneTimeDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text(stringResource(R.string.one_time_choose_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOneTimeDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = stringResource(R.string.one_time_date_picker_title),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PuzzleEditor(
    puzzleTypes: List<PuzzleType>,
    selectedPuzzles: MutableList<PuzzleType>,
    puzzleCount: Int,
    onPuzzleCountChange: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        puzzleTypes.forEach { type ->
            val isSelected = selectedPuzzles.contains(type)
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        if (selectedPuzzles.size > 1) {
                            selectedPuzzles.remove(type)
                            if (puzzleCount > selectedPuzzles.size) {
                                onPuzzleCountChange(selectedPuzzles.size)
                            }
                        }
                    } else {
                        selectedPuzzles.add(type)
                    }
                },
                label = { Text(stringResource(type.nameResource())) },
                colors = editorFilterChipColors()
            )
        }
    }

    val decreaseDescription = stringResource(R.string.decrease_puzzle_count)
    val increaseDescription = stringResource(R.string.increase_puzzle_count)
    StepperRow(
        label = stringResource(R.string.puzzles_required),
        value = puzzleCount,
        onDecrease = {
            if (puzzleCount > 1) onPuzzleCountChange(puzzleCount - 1)
        },
        onIncrease = {
            if (puzzleCount < selectedPuzzles.size) onPuzzleCountChange(puzzleCount + 1)
        },
        decreaseDescription = decreaseDescription,
        increaseDescription = increaseDescription,
        modifier = Modifier.testTag(ALARM_EDITOR_PUZZLE_COUNT_TAG)
    )
}

@Composable
private fun SoundEditor(
    selectedSoundName: String,
    pickedSoundUri: String?,
    onPickSound: () -> Unit,
    onPreviewSound: () -> Unit,
    isSoundPreviewPlaying: Boolean,
    onResetSound: () -> Unit,
    volumeRampSeconds: Int,
    onVolumeRampChange: (Int) -> Unit
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPickSound)
            .testTag(ALARM_EDITOR_SOUND_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.sound_label),
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            color = SecondaryText
        )
        Text(
            text = selectedSoundName,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = onPreviewSound,
            colors = ButtonDefaults.textButtonColors(contentColor = IndigoContent)
        ) {
            Text(
                text =
                stringResource(
                    if (isSoundPreviewPlaying) {
                        R.string.stop_sound_preview
                    } else {
                        R.string.preview_sound
                    }
                )
            )
        }
        if (pickedSoundUri != null) {
            TextButton(
                onClick = onResetSound,
                colors = ButtonDefaults.textButtonColors(contentColor = IndigoContent)
            ) {
                Text(stringResource(R.string.use_default_sound))
            }
        }
    }

    HorizontalDivider(color = CardBorderGlass)
    Text(
        text = stringResource(R.string.volume_ramp_duration),
        color = Color.White,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = stringResource(R.string.volume_ramp_duration_desc),
        color = SecondaryText,
        fontSize = 12.sp
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlarmVolumeRamp.OPTIONS_SECONDS.forEach { seconds ->
            FilterChip(
                selected = volumeRampSeconds == seconds,
                onClick = { onVolumeRampChange(seconds) },
                label = { Text(volumeRampSummary(seconds)) },
                colors = editorFilterChipColors()
            )
        }
    }
}

@Composable
private fun WakeUpChecksEditor(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    count: Int,
    onCountChange: (Int) -> Unit,
    intervalMinutes: Int,
    onIntervalChange: (Int) -> Unit
) {
    val onState = stringResource(R.string.accessibility_on)
    val offState = stringResource(R.string.accessibility_off)
    val wakeUpChecksDescription = stringResource(R.string.wake_up_checks_label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = stringResource(R.string.wake_up_checks_label),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.wake_up_checks_description),
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier =
            Modifier.semantics {
                contentDescription = wakeUpChecksDescription
                stateDescription = if (enabled) onState else offState
            },
            colors = editorSwitchColors()
        )
    }

    if (enabled) {
        StepperRow(
            label = stringResource(R.string.wake_up_check_count_label),
            value = count,
            onDecrease = {
                if (count > WakeUpCheckConfig.COUNT_RANGE.first) {
                    onCountChange(count - 1)
                }
            },
            onIncrease = {
                if (count < WakeUpCheckConfig.COUNT_RANGE.last) {
                    onCountChange(count + 1)
                }
            }
        )
        Text(
            text = stringResource(R.string.wake_up_check_interval_label),
            color = SecondaryText
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WakeUpCheckConfig.INTERVAL_OPTIONS_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = intervalMinutes == minutes,
                    onClick = { onIntervalChange(minutes) },
                    label = {
                        Text(
                            stringResource(
                                R.string.wake_up_check_minutes_format,
                                minutes
                            )
                        )
                    },
                    colors = editorFilterChipColors()
                )
            }
        }
        Text(
            text = stringResource(R.string.wake_up_check_easy_task_description),
            color = SecondaryText,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseDescription: String? = null,
    increaseDescription: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            color = SecondaryText
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = onDecrease,
                modifier =
                Modifier
                    .size(48.dp)
                    .semantics {
                        decreaseDescription?.let { contentDescription = it }
                    },
                colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = KeyButtonBg,
                    contentColor = Color.White
                )
            ) {
                Text("−")
            }
            Text(
                text = value.toString(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            FilledTonalIconButton(
                onClick = onIncrease,
                modifier =
                Modifier
                    .size(48.dp)
                    .semantics {
                        increaseDescription?.let { contentDescription = it }
                    },
                colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = KeyButtonBg,
                    contentColor = Color.White
                )
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun repeatSummary(
    repeatEnabled: Boolean,
    selectedDays: List<AlarmDay>,
    repeatWeekParity: AlarmWeekParity,
    oneTimeDateEpochDay: Long?
): String {
    if (!repeatEnabled || selectedDays.isEmpty()) {
        val oneTime = stringResource(R.string.one_time)
        return oneTimeDateEpochDay?.let {
            "$oneTime · ${AlarmTimeFormatter.formatDate(LocalContext.current, it)}"
        } ?: oneTime
    }
    val days = selectedDays.sortedBy(AlarmDay::isoValue)
    val daySummary =
        when {
            days.size == 7 -> stringResource(R.string.every_day)
            days.size == 5 && days.containsAll(AlarmDay.entries.take(5)) ->
                stringResource(R.string.weekdays)
            days.size == 2 && days.containsAll(AlarmDay.entries.takeLast(2)) ->
                stringResource(R.string.weekends)
            else -> {
                val names =
                    listOf(
                        stringResource(R.string.day_mon),
                        stringResource(R.string.day_tue),
                        stringResource(R.string.day_wed),
                        stringResource(R.string.day_thu),
                        stringResource(R.string.day_fri),
                        stringResource(R.string.day_sat),
                        stringResource(R.string.day_sun)
                    )
                days.joinToString(", ") { names[it.isoValue - 1] }
            }
        }
    return when (repeatWeekParity) {
        AlarmWeekParity.EVERY -> daySummary
        AlarmWeekParity.ODD ->
            "$daySummary · ${stringResource(R.string.repeat_week_odd_summary)}"
        AlarmWeekParity.EVEN ->
            "$daySummary · ${stringResource(R.string.repeat_week_even_summary)}"
    }
}

@Composable
private fun volumeRampSummary(seconds: Int): String = if (seconds < 60) {
    stringResource(R.string.volume_ramp_seconds_format, seconds)
} else {
    stringResource(R.string.volume_ramp_minutes_format, seconds / 60)
}

private fun PuzzleType.nameResource(): Int = when (this) {
    PuzzleType.MATH -> R.string.puzzle_math
    PuzzleType.MEMORY -> R.string.puzzle_memory
    PuzzleType.TYPING -> R.string.puzzle_typing
    PuzzleType.SHAKE -> R.string.puzzle_shake
}

private fun String?.toggled(section: String): String? = if (this == section) null else section

@Composable
private fun editorSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = IndigoPrimary,
    checkedTrackColor = IndigoPrimary.copy(alpha = 0.3f),
    uncheckedThumbColor = Color.Gray,
    uncheckedTrackColor = CardBorderGlass
)

@Composable
private fun editorFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = IndigoPrimary,
    selectedLabelColor = Color.White,
    containerColor = KeyButtonBg,
    labelColor = InactiveControlText
)

internal const val ALARM_EDITOR_CONTENT_TAG = "alarm_editor_content"
internal const val ALARM_EDITOR_SCROLL_TAG = "alarm_editor_scroll"
internal const val ALARM_EDITOR_TIME_ROW_TAG = "alarm_editor_time_row"
internal const val ALARM_EDITOR_SOUND_ROW_TAG = "alarm_editor_sound_row"
internal const val ALARM_EDITOR_REPEAT_TAG = "alarm_editor_repeat"
internal const val ALARM_EDITOR_DAYS_TAG = "alarm_editor_days"
internal const val ALARM_EDITOR_WEEK_PARITY_TAG = "alarm_editor_week_parity"
internal const val ALARM_EDITOR_ONE_TIME_DATE_TAG = "alarm_editor_one_time_date"
internal const val ALARM_EDITOR_CHALLENGE_TAG = "alarm_editor_challenge"
internal const val ALARM_EDITOR_SOUND_SECTION_TAG = "alarm_editor_sound_section"
internal const val ALARM_EDITOR_PUZZLE_COUNT_TAG = "alarm_editor_puzzle_count"
internal const val ALARM_EDITOR_WAKE_UP_CHECKS_TAG = "alarm_editor_wake_up_checks"
internal const val ALARM_EDITOR_SAVE_TAG = "alarm_editor_save"
internal const val ALARM_LABEL_MAX_LENGTH = 60

private const val EDITOR_SECTION_SCHEDULE = "schedule"
private const val EDITOR_SECTION_CHALLENGE = "challenge"
private const val EDITOR_SECTION_SOUND = "sound"
private const val EDITOR_SECTION_AFTER_DISMISSAL = "after_dismissal"
private const val MILLIS_PER_DAY = 86_400_000L

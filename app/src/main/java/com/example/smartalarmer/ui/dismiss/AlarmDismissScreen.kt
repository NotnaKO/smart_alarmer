package com.example.smartalarmer.ui.dismiss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.R
import com.example.smartalarmer.domain.PuzzleSelection
import com.example.smartalarmer.domain.PuzzleType
import com.example.smartalarmer.puzzle.*
import com.example.smartalarmer.ui.theme.*

@Composable
fun AlarmDismissScreen(
    puzzlesList: String,
    puzzleCount: Int,
    onDismissComplete: () -> Unit,
    onVerifiedProgress: (taskIndex: Int, progress: Float) -> Unit = { _, _ -> },
    onIntermediateTaskCompleted: (taskIndex: Int) -> Unit = {},
    alarmLabel: String = "",
    isWakeUpCheck: Boolean = false,
    wakeUpCheckNumber: Int = 0,
    wakeUpCheckTotal: Int = 0,
    mathProvider: MathPuzzleProvider = MathEngine,
    typingProvider: TypingPuzzleProvider = TypingEngine,
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
    shakeProvider: ShakeSensorProvider = AndroidShakeSensorProvider(androidx.compose.ui.platform.LocalContext.current)
) {
    val puzzles =
        rememberSaveable(
            puzzlesList,
            puzzleCount,
            isWakeUpCheck,
            shakeProvider.isAvailable,
            saver =
            listSaver(
                save = { puzzleTypes -> puzzleTypes.map(PuzzleType::name) },
                restore = { names -> names.map(PuzzleType::valueOf).toMutableStateList() }
            )
        ) {
            val configuredPuzzles =
                PuzzleSelection
                    .parse(puzzlesList)
                    .values
                    .map { puzzle ->
                        if (puzzle == PuzzleType.SHAKE && !shakeProvider.isAvailable) {
                            PuzzleType.MATH
                        } else {
                            puzzle
                        }
                    }

            configuredPuzzles
                .shuffled()
                .take(puzzleCount.coerceAtLeast(1))
                .ifEmpty { listOf(PuzzleType.MATH) }
                .toMutableStateList()
        }

    var currentTaskIndex by rememberSaveable(puzzlesList, puzzleCount, isWakeUpCheck) {
        mutableIntStateOf(0)
    }

    if (currentTaskIndex >= puzzles.size) {
        LaunchedEffect(Unit) {
            onDismissComplete()
        }
        return
    }

    val activeTaskIndex = currentTaskIndex
    val currentPuzzle = puzzles[activeTaskIndex]
    val alternatePuzzles =
        remember(puzzlesList, shakeProvider.isAvailable) {
            buildList {
                addAll(
                    PuzzleSelection
                        .parse(puzzlesList)
                        .values
                        .filter { it != PuzzleType.SHAKE || shakeProvider.isAvailable }
                )
                addAll(listOf(PuzzleType.MATH, PuzzleType.TYPING, PuzzleType.MEMORY))
                if (shakeProvider.isAvailable) add(PuzzleType.SHAKE)
            }.distinct()
        }
    var failureCount by rememberSaveable(activeTaskIndex, currentPuzzle) {
        mutableIntStateOf(0)
    }
    var isAlternateAvailable by rememberSaveable(activeTaskIndex, currentPuzzle) {
        mutableStateOf(false)
    }
    LaunchedEffect(activeTaskIndex, currentPuzzle) {
        kotlinx.coroutines.delay(ALTERNATE_PUZZLE_DELAY_MILLIS)
        isAlternateAvailable = true
    }
    val recordFailure = {
        failureCount++
        if (failureCount >= ALTERNATE_PUZZLE_FAILURE_THRESHOLD) {
            isAlternateAvailable = true
        }
    }
    val completeCurrentTask = {
        if (currentTaskIndex == activeTaskIndex) {
            if (activeTaskIndex + 1 < puzzles.size) {
                onIntermediateTaskCompleted(activeTaskIndex)
            }
            currentTaskIndex = activeTaskIndex + 1
        }
        Unit
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(DarkBgScreen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isWakeUpCheck) {
                Text(
                    text = stringResource(R.string.wake_up_check_title),
                    color = IndigoContent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text =
                    stringResource(
                        R.string.wake_up_check_progress,
                        wakeUpCheckNumber,
                        wakeUpCheckTotal
                    ),
                    color = SecondaryText,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (alarmLabel.isNotEmpty()) {
                Text(
                    text = alarmLabel,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Text(
                text =
                stringResource(
                    R.string.task_progress_format,
                    currentTaskIndex + 1,
                    puzzles.size
                ),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(PUZZLE_CONTAINER_TAG),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag(PUZZLE_CONTENT_TAG),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                key(activeTaskIndex, currentPuzzle) {
                    when (currentPuzzle) {
                        PuzzleType.MATH ->
                            MathPuzzleView(
                                onComplete = completeCurrentTask,
                                onProgress = { progress -> onVerifiedProgress(activeTaskIndex, progress) },
                                onFailure = recordFailure,
                                mathProvider = mathProvider,
                                easyMode = isWakeUpCheck
                            )
                        PuzzleType.TYPING ->
                            TypingPuzzleView(
                                onComplete = completeCurrentTask,
                                onProgress = { progress -> onVerifiedProgress(activeTaskIndex, progress) },
                                onFailure = recordFailure,
                                typingProvider = typingProvider,
                                easyMode = isWakeUpCheck
                            )
                        PuzzleType.MEMORY ->
                            MemoryPuzzleView(
                                onComplete = completeCurrentTask,
                                onProgress = { progress -> onVerifiedProgress(activeTaskIndex, progress) },
                                onFailure = recordFailure,
                                memoryProvider = memoryProvider,
                                easyMode = isWakeUpCheck
                            )
                        PuzzleType.SHAKE ->
                            ShakePuzzleView(
                                onComplete = completeCurrentTask,
                                onProgress = { progress -> onVerifiedProgress(activeTaskIndex, progress) },
                                shakeProvider = shakeProvider,
                                easyMode = isWakeUpCheck
                            )
                    }
                }
                if (isAlternateAvailable) {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = {
                            puzzles[activeTaskIndex] =
                                nextAlternatePuzzle(
                                    current = currentPuzzle,
                                    candidates = alternatePuzzles
                                )
                        },
                        modifier = Modifier.testTag(ALTERNATE_PUZZLE_BUTTON_TAG)
                    ) {
                        Text(stringResource(R.string.try_different_puzzle))
                    }
                }
            }
        }
    }
}

internal const val PUZZLE_CONTAINER_TAG = "alarm_dismiss_puzzle_container"
internal const val PUZZLE_CONTENT_TAG = "alarm_dismiss_puzzle_content"
internal const val ALTERNATE_PUZZLE_BUTTON_TAG = "alternate_puzzle_button"
internal const val ALTERNATE_PUZZLE_FAILURE_THRESHOLD = 3
internal const val ALTERNATE_PUZZLE_DELAY_MILLIS = 30_000L

internal fun nextAlternatePuzzle(
    current: PuzzleType,
    candidates: List<PuzzleType>
): PuzzleType = candidates.firstOrNull { it != current } ?: PuzzleType.MATH

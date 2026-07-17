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
                restore = { names -> names.map(PuzzleType::valueOf) }
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
        }

    var currentTaskIndex by rememberSaveable(puzzles) { mutableStateOf(0) }

    if (currentTaskIndex >= puzzles.size) {
        LaunchedEffect(Unit) {
            onDismissComplete()
        }
        return
    }

    val activeTaskIndex = currentTaskIndex
    val currentPuzzle = puzzles[activeTaskIndex]
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
                    color = IndigoPrimary,
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
                    color = Color.LightGray,
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
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag(PUZZLE_CONTENT_TAG),
                contentAlignment = Alignment.Center
            ) {
                when (currentPuzzle) {
                    PuzzleType.MATH ->
                        MathPuzzleView(
                            onComplete = completeCurrentTask,
                            onProgress = { progress -> onVerifiedProgress(activeTaskIndex, progress) },
                            mathProvider = mathProvider,
                            easyMode = isWakeUpCheck
                        )
                    PuzzleType.TYPING ->
                        TypingPuzzleView(
                            onComplete = completeCurrentTask,
                            onProgress = { progress -> onVerifiedProgress(activeTaskIndex, progress) },
                            typingProvider = typingProvider,
                            easyMode = isWakeUpCheck
                        )
                    PuzzleType.MEMORY ->
                        MemoryPuzzleView(
                            onComplete = completeCurrentTask,
                            onProgress = { progress -> onVerifiedProgress(activeTaskIndex, progress) },
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
        }
    }
}

internal const val PUZZLE_CONTAINER_TAG = "alarm_dismiss_puzzle_container"
internal const val PUZZLE_CONTENT_TAG = "alarm_dismiss_puzzle_content"

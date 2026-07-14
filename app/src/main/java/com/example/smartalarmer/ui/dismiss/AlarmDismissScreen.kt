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
    alarmLabel: String = "",
    mathProvider: MathPuzzleProvider = MathEngine,
    typingProvider: TypingPuzzleProvider = TypingEngine,
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
    shakeProvider: ShakeSensorProvider = AndroidShakeSensorProvider(androidx.compose.ui.platform.LocalContext.current)
) {
    val puzzles =
        rememberSaveable(
            puzzlesList,
            puzzleCount,
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

    val currentPuzzle = puzzles[currentTaskIndex]

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(DarkBgScreen)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label Header (if present)
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

        // Progress Header
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

        // Active Puzzle View
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (currentPuzzle) {
                PuzzleType.MATH ->
                    MathPuzzleView(
                        onComplete = { currentTaskIndex++ },
                        mathProvider = mathProvider
                    )
                PuzzleType.TYPING ->
                    TypingPuzzleView(
                        onComplete = { currentTaskIndex++ },
                        typingProvider = typingProvider
                    )
                PuzzleType.MEMORY ->
                    MemoryPuzzleView(
                        onComplete = { currentTaskIndex++ },
                        memoryProvider = memoryProvider
                    )
                PuzzleType.SHAKE ->
                    ShakePuzzleView(
                        onComplete = { currentTaskIndex++ },
                        shakeProvider = shakeProvider
                    )
            }
        }
    }
}

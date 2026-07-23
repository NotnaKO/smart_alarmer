package com.example.smartalarmer.ui.dismiss

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smartalarmer.R
import com.example.smartalarmer.puzzle.*
import com.example.smartalarmer.ui.theme.*

@Composable
fun MemoryPuzzleView(
    onComplete: () -> Unit,
    onProgress: (Float) -> Unit = {},
    onFailure: () -> Unit = {},
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
    easyMode: Boolean = false
) {
    val sequence =
        rememberSaveable {
            val difficulty = if (easyMode) Difficulty.EASY else listOf(Difficulty.MEDIUM, Difficulty.HARD).random()
            val length =
                when (difficulty) {
                    Difficulty.EASY -> 3
                    Difficulty.MEDIUM -> 5
                    Difficulty.HARD -> 7
                }
            memoryProvider.generateSequence(length)
        }
    val userInputs =
        rememberSaveable(
            saver =
            listSaver(
                save = { inputs -> inputs.toList() },
                restore = { inputs -> mutableStateListOf<Int>().apply { addAll(inputs) } }
            )
        ) { mutableStateListOf<Int>() }
    var hasStarted by rememberSaveable { mutableStateOf(false) }
    var isShowingSequence by rememberSaveable { mutableStateOf(false) }
    var activeFlashIndex by rememberSaveable { mutableIntStateOf(-1) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val memoryCellDescriptions = (1..9).map { stringResource(R.string.memory_cell_desc, it) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by
        remember(lifecycleOwner) {
            mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { source, _ ->
                isResumed = source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (!isResumed) activeFlashIndex = -1
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasStarted, isShowingSequence, isResumed) {
        if (hasStarted && isShowingSequence && isResumed) {
            activeFlashIndex = -1
            kotlinx.coroutines.delay(MEMORY_READY_DELAY_MS)
            for (index in sequence) {
                activeFlashIndex = index
                kotlinx.coroutines.delay(MEMORY_FLASH_DURATION_MS)
                activeFlashIndex = -1
                kotlinx.coroutines.delay(MEMORY_FLASH_GAP_MS)
            }
            isShowingSequence = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text =
            when {
                !hasStarted -> stringResource(R.string.memory_ready_prompt)
                isShowingSequence -> stringResource(R.string.memorize_pattern)
                else -> stringResource(R.string.repeat_pattern)
            },
            color = Color.White,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column {
            (0..8).chunked(3).forEach { row ->
                Row {
                    row.forEach { index ->
                        val isFlashed = activeFlashIndex == index
                        Button(
                            onClick = {
                                if (!isShowingSequence) {
                                    userInputs.add(index)
                                    val isValid = memoryProvider.verifyStep(sequence, userInputs)
                                    if (!isValid) {
                                        userInputs.clear()
                                        showError = true
                                        isShowingSequence = true
                                        onFailure()
                                    } else {
                                        onProgress(userInputs.size.toFloat() / sequence.size.toFloat())
                                        if (userInputs.size == sequence.size) {
                                            onComplete()
                                            return@Button
                                        }
                                        showError = false
                                    }
                                }
                            },
                            enabled = hasStarted && !isShowingSequence,
                            modifier =
                            Modifier.padding(6.dp).size(72.dp).semantics {
                                contentDescription = memoryCellDescriptions[index]
                            },
                            colors =
                            ButtonDefaults.buttonColors(
                                containerColor = if (isFlashed) OrangeWarning else DarkGreyButton,
                                disabledContainerColor = if (isFlashed) OrangeWarning else DarkGreyButton
                            ),
                            shape = RoundedCornerShape(36.dp)
                        ) {}
                    }
                }
            }
        }
        if (!hasStarted) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    hasStarted = true
                    isShowingSequence = true
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.show_pattern))
            }
        }
        if (showError) {
            Text(
                text = stringResource(R.string.incorrect_pattern),
                color = RedError,
                modifier =
                Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Assertive
                }
            )
        }
    }
}

private const val MEMORY_READY_DELAY_MS = 800L
private const val MEMORY_FLASH_DURATION_MS = 600L
private const val MEMORY_FLASH_GAP_MS = 200L

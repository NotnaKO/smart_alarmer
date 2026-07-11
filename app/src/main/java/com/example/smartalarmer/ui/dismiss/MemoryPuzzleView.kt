package com.example.smartalarmer.ui.dismiss

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.puzzle.*
import com.example.smartalarmer.ui.theme.*
import com.example.smartalarmer.R
import com.example.smartalarmer.domain.PuzzleSelection
import com.example.smartalarmer.domain.PuzzleType
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun MemoryPuzzleView(
    onComplete: () -> Unit,
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
) {
    val sequence = rememberSaveable {
        val difficulty = listOf(Difficulty.MEDIUM, Difficulty.HARD).random()
        val length = when (difficulty) {
            Difficulty.EASY -> 3
            Difficulty.MEDIUM -> 5
            Difficulty.HARD -> 7
        }
        memoryProvider.generateSequence(length)
    }
    val userInputs = rememberSaveable(
        saver = listSaver(
            save = { inputs -> inputs.toList() },
            restore = { inputs -> mutableStateListOf<Int>().apply { addAll(inputs) } }
        )
    ) { mutableStateListOf<Int>() }
    var isShowingSequence by rememberSaveable { mutableStateOf(true) }
    var activeFlashIndex by rememberSaveable { mutableStateOf(-1) }
    val memoryCellDescriptions = (1..9).map { stringResource(R.string.memory_cell_desc, it) }

    LaunchedEffect(isShowingSequence) {
        if (isShowingSequence) {
            for (index in sequence) {
                activeFlashIndex = index
                kotlinx.coroutines.delay(600)
                activeFlashIndex = -1
                kotlinx.coroutines.delay(200)
            }
            isShowingSequence = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (isShowingSequence) {
                stringResource(R.string.memorize_pattern)
            } else {
                stringResource(R.string.repeat_pattern)
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
                                        isShowingSequence = true
                                    } else if (userInputs.size == sequence.size) {
                                        onComplete()
                                    }
                                }
                            },
                            modifier = Modifier.padding(6.dp).size(72.dp).semantics {
                                contentDescription = memoryCellDescriptions[index]
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFlashed) OrangeWarning else DarkGreyButton
                            ),
                            shape = RoundedCornerShape(36.dp)
                          ) {}
                      }
                  }
              }
          }
      }
}


package com.example.smartalarmer.ui.dismiss

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.R
import com.example.smartalarmer.puzzle.*
import com.example.smartalarmer.ui.theme.*

@Composable
fun MathPuzzleView(
    onComplete: () -> Unit,
    onProgress: (Float) -> Unit = {},
    onFailure: () -> Unit = {},
    mathProvider: MathPuzzleProvider = MathEngine,
    easyMode: Boolean = false
) {
    val puzzle =
        rememberSaveable(
            saver =
            mapSaver(
                save = { generated ->
                    mapOf(
                        "equation" to generated.equation,
                        "answer" to generated.answer,
                        "difficulty" to generated.difficulty.name
                    )
                },
                restore = { saved ->
                    MathPuzzle(
                        equation = saved.getValue("equation") as String,
                        answer = saved.getValue("answer") as Int,
                        difficulty = Difficulty.valueOf(saved.getValue("difficulty") as String)
                    )
                }
            )
        ) {
            val difficulty = if (easyMode) Difficulty.EASY else listOf(Difficulty.MEDIUM, Difficulty.HARD).random()
            mathProvider.generate(difficulty)
        }
    var input by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val backspaceDescription = stringResource(R.string.backspace_desc)
    val confirmDescription = stringResource(R.string.confirm_answer_desc)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = puzzle.equation, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.your_answer_format, input), color = Color.LightGray, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))
        // Numeric Keyboard
        Column {
            (1..9).chunked(3).forEach { row ->
                Row {
                    row.forEach { num ->
                        Button(
                            onClick = {
                                showError = false
                                val updatedInput = input + num.toString()
                                input = updatedInput
                                reportMathProgress(updatedInput, puzzle.answer, onProgress)
                            },
                            modifier =
                            Modifier
                                .padding(4.dp)
                                .size(64.dp)
                                .semantics { contentDescription = num.toString() },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text(text = num.toString(), color = Color.White, fontSize = 20.sp)
                        }
                    }
                }
            }
            Row {
                Button(
                    onClick = { if (input.isNotEmpty()) input = input.dropLast(1) },
                    modifier =
                    Modifier.padding(4.dp).size(64.dp).semantics {
                        contentDescription = backspaceDescription
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(text = "⌫", color = Color.White, fontSize = 20.sp)
                }
                Button(
                    onClick = {
                        showError = false
                        val updatedInput = input + "0"
                        input = updatedInput
                        reportMathProgress(updatedInput, puzzle.answer, onProgress)
                    },
                    modifier =
                    Modifier.padding(4.dp).size(64.dp).semantics {
                        contentDescription = "0"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text(text = "0", color = Color.White, fontSize = 20.sp)
                }
                Button(
                    onClick = {
                        if (input.toIntOrNull() == puzzle.answer) {
                            onComplete()
                        } else {
                            input = ""
                            showError = true
                            onFailure()
                        }
                    },
                    modifier =
                    Modifier.padding(4.dp).size(64.dp).semantics {
                        contentDescription = confirmDescription
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Text(text = "✔", color = Color.White, fontSize = 20.sp)
                }
            }
        }
        if (showError) {
            Text(
                text = stringResource(R.string.incorrect_answer),
                color = RedError,
                modifier =
                Modifier.semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Assertive
                }
            )
        }
    }
}

private fun reportMathProgress(
    input: String,
    answer: Int,
    onProgress: (Float) -> Unit
) {
    val answerText = answer.toString()
    if (input.isNotEmpty() && answerText.startsWith(input)) {
        onProgress((input.length.toFloat() / answerText.length.toFloat()).coerceIn(0f, 1f))
    }
}

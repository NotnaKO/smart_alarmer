package com.example.smartalarmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.puzzle.*

enum class PuzzleType { MATH, TYPING, MEMORY }

@Composable
fun AlarmDismissScreen(
    puzzlesList: String,
    puzzleCount: Int,
    onDismissComplete: () -> Unit,
    mathProvider: MathPuzzleProvider = MathEngine,
    typingProvider: TypingPuzzleProvider = TypingEngine,
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
) {
    val puzzles = remember {
        puzzlesList.split(",")
            .mapNotNull {
                runCatching { PuzzleType.valueOf(it.trim().uppercase()) }.getOrNull()
            }
            .shuffled()
            .take(puzzleCount)
    }

    LaunchedEffect(puzzles) {
        android.util.Log.d("TEST_DEBUG", "Puzzles: ${puzzles.joinToString(", ")}")
    }

    var currentTaskIndex by remember { mutableStateOf(0) }

    if (currentTaskIndex >= puzzles.size) {
        LaunchedEffect(Unit) {
            onDismissComplete()
        }
        return
    }

    val currentPuzzle = puzzles[currentTaskIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Progress Header
        Text(
            text = "Task ${currentTaskIndex + 1} of ${puzzles.size}",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // Active Puzzle View
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when (currentPuzzle) {
                PuzzleType.MATH -> MathPuzzleView(
                    onComplete = { currentTaskIndex++ },
                    mathProvider = mathProvider,
                )
                PuzzleType.TYPING -> TypingPuzzleView(
                    onComplete = { currentTaskIndex++ },
                    typingProvider = typingProvider,
                )
                PuzzleType.MEMORY -> MemoryPuzzleView(
                    onComplete = { currentTaskIndex++ },
                    memoryProvider = memoryProvider,
                )
            }
        }
    }
}

@Composable
fun MathPuzzleView(
    onComplete: () -> Unit,
    mathProvider: MathPuzzleProvider = MathEngine,
) {
    val puzzle = remember { mathProvider.generate(Difficulty.MEDIUM) }
    LaunchedEffect(puzzle) {
        android.util.Log.d("TEST_DEBUG", "Math Puzzle: ${puzzle.equation} = ${puzzle.answer}")
    }
    var input by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = puzzle.equation, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Your Answer: $input", color = Color.LightGray, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))
        // Numeric Keyboard
        Column {
            (1..9).chunked(3).forEach { row ->
                Row {
                    row.forEach { num ->
                        Button(
                            onClick = { input += num.toString() },
                            modifier = Modifier.padding(4.dp).size(64.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text(text = num.toString(), color = Color.White, fontSize = 20.sp)
                        }
                    }
                }
            }
            Row {
                Button(
                    onClick = { if (input.isNotEmpty()) input = input.dropLast(1) },
                    modifier = Modifier.padding(4.dp).size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(text = "⌫", color = Color.White, fontSize = 20.sp)
                }
                Button(
                    onClick = { input += "0" },
                    modifier = Modifier.padding(4.dp).size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text(text = "0", color = Color.White, fontSize = 20.sp)
                }
                Button(
                    onClick = {
                        if (input.toIntOrNull() == puzzle.answer) {
                            onComplete()
                        } else {
                            input = ""
                        }
                    },
                    modifier = Modifier.padding(4.dp).size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text(text = "✔", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun TypingPuzzleView(
    onComplete: () -> Unit,
    typingProvider: TypingPuzzleProvider = TypingEngine,
) {
    val targetQuote = remember { typingProvider.getRandomQuote() }
    LaunchedEffect(targetQuote) {
        android.util.Log.d("TEST_DEBUG", "Typing Quote: $targetQuote")
    }
    var input by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Type this exact sentence:", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = targetQuote, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(24.dp))
        
        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF222222),
                unfocusedContainerColor = Color(0xFF222222)
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (typingProvider.isMatch(targetQuote, input)) {
                    onComplete()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
        ) {
            Text("Submit")
        }
    }
}

@Composable
fun MemoryPuzzleView(
    onComplete: () -> Unit,
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
) {
    val sequence = remember { memoryProvider.generateSequence(4) }
    LaunchedEffect(sequence) {
        android.util.Log.d("TEST_DEBUG", "Memory Sequence: ${sequence.joinToString(", ")}")
    }
    val userInputs = remember { mutableStateListOf<Int>() }
    var isShowingSequence by remember { mutableStateOf(true) }
    var activeFlashIndex by remember { mutableStateOf(-1) }

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
            text = if (isShowingSequence) "Memorize Pattern..." else "Repeat Pattern!",
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
                            modifier = Modifier.padding(6.dp).size(72.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFlashed) Color(0xFFF59E0B) else Color(0xFF333333)
                            ),
                            shape = RoundedCornerShape(36.dp)
                          ) {}
                      }
                  }
              }
          }
      }
}

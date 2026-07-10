package com.example.smartalarmer.ui.dismiss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val puzzles = remember(puzzlesList, puzzleCount, shakeProvider.isAvailable) {
        val configuredPuzzles = PuzzleSelection.parse(puzzlesList).values
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

    var currentTaskIndex by remember(puzzles) { mutableStateOf(0) }

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
            .background(DarkBgScreen)
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
            text = stringResource(
                R.string.task_progress_format,
                currentTaskIndex + 1,
                puzzles.size
            ),
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
                PuzzleType.SHAKE -> ShakePuzzleView(
                    onComplete = { currentTaskIndex++ },
                    shakeProvider = shakeProvider
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
    val puzzle = remember {
        val difficulty = listOf(Difficulty.MEDIUM, Difficulty.HARD).random()
        mathProvider.generate(difficulty)
    }
    var input by remember { mutableStateOf("") }
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
                            onClick = { input += num.toString() },
                            modifier = Modifier.padding(4.dp).size(64.dp),
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
                    modifier = Modifier.padding(4.dp).size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(text = "⌫", color = Color.White, fontSize = 20.sp)
                }
                Button(
                    onClick = { input += "0" },
                    modifier = Modifier.padding(4.dp).size(64.dp),
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
                        }
                    },
                    modifier = Modifier.padding(4.dp).size(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
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
    val quotes = stringArrayResource(R.array.typing_quotes).toList()
    val targetQuote = remember { typingProvider.getRandomQuote(quotes) }
    var input by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.type_sentence_label), color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = targetQuote, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(24.dp))
        
        TextField(
            value = input,
            onValueChange = { /* readOnly handles input */ },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = DarkGreyInput,
                unfocusedContainerColor = DarkGreyInput
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        VirtualKeyboard(
            onKeyClick = { input += it },
            onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (typingProvider.isMatch(targetQuote, input)) {
                    onComplete()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Purple600)
        ) {
            Text(stringResource(R.string.submit_btn))
        }
    }
}

@Composable
fun VirtualKeyboard(
    onKeyClick: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isShifted by remember { mutableStateOf(false) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val locale = configuration.locales[0] ?: java.util.Locale.getDefault()
    val language = locale.language

    val rows = remember(isShifted, language) {
        KeyboardLayouts.getLayoutForLanguage(language).map { row ->
            if (isShifted) {
                row.map { it.uppercaseChar() }
            } else {
                row
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KeyboardBg, RoundedCornerShape(16.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Rows 1 & 2
        rows.take(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { char ->
                    KeyButton(text = char.toString(), onClick = { onKeyClick(char) }, modifier = Modifier.weight(1f))
                }
            }
        }

        // Row 3 (Shift, letters & punctuation, Backspace)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                text = "⇧",
                onClick = { isShifted = !isShifted },
                containerColor = if (isShifted) IndigoPrimary else KeyButtonBgActive,
                modifier = Modifier.weight(1.5f)
            )

            rows[2].forEach { char ->
                KeyButton(text = char.toString(), onClick = { onKeyClick(char) }, modifier = Modifier.weight(1f))
            }

            KeyButton(
                text = "⌫",
                onClick = onBackspace,
                containerColor = KeyButtonBgActive,
                modifier = Modifier.weight(1.5f)
            )
        }

        // Row 4 (Space)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            KeyButton(
                text = "Space",
                onClick = { onKeyClick(' ') },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
fun KeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = KeyButtonBg,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MemoryPuzzleView(
    onComplete: () -> Unit,
    memoryProvider: MemoryPuzzleProvider = MemoryEngine,
) {
    val sequence = remember {
        val difficulty = listOf(Difficulty.MEDIUM, Difficulty.HARD).random()
        val length = when (difficulty) {
            Difficulty.EASY -> 3
            Difficulty.MEDIUM -> 5
            Difficulty.HARD -> 7
        }
        memoryProvider.generateSequence(length)
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
                            modifier = Modifier.padding(6.dp).size(72.dp),
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

@Composable
fun ShakePuzzleView(
    onComplete: () -> Unit,
    shakeProvider: ShakeSensorProvider
) {
    var shakeCount by remember { mutableStateOf(30) }
    val targetShakes = 30

    DisposableEffect(key1 = shakeProvider) {
        var lastUpdate = System.currentTimeMillis()
        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f

        shakeProvider.register { x, y, z ->
            val curTime = System.currentTimeMillis()
            // Only check every 100ms
            if ((curTime - lastUpdate) > 100) {
                val diffTime = (curTime - lastUpdate)
                lastUpdate = curTime

                val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000

                if (speed > 800) { // Shake detected
                    if (shakeCount > 0) {
                        shakeCount--
                        if (shakeCount == 0) {
                            onComplete()
                        }
                    }
                }
                lastX = x
                lastY = y
                lastZ = z
            }
        }

        onDispose {
            shakeProvider.unregister()
        }
    }

    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.shake_device),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.shakes_remaining, shakeCount),
            color = Color.LightGray,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { (targetShakes - shakeCount).toFloat() / targetShakes },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = GreenSuccess,
            trackColor = KeyButtonBgActive
        )
    }
}

package com.example.smartalarmer.ui.dismiss

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.R
import com.example.smartalarmer.puzzle.*
import com.example.smartalarmer.ui.theme.*

@Composable
fun TypingPuzzleView(
    onComplete: () -> Unit,
    onProgress: (Float) -> Unit = {},
    onFailure: () -> Unit = {},
    typingProvider: TypingPuzzleProvider = TypingEngine,
    easyMode: Boolean = false
) {
    val quotes = stringArrayResource(R.array.typing_quotes).toList()
    val targetQuote = rememberSaveable(easyMode) {
        val quote = typingProvider.getRandomQuote(quotes)
        if (easyMode) quote.split(" ").take(4).joinToString(" ") else quote
    }
    var input by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
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
            colors =
            TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = DarkGreyInput,
                unfocusedContainerColor = DarkGreyInput
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        VirtualKeyboard(
            onKeyClick = {
                showError = false
                val updatedInput = input + it
                input = updatedInput
                typingProvider.progress(targetQuote, updatedInput).takeIf { progress -> progress > 0f }?.let(onProgress)
            },
            onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (typingProvider.isMatch(targetQuote, input)) {
                    onComplete()
                } else {
                    showError = true
                    onFailure()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Purple600)
        ) {
            Text(stringResource(R.string.submit_btn))
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

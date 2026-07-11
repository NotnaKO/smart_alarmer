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
fun TypingPuzzleView(
    onComplete: () -> Unit,
    typingProvider: TypingPuzzleProvider = TypingEngine,
) {
    val quotes = stringArrayResource(R.array.typing_quotes).toList()
    val targetQuote = rememberSaveable { typingProvider.getRandomQuote(quotes) }
    var input by rememberSaveable { mutableStateOf("") }
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

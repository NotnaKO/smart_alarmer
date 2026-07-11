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
fun VirtualKeyboard(
    onKeyClick: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isShifted by rememberSaveable { mutableStateOf(false) }
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
    val shiftDescription = stringResource(R.string.shift_key_desc)
    val shiftedState = stringResource(if (isShifted) R.string.shift_on else R.string.shift_off)
    val backspaceDescription = stringResource(R.string.backspace_desc)
    val spaceLabel = stringResource(R.string.space_key)

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
                modifier = Modifier.weight(1.5f).semantics {
                    contentDescription = shiftDescription
                    stateDescription = shiftedState
                }
            )

            rows[2].forEach { char ->
                KeyButton(text = char.toString(), onClick = { onKeyClick(char) }, modifier = Modifier.weight(1f))
            }

            KeyButton(
                text = "⌫",
                onClick = onBackspace,
                containerColor = KeyButtonBgActive,
                modifier = Modifier.weight(1.5f).semantics {
                    contentDescription = backspaceDescription
                }
            )
        }

        // Row 4 (Space)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            KeyButton(
                text = spaceLabel,
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
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.Button },
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

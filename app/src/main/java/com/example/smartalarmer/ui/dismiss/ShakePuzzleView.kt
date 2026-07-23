package com.example.smartalarmer.ui.dismiss

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartalarmer.R
import com.example.smartalarmer.puzzle.*
import com.example.smartalarmer.ui.theme.*

@Composable
fun ShakePuzzleView(
    onComplete: () -> Unit,
    onProgress: (Float) -> Unit = {},
    shakeProvider: ShakeSensorProvider,
    easyMode: Boolean = false
) {
    val targetShakes = if (easyMode) 8 else 30
    var shakeCount by rememberSaveable(targetShakes) { mutableIntStateOf(targetShakes) }

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
                        onProgress((targetShakes - shakeCount).toFloat() / targetShakes.toFloat())
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
            color = SecondaryText,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { (targetShakes - shakeCount).toFloat() / targetShakes },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = GreenSuccessContent,
            trackColor = KeyButtonBgActive
        )
    }
}

package com.example.smartalarmer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SmartAlarmerColorScheme =
    darkColorScheme(
        primary = IndigoContent,
        onPrimary = DarkBgStart,
        primaryContainer = IndigoPrimary,
        onPrimaryContainer = Color.White,
        secondary = GreenSuccessContent,
        onSecondary = DarkBgStart,
        background = DarkBgStart,
        onBackground = Color.White,
        surface = BottomSheetBg,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF302C50),
        onSurfaceVariant = SecondaryText,
        error = RedErrorContent,
        onError = DarkBgStart,
        errorContainer = RedError,
        onErrorContainer = Color.White,
        outline = Color(0xFF8D89A8)
    )

@Composable
fun SmartAlarmerTheme(content: @Composable () -> Unit) {
    // The product UI is intentionally always dark. Dynamic/light schemes can
    // make inherited Material control text illegible on its fixed dark glass.
    MaterialTheme(
        colorScheme = SmartAlarmerColorScheme,
        typography = Typography,
        content = content
    )
}

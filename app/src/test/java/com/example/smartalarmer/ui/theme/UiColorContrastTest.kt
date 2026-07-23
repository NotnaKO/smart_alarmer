package com.example.smartalarmer.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class UiColorContrastTest {
    @Test
    fun filledActionsMeetNormalTextContrast() {
        assertContrast("Primary action", Color.White, IndigoPrimary)
        assertContrast("Success action", Color.White, GreenSuccess)
        assertContrast("Error action", Color.White, RedError)
        assertContrast("Material primary action", DarkBgStart, IndigoContent)
        assertContrast("Material secondary action", DarkBgStart, GreenSuccessContent)
        assertContrast("Material error action", DarkBgStart, RedErrorContent)
    }

    @Test
    fun contentTokensMeetNormalTextContrastOnEveryDarkSurface() {
        val surfaces =
            mapOf(
                "gradient start" to DarkBgStart,
                "gradient end" to DarkBgEnd,
                "puzzle screen" to DarkBgScreen,
                "bottom sheet" to BottomSheetBg
            )
        val contentColors =
            mapOf(
                "primary text" to Color.White,
                "secondary text" to SecondaryText,
                "inactive control text" to InactiveControlText,
                "disabled control text" to DisabledControlText,
                "accent text" to IndigoContent,
                "success text" to GreenSuccessContent,
                "error text" to RedErrorContent,
                "warning text" to OrangeWarning
            )

        surfaces.forEach { (surfaceName, surfaceColor) ->
            contentColors.forEach { (contentName, contentColor) ->
                assertContrast("$contentName on $surfaceName", contentColor, surfaceColor)
            }
        }
    }

    @Test
    fun translucentControlsMeetNormalTextContrastAfterCompositing() {
        val inactiveChip = KeyButtonBg.compositeOver(BottomSheetBg)
        val keyboardKey = KeyButtonBg.compositeOver(DarkBgScreen)
        val glassCard = CardBgGlass.compositeOver(DarkBgStart)

        assertContrast("Inactive chip label", InactiveControlText, inactiveChip)
        assertContrast("Disabled chip label", DisabledControlText, inactiveChip)
        assertContrast("Keyboard key", Color.White, keyboardKey)
        assertContrast("Glass card secondary text", SecondaryText, glassCard)
    }

    private fun assertContrast(
        description: String,
        foreground: Color,
        background: Color
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$description contrast ${
                String.format(
                    Locale.ROOT,
                    "%.2f",
                    ratio
                )
            }:1 is below $NORMAL_TEXT_MINIMUM:1",
            ratio >= NORMAL_TEXT_MINIMUM
        )
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color
    ): Double {
        require(foreground.alpha == 1f && background.alpha == 1f) {
            "Contrast must be measured after alpha compositing"
        }
        val foregroundLuminance = foreground.relativeLuminance()
        val backgroundLuminance = background.relativeLuminance()
        return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    private fun Color.relativeLuminance(): Double = 0.2126 * red.linearized() +
        0.7152 * green.linearized() +
        0.0722 * blue.linearized()

    private fun Float.linearized(): Double = if (this <= 0.04045f) {
        toDouble() / 12.92
    } else {
        ((toDouble() + 0.055) / 1.055).pow(2.4)
    }

    private fun Color.compositeOver(background: Color): Color {
        val resultAlpha = alpha + background.alpha * (1f - alpha)
        return Color(
            red = (red * alpha + background.red * background.alpha * (1f - alpha)) / resultAlpha,
            green = (green * alpha + background.green * background.alpha * (1f - alpha)) / resultAlpha,
            blue = (blue * alpha + background.blue * background.alpha * (1f - alpha)) / resultAlpha,
            alpha = resultAlpha
        )
    }

    private companion object {
        const val NORMAL_TEXT_MINIMUM = 4.5
    }
}

package com.example.smartalarmer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.ui.dismiss.AlarmDismissScreen
import com.example.smartalarmer.ui.dismiss.MathPuzzleView
import com.example.smartalarmer.ui.dismiss.MemoryPuzzleView
import com.example.smartalarmer.ui.dismiss.TypingPuzzleView
import com.example.smartalarmer.ui.dismiss.ShakePuzzleView
import com.example.smartalarmer.ui.dismiss.VirtualKeyboard
import com.example.smartalarmer.puzzle.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDismissScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Fake providers ────────────────────────────────────────────────────

    /** Always returns "5 + 3" with answer 8. */
    private val fakeMath = object : MathPuzzleProvider {
        override fun generate(difficulty: Difficulty) =
            MathPuzzle(equation = "5 + 3", answer = 8, difficulty = Difficulty.MEDIUM)
    }

    /** Always returns the same fixed quote. */
    private val fakeTyping = object : TypingPuzzleProvider {
        override fun getRandomQuote(quotes: List<String>) = "Wake up now"
        override fun isMatch(target: String, input: String) = target.trim() == input.trim()
    }

    /**
     * Returns sequence [0, 1, 2] and delegates real verification logic
     * so we can tap correctly without rewriting the engine.
     */
    private val fakeMemory = object : MemoryPuzzleProvider {
        override fun generateSequence(length: Int) = listOf(0, 1, 2)
        override fun verifyStep(sequence: List<Int>, userInputs: List<Int>) =
            MemoryEngine.verifyStep(sequence, userInputs)
    }

    private val fakeShake = object : ShakeSensorProvider {
        private var callback: ((Float, Float, Float) -> Unit)? = null
        override fun register(onSensorChanged: (Float, Float, Float) -> Unit) {
            callback = onSensorChanged
        }
        override fun unregister() {
            callback = null
        }
        fun simulateShake(x: Float, y: Float, z: Float) {
            callback?.invoke(x, y, z)
        }
    }

    // ── Math puzzle ───────────────────────────────────────────────────────

    @Test
    fun mathPuzzle_correctAnswer_callsOnComplete() {
        var completed = false
        composeTestRule.setContent {
            MathPuzzleView(
                onComplete = { completed = true },
                mathProvider = fakeMath,
            )
        }

        // Verify the deterministic equation is shown
        composeTestRule.onNodeWithText("5 + 3").assertIsDisplayed()

        // Tap 8, then confirm
        composeTestRule.onNodeWithText("8").performClick()
        composeTestRule.onNodeWithText("✔").performClick()

        assertTrue("onComplete should have been called", completed)
    }

    @Test
    fun mathPuzzle_wrongAnswer_clearsInputWithoutAdvancing() {
        var completed = false
        composeTestRule.setContent {
            MathPuzzleView(
                onComplete = { completed = true },
                mathProvider = fakeMath,
            )
        }

        // Tap a wrong answer (9), confirm
        composeTestRule.onNodeWithText("9").performClick()
        composeTestRule.onNodeWithText("✔").performClick()

        // Input should be cleared (label resets to empty)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val yourAnswerEmpty = context.getString(com.example.smartalarmer.R.string.your_answer_format, "")
        composeTestRule.onNodeWithText(yourAnswerEmpty).assertIsDisplayed()
        assertTrue("onComplete should NOT be called", !completed)
    }

    @Test
    fun mathPuzzle_backspace_removesLastDigit() {
        composeTestRule.setContent {
            MathPuzzleView(
                onComplete = {},
                mathProvider = fakeMath,
            )
        }

        composeTestRule.onNodeWithText("8").performClick()
        composeTestRule.onNodeWithText("5").performClick()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val yourAnswer85 = context.getString(com.example.smartalarmer.R.string.your_answer_format, "85")
        composeTestRule.onNodeWithText(yourAnswer85).assertIsDisplayed()

        composeTestRule.onNodeWithText("⌫").performClick()
        // input should be "8"
        val yourAnswer8 = context.getString(com.example.smartalarmer.R.string.your_answer_format, "8")
        composeTestRule.onNodeWithText(yourAnswer8).assertIsDisplayed()
    }

    @Test
    fun mathPuzzle_recreationPreservesEnteredAnswer() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            MathPuzzleView(onComplete = {}, mathProvider = fakeMath)
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule.onNodeWithText("8").performClick()
        restorationTester.emulateSavedInstanceStateRestore()

        val enteredAnswer = context.getString(com.example.smartalarmer.R.string.your_answer_format, "8")
        composeTestRule.onNodeWithText(enteredAnswer).assertIsDisplayed()
    }

    @Test
    fun mathPuzzle_requestsOnlyMediumOrHardDifficulty() {
        var requestedDifficulty: Difficulty? = null
        val capturingMathProvider = object : MathPuzzleProvider {
            override fun generate(difficulty: Difficulty): MathPuzzle {
                requestedDifficulty = difficulty
                return MathPuzzle(equation = "1 + 1", answer = 2, difficulty = difficulty)
            }
        }

        composeTestRule.setContent {
            MathPuzzleView(
                onComplete = {},
                mathProvider = capturingMathProvider,
            )
        }

        composeTestRule.waitForIdle()
        assertTrue(
            "Math puzzle should only request MEDIUM or HARD difficulty",
            requestedDifficulty == Difficulty.MEDIUM || requestedDifficulty == Difficulty.HARD
        )
    }

    // ── Typing puzzle ─────────────────────────────────────────────────────

    private fun simulateVirtualKeyboardInput(text: String) {
        var isShifted = false
        text.forEach { char ->
            if (char == ' ') {
                composeTestRule.onNodeWithText("Space").performClick()
            } else {
                val shouldBeShifted = char.isUpperCase()
                if (isShifted != shouldBeShifted) {
                    composeTestRule.onNodeWithText("⇧").performClick()
                    isShifted = shouldBeShifted
                }
                composeTestRule.onNodeWithText(char.toString()).performClick()
            }
        }
        if (isShifted) {
            composeTestRule.onNodeWithText("⇧").performClick()
        }
    }

    @Test
    fun typingPuzzle_exactMatch_callsOnComplete() {
        var completed = false
        composeTestRule.setContent {
            TypingPuzzleView(
                onComplete = { completed = true },
                typingProvider = fakeTyping,
            )
        }

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val submitBtn = context.getString(com.example.smartalarmer.R.string.submit_btn)
        composeTestRule.onNodeWithText("Wake up now").assertIsDisplayed()
        simulateVirtualKeyboardInput("Wake up now")
        composeTestRule.onNodeWithText(submitBtn).performClick()

        assertTrue("onComplete should have been called", completed)
    }

    @Test
    fun typingPuzzle_mismatch_doesNotCallOnComplete() {
        var completed = false
        composeTestRule.setContent {
            TypingPuzzleView(
                onComplete = { completed = true },
                typingProvider = fakeTyping,
            )
        }

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val submitBtn = context.getString(com.example.smartalarmer.R.string.submit_btn)
        simulateVirtualKeyboardInput("wrong text")
        composeTestRule.onNodeWithText(submitBtn).performClick()

        assertTrue("onComplete should NOT be called", !completed)
    }

    // ── Memory puzzle ─────────────────────────────────────────────────────

    /**
     * After the flash animation completes we need to wait for state change.
     * We use [waitUntil] to wait for "Repeat Pattern!" to appear.
     */
    @Test
    fun memoryPuzzle_correctSequence_callsOnComplete() {
        var completed = false
        composeTestRule.setContent {
            MemoryPuzzleView(
                onComplete = { completed = true },
                memoryProvider = fakeMemory,
            )
        }

        // Wait until flash animation finishes and input is allowed
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val repeatPattern = context.getString(com.example.smartalarmer.R.string.repeat_pattern)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(repeatPattern).fetchSemanticsNodes().isNotEmpty()
        }

        // Tap the three buttons in order: index 0, 1, 2
        // The 3×3 grid has 9 buttons — we click the first three (indices 0,1,2)
        val buttons = composeTestRule.onAllNodes(hasClickAction() and !hasText(repeatPattern))
        buttons[0].performClick()
        buttons[1].performClick()
        buttons[2].performClick()

        composeTestRule.waitForIdle()
        assertTrue("onComplete should have been called", completed)
    }

    @Test
    fun memoryPuzzle_wrongStep_resetsPuzzle() {
        composeTestRule.setContent {
            MemoryPuzzleView(
                onComplete = {},
                memoryProvider = fakeMemory,
            )
        }

        // Wait for input phase
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val repeatPattern = context.getString(com.example.smartalarmer.R.string.repeat_pattern)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(repeatPattern).fetchSemanticsNodes().isNotEmpty()
        }

        // Tap the wrong button (index 2 first instead of 0)
        val buttons = composeTestRule.onAllNodes(hasClickAction() and !hasText(repeatPattern))
        buttons[2].performClick()   // wrong first step

        // Puzzle should reset: "Memorize Pattern..." should reappear
        val memorizePattern = context.getString(com.example.smartalarmer.R.string.memorize_pattern)
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(memorizePattern).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun memoryPuzzle_requestsOnlyMediumOrHardSequenceLength() {
        var requestedLength: Int? = null
        val capturingMemoryProvider = object : MemoryPuzzleProvider {
            override fun generateSequence(length: Int): List<Int> {
                requestedLength = length
                return listOf(0, 1, 2)
            }

            override fun verifyStep(sequence: List<Int>, userInputs: List<Int>): Boolean {
                return MemoryEngine.verifyStep(sequence, userInputs)
            }
        }

        composeTestRule.setContent {
            MemoryPuzzleView(
                onComplete = {},
                memoryProvider = capturingMemoryProvider,
            )
        }

        composeTestRule.waitForIdle()
        assertTrue(
            "Memory puzzle should only request medium/hard sequence lengths",
            requestedLength == 5 || requestedLength == 7
        )
    }

    // ── Shake puzzle ──────────────────────────────────────────────────────

    @Test
    fun shakePuzzle_displaysInitialShakes() {
        composeTestRule.setContent {
            ShakePuzzleView(
                onComplete = {},
                shakeProvider = fakeShake
            )
        }

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val shakeDevice = context.getString(com.example.smartalarmer.R.string.shake_device)
        val shakesRemaining = context.getString(com.example.smartalarmer.R.string.shakes_remaining, 30)

        composeTestRule.onNodeWithText(shakeDevice).assertIsDisplayed()
        composeTestRule.onNodeWithText(shakesRemaining).assertIsDisplayed()
    }

    @Test
    fun shakePuzzle_simulatedShakes_callsOnComplete() {
        var completed = false
        composeTestRule.setContent {
            ShakePuzzleView(
                onComplete = { completed = true },
                shakeProvider = fakeShake
            )
        }

        // Trigger 30 shakes. Each shake consists of moving X to 25 and back to -25.
        // We must advance time slightly between calls so that lastUpdate difference is > 100ms.
        for (i in 1..30) {
            fakeShake.simulateShake(25f, 9.8f, 0f)
            // Wait 150ms
            Thread.sleep(150)
            fakeShake.simulateShake(-25f, 9.8f, 0f)
            Thread.sleep(150)
        }

        composeTestRule.waitForIdle()
        assertTrue("onComplete should have been called after 30 shakes", completed)
    }

    // ── Full flow ─────────────────────────────────────────────────────────

    @Test
    fun alarmDismissScreen_singleMathPuzzle_callsDismissOnSolve() {
        var dismissed = false
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "MATH",
                puzzleCount = 1,
                onDismissComplete = { dismissed = true },
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        composeTestRule.onNodeWithText("5 + 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("8").performClick()
        composeTestRule.onNodeWithText("✔").performClick()

        composeTestRule.waitForIdle()
        assertTrue("onDismissComplete should have been called", dismissed)
    }

    @Test
    fun alarmDismissScreen_showsProgressHeader() {
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "MATH",
                puzzleCount = 1,
                onDismissComplete = {},
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val expectedText = context.getString(com.example.smartalarmer.R.string.task_progress_format, 1, 1)
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun alarmDismissScreen_withLabel_showsLabelAtTop() {
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "MATH",
                puzzleCount = 1,
                onDismissComplete = {},
                alarmLabel = "Wake Up Gym",
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        composeTestRule.onNodeWithText("Wake Up Gym").assertIsDisplayed()
    }

    @Test
    fun alarmDismissScreen_withoutLabel_doesNotShowLabel() {
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "MATH",
                puzzleCount = 1,
                onDismissComplete = {},
                alarmLabel = "",
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        composeTestRule.onNodeWithText("Wake Up Gym").assertDoesNotExist()
    }

    @Test
    fun alarmDismissScreen_invalidPuzzleList_fallsBackToMath() {
        var dismissed = false
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "UNKNOWN,INVALID",
                puzzleCount = 2,
                onDismissComplete = { dismissed = true },
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        composeTestRule.onNodeWithText("5 + 3").assertIsDisplayed()
        assertFalse("Invalid configuration must not dismiss the alarm", dismissed)
    }

    @Test
    fun alarmDismissScreen_zeroPuzzleCount_fallsBackToMath() {
        var dismissed = false
        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "",
                puzzleCount = 0,
                onDismissComplete = { dismissed = true },
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = fakeShake,
            )
        }

        composeTestRule.onNodeWithText("5 + 3").assertIsDisplayed()
        assertFalse("Empty configuration must not dismiss the alarm", dismissed)
    }

    @Test
    fun alarmDismissScreen_unavailableShakeSensor_fallsBackToMath() {
        val unavailableShake = object : ShakeSensorProvider {
            override val isAvailable = false
            override fun register(onSensorChanged: (Float, Float, Float) -> Unit) = Unit
            override fun unregister() = Unit
        }
        var dismissed = false

        composeTestRule.setContent {
            AlarmDismissScreen(
                puzzlesList = "SHAKE",
                puzzleCount = 1,
                onDismissComplete = { dismissed = true },
                mathProvider = fakeMath,
                typingProvider = fakeTyping,
                memoryProvider = fakeMemory,
                shakeProvider = unavailableShake,
            )
        }

        composeTestRule.onNodeWithText("5 + 3").assertIsDisplayed()
        assertFalse("Missing sensor must not dismiss the alarm", dismissed)
    }

    @Test
    fun virtualKeyboard_inSpanish_displaysSpanishSpecificKeys() {
        composeTestRule.setContent {
            val config = androidx.compose.ui.platform.LocalConfiguration.current.apply {
                val locale = java.util.Locale.forLanguageTag("es")
                setLocale(locale)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config
            ) {
                VirtualKeyboard(
                    onKeyClick = {},
                    onBackspace = {}
                )
            }
        }

        // Spanish layout should have 'ñ' key displayed
        composeTestRule.onNodeWithText("ñ").assertIsDisplayed()
    }

    @Test
    fun virtualKeyboard_inGerman_displaysGermanSpecificKeys() {
        composeTestRule.setContent {
            val config = androidx.compose.ui.platform.LocalConfiguration.current.apply {
                val locale = java.util.Locale.forLanguageTag("de")
                setLocale(locale)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config
            ) {
                VirtualKeyboard(
                    onKeyClick = {},
                    onBackspace = {}
                )
            }
        }

        // German layout should have 'ü' key displayed
        composeTestRule.onNodeWithText("ü").assertIsDisplayed()
    }

    @Test
    fun virtualKeyboard_inRussian_displaysRussianSpecificKeys() {
        composeTestRule.setContent {
            val config = androidx.compose.ui.platform.LocalConfiguration.current.apply {
                val locale = java.util.Locale.forLanguageTag("ru")
                setLocale(locale)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config
            ) {
                VirtualKeyboard(
                    onKeyClick = {},
                    onBackspace = {}
                )
            }
        }

        // Russian layout should have 'й' key displayed
        composeTestRule.onNodeWithText("й").assertIsDisplayed()
    }
}

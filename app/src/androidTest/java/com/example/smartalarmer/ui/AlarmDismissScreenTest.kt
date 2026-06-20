package com.example.smartalarmer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.AlarmDismissScreen
import com.example.smartalarmer.MathPuzzleView
import com.example.smartalarmer.MemoryPuzzleView
import com.example.smartalarmer.TypingPuzzleView
import com.example.smartalarmer.ShakePuzzleView
import com.example.smartalarmer.puzzle.*
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
        override fun getRandomQuote() = "Wake up now"
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
        composeTestRule.onNodeWithText("Your Answer: ").assertIsDisplayed()
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
        // input is now "85"
        composeTestRule.onNodeWithText("Your Answer: 85").assertIsDisplayed()

        composeTestRule.onNodeWithText("⌫").performClick()
        // input should be "8"
        composeTestRule.onNodeWithText("Your Answer: 8").assertIsDisplayed()
    }

    // ── Typing puzzle ─────────────────────────────────────────────────────

    @Test
    fun typingPuzzle_exactMatch_callsOnComplete() {
        var completed = false
        composeTestRule.setContent {
            TypingPuzzleView(
                onComplete = { completed = true },
                typingProvider = fakeTyping,
            )
        }

        composeTestRule.onNodeWithText("Wake up now").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Wake up now")
        composeTestRule.onNodeWithText("Submit").performClick()

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

        composeTestRule.onNode(hasSetTextAction()).performTextInput("wrong text")
        composeTestRule.onNodeWithText("Submit").performClick()

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
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Repeat Pattern!").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap the three buttons in order: index 0, 1, 2
        // The 3×3 grid has 9 buttons — we click the first three (indices 0,1,2)
        val buttons = composeTestRule.onAllNodes(hasClickAction() and !hasText("Repeat Pattern!"))
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
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Repeat Pattern!").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap the wrong button (index 2 first instead of 0)
        val buttons = composeTestRule.onAllNodes(hasClickAction() and !hasText("Repeat Pattern!"))
        buttons[2].performClick()   // wrong first step

        // Puzzle should reset: "Memorize Pattern..." should reappear
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Memorize Pattern...").fetchSemanticsNodes().isNotEmpty()
        }
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

        composeTestRule.onNodeWithText("Shake the device!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shakes remaining: 30").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Task 1 of 1").assertIsDisplayed()
    }
}

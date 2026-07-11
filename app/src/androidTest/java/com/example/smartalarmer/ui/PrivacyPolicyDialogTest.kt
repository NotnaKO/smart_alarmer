package com.example.smartalarmer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.ui.main.PrivacyPolicyDialog
import com.example.smartalarmer.ui.theme.SmartAlarmerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyPolicyDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun policyIsVisibleAndCanBeClosed() {
        var dismissed = false

        composeTestRule.setContent {
            SmartAlarmerTheme {
                PrivacyPolicyDialog(onDismiss = { dismissed = true })
            }
        }

        composeTestRule.onNodeWithText("Privacy policy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").performClick()

        assertTrue(dismissed)
    }
}

package dev.damianpetla.physicsscene

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class PhysicsPlaygroundSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dropButtonAEventuallyReachesRemovedState() {
        rule.onNodeWithText("Falling Shatter").assertIsDisplayed()
        rule.onNodeWithText("Falling Shatter").performClick()

        rule.onNodeWithText("Drop A").assertIsDisplayed()
        rule.onNodeWithText("Drop A").performClick()

        rule.waitUntil(timeoutMillis = 8_000L) {
            rule.onAllNodesWithText("A: Removed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

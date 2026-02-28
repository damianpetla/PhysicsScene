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
    fun fallingShatter_primaryActionWorks() {
        openDemo("Falling Shatter")

        rule.onNodeWithText("Drop A").assertIsDisplayed()
        rule.onNodeWithText("Drop A").performClick()

        rule.waitUntil(timeoutMillis = 8_000L) {
            rule.onAllNodesWithText("A: Removed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun centerBurst_primaryActionWorks() {
        openDemo("Center Burst (Zero Gravity)")

        rule.onNodeWithText("Explode Center").assertIsDisplayed()
        rule.onNodeWithText("Explode Center").performClick()
        rule.onNodeWithText("Reset Scene").assertIsDisplayed()
    }

    @Test
    fun shardRecall_primaryActionWorks() {
        openDemo("Shard Recall")

        rule.onNodeWithText("Recall Shatter").assertIsDisplayed()
        rule.onNodeWithText("Recall Shatter").performClick()

        rule.waitUntil(timeoutMillis = 4_000L) {
            rule.onAllNodesWithText("Recall starts in 3s", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun emojiCannon_usesEnglishCopyAndPrimaryActionWorks() {
        openDemo("Emoji Cannon")

        rule.onNodeWithText("Fire Emoji").assertIsDisplayed()
        rule.onNodeWithText("Fire Emoji").performClick()

        rule.waitUntil(timeoutMillis = 4_000L) {
            rule.onAllNodesWithText("Fired: 1", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun respawnButton_reenablesSingleBodyWithoutSceneReset() {
        openDemo("Falling Shatter")

        rule.onNodeWithText("Drop A").performClick()
        rule.waitUntil(timeoutMillis = 8_000L) {
            rule.onAllNodesWithText("A: Removed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Respawn A").assertIsDisplayed()
        rule.onNodeWithText("Respawn A").performClick()

        rule.waitUntil(timeoutMillis = 2_000L) {
            rule.onAllNodesWithText("A: Idle", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Drop A").performClick()
        rule.waitUntil(timeoutMillis = 8_000L) {
            rule.onAllNodesWithText("A: Removed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDemo(title: String) {
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(title).performClick()
    }
}

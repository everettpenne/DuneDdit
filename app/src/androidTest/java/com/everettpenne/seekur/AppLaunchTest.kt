package com.everettpenne.seekur

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/**
 * The real app, signed out, from launch through its tabs. It exists because a glass control that sampled a layer containing itself made the
 * renderer recurse until the process died, and that only happens with the whole app composed together.
 */
class AppLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun settle() { compose.waitForIdle(); Thread.sleep(1500); compose.waitForIdle() }

    @Test fun signedOutTheAppAsksForAnAccountAndEveryTabOpens() {
        settle()
        compose.onNodeWithText("Sign in to Reddit").assertExists()
        compose.onNodeWithText("Add account").assertExists()
        compose.onNodeWithContentDescription("Profile").performClick(); settle()
        compose.onNodeWithText("No account connected").assertExists()
        compose.onNodeWithContentDescription("Search").performClick(); settle()
        compose.onNodeWithText("Search subreddits").assertExists()
        compose.onNodeWithContentDescription("Feed").performClick(); settle()
        compose.onNodeWithText("Sign in to Reddit").assertExists()
    }

    @Test fun settingsAndAccountsOpenAndTheSwitchesWork() {
        settle()
        compose.onNodeWithContentDescription("Profile").performClick(); settle()
        compose.onNodeWithContentDescription("Settings").performClick(); settle()
        compose.onNodeWithText("Autoplay videos").assertExists()
        compose.onAllNodesWithContentDescription("On")[0].performClick(); settle()
        compose.onNodeWithText("Accounts").performClick(); settle()
        compose.onNodeWithText("No accounts").assertExists()
    }
}

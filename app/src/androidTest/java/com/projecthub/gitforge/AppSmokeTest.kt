package com.projecthub.gitforge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecthub.gitforge.ui.nav.Destination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke test of the real application: the real [MainActivity], the real
 * [com.projecthub.gitforge.di.AppContainer], the real navigation graph and the real
 * screens. Nothing is stubbed, so this proves the app launches and every
 * destination can be reached.
 *
 * It runs on a device or emulator:
 *
 *   gradlew.bat connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsThePrimaryDestinations() {
        Destination.primary.forEach { destination ->
            composeRule.onNodeWithText(destination.label).assertIsDisplayed()
        }
    }

    @Test
    fun everyPrimaryDestinationCanBeOpened() {
        Destination.primary.forEach { destination ->
            composeRule.onNodeWithText(destination.label).performClick()
            composeRule.waitForIdle()
            composeRule.activityRule.scenario.onActivity { activity ->
                check(!activity.isFinishing) {
                    "The activity finished after opening ${destination.label}"
                }
            }
        }
    }

    @Test
    fun settingsIsReachableThroughTheDrawerAndRendersItsSections() {
        composeRule.onNodeWithContentDescription("Open navigation drawer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Terminal").assertIsDisplayed()
        composeRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun everySecondaryDestinationCanBeOpened() {
        Destination.secondary.forEach { destination ->
            composeRule.onNodeWithContentDescription("Open navigation drawer").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(destination.label).performClick()
            composeRule.waitForIdle()
            composeRule.activityRule.scenario.onActivity { activity ->
                check(!activity.isFinishing) {
                    "The activity finished after opening ${destination.label}"
                }
            }
        }
    }
}

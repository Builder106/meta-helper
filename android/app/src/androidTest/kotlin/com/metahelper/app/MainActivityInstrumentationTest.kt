package com.metahelper.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.metahelper.app.ui.theme.MetaHelperTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentationTest {
    @get:Rule
    @Suppress("DEPRECATION")
    val composeTestRule = createComposeRule()

    @Test
    fun primaryScreen_showsGalleryWorkflow_andReplayInvokesCallback() {
        val replayRequested = AtomicBoolean(false)

        composeTestRule.setContent {
            MetaHelperTheme {
                MainScreenForTest(onReplayLastAudio = { replayRequested.set(true) })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("MetaHelper").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Reads code aloud through your glasses.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("GALLERY WATCHER ACTIVE").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("1. Take a photo with your glasses.", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Replay Last Answer").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue("Replay action did not invoke the activity callback", replayRequested.get())
    }
}

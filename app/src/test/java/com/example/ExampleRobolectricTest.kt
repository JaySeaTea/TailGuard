package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.service.TailscaleBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches TailGuard and Dashboard rapid toggle dispatches Tailscale broadcast`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("TailGuard", context.getString(R.string.app_name))

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rapid_toggle_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rapid_toggle_button").performClick()
        composeTestRule.waitForIdle()

        val appShadow = shadowOf(ApplicationProvider.getApplicationContext<TailGuardApplication>())
        val broadcastIntents: List<Intent> = appShadow.broadcastIntents
        assertTrue(
            "Expected official com.tailscale.ipn.CONNECT_VPN broadcast intent",
            broadcastIntents.any {
                it.action == TailscaleBridge.ACTION_CONNECT_VPN &&
                    it.component?.packageName == TailscaleBridge.TAILSCALE_PACKAGE &&
                    it.component?.className == TailscaleBridge.TAILSCALE_RECEIVER
            }
        )
    }

    @Test
    fun `can navigate across Trusted Networks and Transition Logs tabs`() {
        composeTestRule.waitForIdle()

        // Navigate to Trusted Networks tab and open the Save Network dialog
        composeTestRule.onNodeWithTag("nav_tab_trusted").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trust_current_network_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("trust_current_network_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_save_network_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Transition Logs tab
        composeTestRule.onNodeWithTag("nav_tab_logs").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("filter_chip_all").assertIsDisplayed()
    }
}

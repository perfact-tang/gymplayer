package com.example.gymplayer.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vibecodingjapan.gymplayer.ui.GymPlayerTheme
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun appTheme_rendersContent() {
    composeTestRule.setContent {
      GymPlayerTheme {
        Text("GymPlayer")
      }
    }
    composeTestRule.waitForIdle()
  }
}

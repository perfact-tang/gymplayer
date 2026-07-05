package com.vibecodingjapan.gymplayer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vibecodingjapan.gymplayer.ui.GymPlayerApp
import com.vibecodingjapan.gymplayer.ui.GymPlayerTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent { GymPlayerTheme { GymPlayerApp() } }
  }
}

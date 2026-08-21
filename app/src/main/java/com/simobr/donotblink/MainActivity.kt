package com.simobr.donotblink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash window is released on the first frame. The splash the player sees is
        // the Compose one, which is where the startup work will run.
        val systemSplash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemSplash.setKeepOnScreenCondition { false }

        setContent {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Black,
            ) { _ ->
                // The game draws edge to edge on purpose; each screen carries its own insets.
                AppRoot(viewModel = viewModel(factory = GameViewModel.Factory))
            }
        }
    }
}

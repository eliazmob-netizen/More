package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.SeaScreen
import com.example.ui.SeaViewModel
import com.example.ui.theme.SeaAndSkyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SeaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            SeaAndSkyTheme {
                SeaScreen(viewModel = viewModel)
            }
        }
    }
}

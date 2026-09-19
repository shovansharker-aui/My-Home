package com.mijia4k.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.mijia4k.app.ui.Mijia4kNavHost
import com.mijia4k.app.ui.theme.Mijia4kTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Mijia4kApp()
        }
    }
}

@Composable
private fun Mijia4kApp() {
    Mijia4kTheme {
        // No Scaffold here: every screen already has its own, and wrapping
        // them in a second one applied the system-bar insets twice — that was
        // the oversized gap above each screen's title.
        Mijia4kNavHost(rememberNavController())
    }
}

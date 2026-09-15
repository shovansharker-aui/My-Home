package com.mijia4k.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        val navController = rememberNavController()
        Scaffold { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                Mijia4kNavHost(navController)
            }
        }
    }
}

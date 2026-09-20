package com.mijia4k.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mijia4k.app.ui.AppOrientation
import com.mijia4k.app.ui.Mijia4kNavHost
import com.mijia4k.app.ui.theme.Mijia4kTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppOrientation.load(applicationContext)
        lifecycleScope.launch {
            AppOrientation.mode.collect { requestedOrientation = it.requested }
        }
        setContent {
            Mijia4kApp()
        }
    }

    // Volume keys take the picture while the live screen is open.
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) &&
            com.mijia4k.app.ui.HardwareKeys.shutterScreenActive
        ) {
            if (event?.repeatCount == 0) com.mijia4k.app.ui.HardwareKeys.shutter.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
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

package com.mijia4k.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mijia4k.app.home.ShareInbox
import com.mijia4k.app.ui.AppNavHost
import com.mijia4k.app.ui.AppOrientation
import com.mijia4k.app.ui.theme.MyHomeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppOrientation.load(applicationContext)
        lifecycleScope.launch {
            AppOrientation.mode.collect { requestedOrientation = it.requested }
        }
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            Mijia4kApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    /** Something shared from another app (Google Keep's "Send", a picture): hand it to the printer's Text screen. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val subject = (intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE))
            ?.takeIf { it.isNotBlank() }
        val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        // Keep sends the note's title as the subject and its body as the text.
        val text = listOfNotNull(subject, body).distinct().joinToString("\n\n").ifBlank { null }
        if (text != null || stream != null) ShareInbox.offer(text, stream)
    }

    // Volume keys take the picture while the camera's live screen is open.
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
    MyHomeTheme {
        // No Scaffold here: every screen already has its own, and wrapping
        // them in a second one applied the system-bar insets twice — that was
        // the oversized gap above each screen's title.
        AppNavHost(rememberNavController())
    }
}

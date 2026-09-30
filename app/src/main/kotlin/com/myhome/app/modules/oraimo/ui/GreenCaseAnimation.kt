package com.myhome.app.modules.oraimo.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.libpag.PAGFile
import org.libpag.PAGView

/**
 * The FreePods Lite case opening, played from the same PAG animation file the
 * official Oraimo app shows (kept in assets/oraimo/case_open.pag). It plays
 * once when the page opens and again when tapped.
 */
@Composable
fun GreenCaseAnimation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember { runCatching { PAGFile.Load(context.assets, "oraimo/case_open.pag") }.getOrNull() } ?: return
    val ratio = file.width().toFloat() / file.height().coerceAtLeast(1)

    AndroidView(
        modifier = modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(ratio),
        factory = { ctx ->
            PAGView(ctx).apply {
                composition = file
                setRepeatCount(1)
                setOnClickListener {
                    progress = 0.0
                    play()
                }
                play()
            }
        },
        onRelease = { it.stop() },
    )
}

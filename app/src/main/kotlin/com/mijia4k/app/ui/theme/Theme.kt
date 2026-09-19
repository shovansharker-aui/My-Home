package com.mijia4k.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Matches the stock Mi Home app's accent (its shutter button, selected mode,
// and selected-option highlight are all this teal) rather than the earlier
// arbitrary orange.
val MijiaTeal = androidx.compose.ui.graphics.Color(0xFF00BFA5)

private val DarkColors = darkColorScheme(
    primary = MijiaTeal,
    secondary = androidx.compose.ui.graphics.Color(0xFFFFC107),
)

private val LightColors = lightColorScheme(
    primary = MijiaTeal,
    secondary = androidx.compose.ui.graphics.Color(0xFFFFC107),
)

@Composable
fun Mijia4kTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: dynamic color pulls from the phone's wallpaper, which
    // would override the stock app's consistent teal accent with whatever
    // color the user's wallpaper happens to be.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

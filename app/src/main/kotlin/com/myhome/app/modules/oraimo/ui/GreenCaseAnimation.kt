package com.myhome.app.modules.oraimo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val ShellTop = Color(0xFF4CC985)
private val ShellBottom = Color(0xFF2B9A60)
private val LidTop = Color(0xFF6FDDA0)
private val LidBottom = Color(0xFF45BE7F)
private val Cavity = Color(0xFF1C6A43)
private val Pod = Color(0xFFF3FBF6)
private val PodShade = Color(0xFFC9E6D5)
private val Glow = Color(0xFF3DDC97)

/**
 * The green FreePods Lite case swinging its lid open and lifting the two
 * earbuds out — drawn in code, played when the page opens and again on a tap.
 */
@Composable
fun GreenCaseAnimation(modifier: Modifier = Modifier) {
    var plays by remember { mutableIntStateOf(0) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(plays) {
        open.snapTo(0f)
        delay(400)
        open.animateTo(1f, tween(1700, easing = FastOutSlowInEasing))
    }
    val pulse by rememberInfiniteTransition(label = "led").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "ledPulse",
    )

    Canvas(
        modifier
            .width(280.dp)
            .height(250.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { plays++ },
    ) {
        val u = size.width / 280f
        val p = open.value
        translate(top = 46f * u) { drawScene(u, p, pulse) }
    }
}

private fun DrawScope.drawScene(u: Float, p: Float, pulse: Float) {
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)

    // Ground shadow and a soft glow that appears as the case opens.
    drawOval(Color(0x22000000), o(50f, 186f), sz(180f, 16f))
    drawCircle(
        Brush.radialGradient(listOf(Glow.copy(alpha = 0.32f * p), Color.Transparent), o(140f, 100f), 130f * u),
        radius = 130f * u, center = o(140f, 100f),
    )

    // The dark opening at the top of the shell.
    drawRoundRect(Cavity.copy(alpha = p), o(52f, 104f), sz(176f, 26f), CornerRadius(13f * u))

    // Earbuds rise out of the case once the lid is mostly open.
    val rise = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
    val eased = rise * rise * (3f - 2f * rise)
    val podY = 148f - 66f * eased
    for (x in listOf(108f, 172f)) drawEarbud(u, x, podY)

    // Front of the shell hides the lower part of the earbuds.
    drawRoundRect(
        Brush.verticalGradient(listOf(ShellTop, ShellBottom), o(0f, 116f).y, o(0f, 190f).y),
        o(40f, 116f), sz(200f, 74f), CornerRadius(32f * u),
    )
    drawRoundRect(Color(0x33FFFFFF), o(52f, 120f), sz(176f, 3f), CornerRadius(2f * u))
    // The status light on the front.
    drawCircle(Color.White.copy(alpha = if (p > 0.6f) pulse else 0.55f), radius = 3.4f * u, center = o(140f, 160f))

    // The lid, hinged at its right-hand end, swings up and away.
    val angle = 58f * p
    rotate(degrees = angle, pivot = o(240f, 122f)) {
        drawRoundRect(
            Brush.verticalGradient(listOf(LidTop, LidBottom), o(0f, 76f).y, o(0f, 124f).y),
            o(40f, 76f), sz(200f, 48f), CornerRadius(30f * u),
        )
        drawRoundRect(Color(0x40FFFFFF), o(56f, 84f), sz(120f, 4f), CornerRadius(2f * u))
        drawRoundRect(Color(0x33000000), o(40f, 76f), sz(200f, 48f), CornerRadius(30f * u), style = Stroke(1.2f * u))
    }
}

private fun DrawScope.drawEarbud(u: Float, cx: Float, cy: Float) {
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    drawRoundRect(Pod, o(cx - 4.5f, cy), Size(9f * u, 34f * u), CornerRadius(4.5f * u))
    drawCircle(Pod, radius = 17f * u, center = o(cx, cy))
    drawCircle(PodShade, radius = 9.5f * u, center = o(cx, cy))
    drawCircle(Color(0x55FFFFFF), radius = 4f * u, center = o(cx - 5f, cy - 6f))
}

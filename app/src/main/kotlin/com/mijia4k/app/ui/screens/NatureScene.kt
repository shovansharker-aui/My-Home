package com.mijia4k.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A soft, minimal vector landscape — pale sky, low sun, layered hills and a
 * calm lake. Drawn entirely from paths so it scales cleanly to any screen
 * shape, portrait or landscape.
 */
@Composable
fun NatureScene(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // Sky
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFFFBE6D6),
                0.45f to Color(0xFFDCEAF3),
                1f to Color(0xFFC3E0E6),
            ),
        )

        // Sun with soft halos
        val sun = Offset(w * 0.72f, h * 0.33f)
        val r = minOf(w, h)
        drawCircle(Color(0xFFFFE8C8).copy(alpha = 0.30f), radius = r * 0.30f, center = sun)
        drawCircle(Color(0xFFFFE1B5).copy(alpha = 0.50f), radius = r * 0.19f, center = sun)
        drawCircle(Color(0xFFFFF4E0), radius = r * 0.10f, center = sun)

        // Clouds
        cloud(w * 0.20f, h * 0.22f, r * 0.15f, Color(0xFFFCF7F3))
        cloud(w * 0.58f, h * 0.20f, r * 0.10f, Color(0xFFF7F3F1))
        cloud(w * 0.88f, h * 0.47f, r * 0.09f, Color(0xFFEFF0F0))

        // Distant birds
        bird(w * 0.34f, h * 0.38f, r * 0.03f)
        bird(w * 0.41f, h * 0.355f, r * 0.022f)
        bird(w * 0.27f, h * 0.42f, r * 0.018f)

        // Far mountains
        layer(h * 0.50f, listOf(0.00f to 0.06f, 0.22f to -0.10f, 0.46f to 0.02f, 0.70f to -0.12f, 1.00f to 0.04f), Color(0xFFBFD3DF), w, h)
        // Middle hills
        layer(h * 0.60f, listOf(0.00f to -0.02f, 0.30f to -0.08f, 0.58f to 0.03f, 0.82f to -0.06f, 1.00f to 0.00f), Color(0xFFA5C9B8), w, h)

        // Lake
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFFB9DDE2),
                1f to Color(0xFF9CCBD3),
                startY = h * 0.66f,
                endY = h,
            ),
            topLeft = Offset(0f, h * 0.66f),
            size = Size(w, h * 0.34f),
        )
        // Gentle ripples
        val ripple = Color.White.copy(alpha = 0.35f)
        for ((i, y) in listOf(0.72f, 0.77f, 0.83f, 0.90f).withIndex()) {
            val len = w * (0.14f + 0.05f * i)
            val x = w * (0.18f + 0.22f * ((i * 3) % 4))
            drawLine(ripple, Offset(x, h * y), Offset(x + len, h * y), strokeWidth = 2.5f * (1 + i * 0.4f), cap = StrokeCap.Round)
        }

        // Near shore hill (left) with a few soft pines
        val shore = Path().apply {
            moveTo(0f, h * 0.74f)
            cubicTo(w * 0.12f, h * 0.62f, w * 0.30f, h * 0.66f, w * 0.44f, h * 0.76f)
            cubicTo(w * 0.50f, h * 0.80f, w * 0.30f, h * 0.84f, 0f, h * 0.86f)
            close()
        }
        drawPath(shore, Color(0xFF7FB39B))
        pine(w * 0.08f, h * 0.66f, r * 0.10f, Color(0xFF5E9A84))
        pine(w * 0.16f, h * 0.64f, r * 0.13f, Color(0xFF4F8B76))
        pine(w * 0.24f, h * 0.68f, r * 0.08f, Color(0xFF5E9A84))

        // Foreground bank
        val bank = Path().apply {
            moveTo(w, h * 0.90f)
            cubicTo(w * 0.80f, h * 0.86f, w * 0.60f, h * 0.94f, w * 0.40f, h)
            lineTo(w, h)
            close()
        }
        drawPath(bank, Color(0xFF74A98F))
    }
}

private fun DrawScope.layer(baseY: Float, points: List<Pair<Float, Float>>, color: Color, w: Float, h: Float) {
    val path = Path().apply {
        moveTo(0f, baseY + h * points.first().second)
        for (i in 1 until points.size) {
            val (px, py) = points[i - 1]
            val (x, y) = points[i]
            val midX = (px + x) / 2f
            cubicTo(w * midX, baseY + h * py, w * midX, baseY + h * y, w * x, baseY + h * y)
        }
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.bird(x: Float, y: Float, s: Float) {
    val stroke = Stroke(width = s * 0.18f, cap = StrokeCap.Round)
    val col = Color(0xFF7A8FA0).copy(alpha = 0.8f)
    drawArc(col, 200f, 140f, false, Offset(x - s, y - s * 0.5f), Size(s, s), style = stroke)
    drawArc(col, 200f, 140f, false, Offset(x, y - s * 0.5f), Size(s, s), style = stroke)
}

private fun DrawScope.pine(cx: Float, baseY: Float, s: Float, color: Color) {
    for (i in 0..2) {
        val top = baseY - s * (1.0f + 0.55f * i)
        val half = s * (0.55f - 0.13f * i)
        val tri = Path().apply {
            moveTo(cx, top)
            lineTo(cx + half, top + s * 0.75f)
            lineTo(cx - half, top + s * 0.75f)
            close()
        }
        drawPath(tri, color)
    }
    drawRect(Color(0xFF6B8E7F), topLeft = Offset(cx - s * 0.05f, baseY - s * 0.05f), size = Size(s * 0.10f, s * 0.28f))
}

private fun DrawScope.cloud(cx: Float, cy: Float, s: Float, c: Color) {
    drawCircle(c, radius = s * 0.34f, center = Offset(cx - s * 0.30f, cy + s * 0.04f))
    drawCircle(c, radius = s * 0.46f, center = Offset(cx, cy - s * 0.06f))
    drawCircle(c, radius = s * 0.36f, center = Offset(cx + s * 0.34f, cy + s * 0.03f))
    drawRoundRect(
        c,
        topLeft = Offset(cx - s * 0.62f, cy + s * 0.02f),
        size = Size(s * 1.30f, s * 0.36f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.18f),
    )
}

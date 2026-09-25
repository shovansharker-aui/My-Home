package com.myhome.app.modules.led.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val Selected = Color(0xFFFF8A00)
private val WheelInk = Color(0xFF3F5A69)

/**
 * A hue-and-saturation colour wheel: the angle is the hue, the distance from the
 * middle is how strong the colour is. Touch or drag anywhere on it.
 */
@Composable
fun ColorWheel(hue: Float, sat: Float, enabled: Boolean, modifier: Modifier = Modifier, onChange: (hue: Float, sat: Float) -> Unit) {
    val current = Color.hsv(hue, sat, 1f)
    fun pick(p: Offset, size: androidx.compose.ui.unit.IntSize) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(cx, cy)
        val dx = p.x - cx
        val dy = p.y - cy
        val deg = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
        onChange(deg, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }
    Canvas(
        modifier
            .size(250.dp)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { pick(it, size) }
            }
            .pointerInput(enabled) {
                if (enabled) detectDragGestures(onDragStart = { pick(it, size) }) { change, _ ->
                    change.consume()
                    pick(change.position, size)
                }
            },
    ) {
        val c = center
        val r = min(size.width, size.height) / 2f - 14.dp.toPx()
        drawCircle(
            Brush.sweepGradient((0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }, c),
            radius = r, center = c,
        )
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), c, r), radius = r, center = c)
        drawCircle(Color(0x22000000), radius = r, center = c, style = Stroke(1.5.dp.toPx()))

        val a = hue * PI.toFloat() / 180f
        val m = Offset(c.x + r * sat * cos(a), c.y + r * sat * sin(a))
        drawCircle(current, radius = 13.dp.toPx(), center = m)
        drawCircle(Color.White, radius = 13.dp.toPx(), center = m, style = Stroke(3.dp.toPx()))
        drawCircle(Color(0x44000000), radius = 15.dp.toPx(), center = m, style = Stroke(1.dp.toPx()))
    }
}

/**
 * A vertical wheel of effect names: scroll and it settles on one row, which is
 * the selected effect (orange, like the stock app); tap a row to jump to it.
 * [onPick] runs once the wheel comes to rest on a new row.
 */
@Composable
fun EffectWheel(names: List<String>, selected: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val rowHeight = 46.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, names.lastIndex))
    val scope = rememberCoroutineScope()
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowHeight.toPx() }

    // Which row sits in the middle: the first visible row plus how far it has scrolled.
    val position = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / rowPx
    val centred = position.toInt().let { if (position - it >= 0.5f) it + 1 else it }.coerceIn(0, names.lastIndex)

    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (moving) userScrolled = true
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            if (userScrolled) {
                userScrolled = false
                val p = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / rowPx
                onPick(p.toInt().let { i -> if (p - i >= 0.5f) i + 1 else i }.coerceIn(0, names.lastIndex))
            }
        }
    }

    // Two blank rows above and below so the first and last effects can reach the middle.
    val padded = listOf<String?>(null, null) + names + listOf(null, null)
    Box(
        Modifier
            .fillMaxWidth()
            .height(rowHeight * 5)
            .drawBehind {
                val top = rowHeight.toPx() * 2
                val bottom = rowHeight.toPx() * 3
                drawLine(WheelInk.copy(alpha = 0.25f), Offset(0f, top), Offset(size.width, top), 1.5.dp.toPx())
                drawLine(WheelInk.copy(alpha = 0.25f), Offset(0f, bottom), Offset(size.width, bottom), 1.5.dp.toPx())
            },
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            userScrollEnabled = enabled,
            modifier = Modifier.fillMaxWidth().height(rowHeight * 5),
        ) {
            itemsIndexed(padded) { listIndex, name ->
                // Distance in rows from the middle of the wheel.
                val distance = kotlin.math.abs(listIndex - 2 - position)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .graphicsLayer {
                            val f = min(distance, 2.5f)
                            alpha = 1f - 0.32f * f
                            scaleX = 1f - 0.06f * f
                            scaleY = scaleX
                        }
                        .pointerInput(name, enabled) {
                            if (enabled && name != null) detectTapGestures {
                                scope.launch { state.animateScrollToItem(listIndex - 2) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (name != null) {
                        val isCentre = listIndex - 2 == centred
                        Text(
                            name,
                            color = if (isCentre) Selected else WheelInk,
                            fontSize = if (isCentre) 21.sp else 18.sp,
                            fontWeight = if (isCentre) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

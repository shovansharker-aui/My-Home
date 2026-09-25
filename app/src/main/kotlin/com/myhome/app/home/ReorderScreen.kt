package com.myhome.app.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF3F5A69)

/** Full-screen list for choosing the order of the module tiles: a row per module with up and down arrows. */
@Composable
fun ReorderScreen(modules: List<HomeModule>, onMove: (index: Int, delta: Int) -> Unit, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFBEBDD), Color(0xFFE4EFF5), Color(0xFFD3E9E8))),
        ),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("Reorganize", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 40.dp))
            Text(
                "Use the arrows to move a module up or down. The dashboard follows this order.",
                color = Ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 16.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modules.forEachIndexed { index, module ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.82f)).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFFFBE6D6), Color(0xFFC3E0E6)))),
                            contentAlignment = Alignment.Center,
                        ) { Image(painterResource(module.iconRes), contentDescription = null, modifier = Modifier.size(42.dp)) }
                        Text(module.title, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 14.dp))
                        IconButton(onClick = { onMove(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = Ink.copy(alpha = if (index > 0) 1f else 0.25f))
                        }
                        IconButton(onClick = { onMove(index, 1) }, enabled = index < modules.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = Ink.copy(alpha = if (index < modules.lastIndex) 1f else 0.25f))
                        }
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("Done") }
        }
    }
}

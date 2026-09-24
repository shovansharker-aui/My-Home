package com.mijia4k.app.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

private val Ink = Color(0xFF3F5A69)

/** The shell's front page: a greeting and one tile per installed module. */
@Composable
fun HomeScreen(modules: List<HomeModule>, onOpenModule: (HomeModule) -> Unit) {
    var showMore by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFBEBDD), Color(0xFFE4EFF5), Color(0xFFD3E9E8)),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(horizontal = 26.dp).padding(top = 36.dp, bottom = 8.dp)) {
                Text(greeting(), color = Ink.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                Text("My Home", color = Ink, fontSize = 42.sp, fontWeight = FontWeight.SemiBold)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Modules",
                        color = Ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                    )
                }
                items(modules, key = { it.id }) { module ->
                    ModuleCard(module, onClick = { onOpenModule(module) })
                }
                item { AddModuleCard(onClick = { showMore = true }) }
            }
        }
    }

    if (showMore) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            confirmButton = { TextButton(onClick = { showMore = false }) { Text("OK") } },
            title = { Text("More modules") },
            text = { Text("My Home is built from modules. The Mijia 4K camera is the first one — more will appear here as they are added.") },
        )
    }
}

@Composable
private fun ModuleCard(module: HomeModule, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFBE6D6), Color(0xFFC3E0E6)))),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(module.iconRes), contentDescription = null, modifier = Modifier.size(54.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(module.title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            module.description,
            color = Ink.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AddModuleCard(onClick: () -> Unit) {
    val dash = Ink.copy(alpha = 0.35f)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
                )
            }
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Ink.copy(alpha = 0.6f), modifier = Modifier.size(34.dp))
        Text(
            "Add module",
            color = Ink.copy(alpha = 0.7f),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

package com.myhome.app.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalTime

private val Ink = Color(0xFF3F5A69)

/** The shell's front page: a greeting and one tile per installed module, as a list of cards or a compact icon grid. */
@Composable
fun HomeScreen(modules: List<HomeModule>, onOpenModule: (HomeModule) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var order by remember { mutableStateOf(ModuleOrder.load(context)) }
    var layout by remember { mutableStateOf(HomeLayout.load(context)) }
    var reorganizing by remember { mutableStateOf(false) }
    val ordered = ModuleOrder.apply(modules, order)

    // Compose's own drawer-closes-on-back only kicks in with predictive back;
    // without this, Back while the drawer is open falls through and exits the app.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Settings", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(28.dp, 40.dp, 28.dp, 16.dp))
                Text(
                    "Dashboard layout",
                    color = Ink.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 28.dp, bottom = 4.dp),
                )
                NavigationDrawerItem(
                    label = { Text("List view") },
                    icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
                    selected = layout == HomeLayout.Mode.LIST,
                    onClick = { layout = HomeLayout.Mode.LIST; HomeLayout.save(context, layout); scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Grid view — two columns") },
                    icon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                    selected = layout == HomeLayout.Mode.GRID,
                    onClick = { layout = HomeLayout.Mode.GRID; HomeLayout.save(context, layout); scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Text(
                    "Modules",
                    color = Ink.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Reorganize modules") },
                    icon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; reorganizing = true },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
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
                Text("Ahshan's Home", color = Ink, fontSize = 42.sp, fontWeight = FontWeight.SemiBold)
            }

            LazyVerticalGrid(
                columns = if (layout == HomeLayout.Mode.GRID) GridCells.Fixed(2) else GridCells.Adaptive(160.dp),
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
                items(ordered, key = { it.id }) { module ->
                    if (layout == HomeLayout.Mode.GRID) {
                        CompactModuleCard(module, onClick = { onOpenModule(module) })
                    } else {
                        ModuleCard(module, onClick = { onOpenModule(module) })
                    }
                }
            }
        }
    }
    if (reorganizing) {
        ReorderScreen(
            modules = ordered,
            onMove = { index, delta ->
                val ids = ordered.map { it.id }.toMutableList()
                val to = (index + delta).coerceIn(0, ids.lastIndex)
                ids.add(to, ids.removeAt(index))
                order = ids
                ModuleOrder.save(context, ids)
            },
            onDone = { reorganizing = false },
        )
    }
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

/** A compact tile for the two-column grid: just an icon and a name, both sized to the tile's own width. */
@Composable
private fun CompactModuleCard(module: HomeModule, onClick: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .clickable(onClick = onClick),
    ) {
        val iconSize = maxWidth * 0.4f
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(iconSize * 0.28f))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFBE6D6), Color(0xFFC3E0E6)))),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(module.iconRes), contentDescription = null, modifier = Modifier.size(iconSize * 0.86f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                module.title,
                color = Ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

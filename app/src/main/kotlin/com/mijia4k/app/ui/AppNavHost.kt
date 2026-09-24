package com.mijia4k.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mijia4k.app.home.HomeScreen
import com.mijia4k.app.home.ModuleRegistry
import com.mijia4k.app.home.ShareInbox
import com.mijia4k.app.modules.printer.PrinterModule

object Routes {
    const val HOME = "home"
}

/** The shell's navigation: the home screen, plus whatever graphs the registered modules add. */
@Composable
fun AppNavHost(navController: NavHostController) {
    val shared by ShareInbox.pending.collectAsState()

    // Anything shared in from another app goes straight to the printer's Text screen.
    LaunchedEffect(shared?.id) {
        if (shared != null) navController.navigate(PrinterModule.Routes.TEXT) { launchSingleTop = true }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                modules = ModuleRegistry.modules,
                onOpenModule = { module -> navController.navigate(module.entryRoute) },
            )
        }
        ModuleRegistry.modules.forEach { module -> module.register(this, navController) }
    }
}

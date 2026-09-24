package com.mijia4k.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mijia4k.app.home.HomeScreen
import com.mijia4k.app.home.ModuleRegistry

object Routes {
    const val HOME = "home"
}

/** The shell's navigation: the home screen, plus whatever graphs the registered modules add. */
@Composable
fun AppNavHost(navController: NavHostController) {
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

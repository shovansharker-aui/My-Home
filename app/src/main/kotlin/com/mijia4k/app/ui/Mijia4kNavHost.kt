package com.mijia4k.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mijia4k.app.ui.screens.ConnectScreen
import com.mijia4k.app.ui.screens.DiagnosticsScreen
import com.mijia4k.app.ui.screens.GalleryScreen
import com.mijia4k.app.ui.screens.SettingsScreen
import com.mijia4k.app.ui.screens.ShootScreen

object Routes {
    const val CONNECT = "connect"
    const val SHOOT = "shoot"
    const val GALLERY = "gallery"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
}

@Composable
fun Mijia4kNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.CONNECT) {
        composable(Routes.CONNECT) {
            ConnectScreen(
                // Home stays on the back stack: it's the hub that also holds
                // Album, and popping it left the live screen's back arrow
                // with nothing to pop — the home page became unreachable
                // without killing the app.
                onConnected = { navController.navigate(Routes.SHOOT) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
            )
        }
        composable(Routes.SHOOT) {
            ShootScreen(
                onBack = { navController.popBackStack() },
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.GALLERY) { GalleryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
            )
        }
    }
}

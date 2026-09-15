package com.mijia4k.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mijia4k.app.ui.screens.ConnectScreen
import com.mijia4k.app.ui.screens.DiagnosticsScreen
import com.mijia4k.app.ui.screens.GalleryScreen
import com.mijia4k.app.ui.screens.ShootScreen

object Routes {
    const val CONNECT = "connect"
    const val SHOOT = "shoot"
    const val GALLERY = "gallery"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun Mijia4kNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.CONNECT) {
        composable(Routes.CONNECT) {
            ConnectScreen(
                onOpenShoot = { navController.navigate(Routes.SHOOT) },
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
            )
        }
        composable(Routes.SHOOT) { ShootScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.GALLERY) { GalleryScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
    }
}

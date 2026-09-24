package com.mijia4k.app.modules.mijia

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.mijia4k.app.R
import com.mijia4k.app.home.HomeModule
import com.mijia4k.app.ui.screens.ConnectScreen
import com.mijia4k.app.ui.screens.DiagnosticsScreen
import com.mijia4k.app.ui.screens.GalleryScreen
import com.mijia4k.app.ui.screens.SettingsScreen
import com.mijia4k.app.ui.screens.ShootScreen

/** The Xiaomi Mijia 4K action camera: live view, shooting controls, album and camera settings. */
object MijiaCameraModule : HomeModule {
    override val id = "mijia4k"
    override val title = "Mijia 4K Camera"
    override val description = "Action camera"
    override val iconRes = R.drawable.ic_module_mijia
    override val entryRoute = Routes.ROOT

    object Routes {
        const val ROOT = "mijia"
        const val CONNECT = "mijia/connect"
        const val SHOOT = "mijia/shoot"
        const val GALLERY = "mijia/gallery"
        const val DIAGNOSTICS = "mijia/diagnostics"
        const val SETTINGS = "mijia/settings"
    }

    override fun register(builder: NavGraphBuilder, navController: NavHostController) {
        builder.navigation(startDestination = Routes.CONNECT, route = Routes.ROOT) {
            composable(Routes.CONNECT) {
                ConnectScreen(
                    // The camera's landing page stays on the back stack: it also
                    // holds the Album link, and popping it left the live screen's
                    // back arrow with nothing to return to.
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
}

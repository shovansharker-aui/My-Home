package com.mijia4k.app.modules.led

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.mijia4k.app.R
import com.mijia4k.app.home.HomeModule
import com.mijia4k.app.modules.led.ui.LedControlScreen
import com.mijia4k.app.modules.led.ui.LedScanScreen

/** ELK-BLEDOM Bluetooth LED strips (the ones the Lotus Lantern app drives): power, colour, brightness, effects. */
object LedModule : HomeModule {
    override val id = "led"
    override val title = "LED Strip"
    override val description = "Bluetooth lights"
    override val iconRes = R.drawable.ic_module_led
    override val entryRoute = Routes.ROOT

    object Routes {
        const val ROOT = "led"
        const val HOME = "led/home"
        const val SCAN = "led/scan"
    }

    override fun register(builder: NavGraphBuilder, navController: NavHostController) {
        val back: () -> Unit = { navController.popBackStack() }
        builder.navigation(startDestination = Routes.HOME, route = Routes.ROOT) {
            composable(Routes.HOME) { LedControlScreen(onBack = back, onOpenScan = { navController.navigate(Routes.SCAN) }) }
            composable(Routes.SCAN) { LedScanScreen(onBack = back, onConnected = back) }
        }
    }
}

package com.myhome.app.modules.oraimo

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.myhome.app.R
import com.myhome.app.home.HomeModule
import com.myhome.app.modules.oraimo.ui.EarbudsPickScreen
import com.myhome.app.modules.oraimo.ui.EarbudsScreen

/** oraimo FreePods earbuds: battery of each earbud and the case, equaliser presets and game mode. */
object OraimoModule : HomeModule {
    override val id = "oraimo"
    override val title = "Oraimo Sound"
    override val description = "FreePods earbuds"
    override val iconRes = R.drawable.ic_module_oraimo
    override val entryRoute = Routes.ROOT

    object Routes {
        const val ROOT = "oraimo"
        const val HOME = "oraimo/home"
        const val PICK = "oraimo/pick"
    }

    override fun register(builder: NavGraphBuilder, navController: NavHostController) {
        val back: () -> Unit = { navController.popBackStack() }
        builder.navigation(startDestination = Routes.HOME, route = Routes.ROOT) {
            composable(Routes.HOME) { EarbudsScreen(onBack = back, onPick = { navController.navigate(Routes.PICK) }) }
            composable(Routes.PICK) { EarbudsPickScreen(onBack = back, onConnected = back) }
        }
    }
}

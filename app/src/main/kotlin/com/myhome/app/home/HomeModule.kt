package com.myhome.app.home

import androidx.annotation.DrawableRes
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.myhome.app.modules.mijia.MijiaCameraModule
import com.myhome.app.modules.led.LedModule
import com.myhome.app.modules.printer.PrinterModule

/**
 * One self-contained app living inside Ahshan's Home (the camera today; a light,
 * a thermostat, a sensor tomorrow). A module describes its tile on the home
 * screen and registers its own navigation graph; the shell knows nothing else
 * about it, so adding a module means writing one of these and listing it in
 * [ModuleRegistry].
 */
interface HomeModule {
    val id: String
    val title: String

    /** One short line under the title on the module's tile. */
    val description: String

    @get:DrawableRes
    val iconRes: Int

    /** Route of the module's nested graph; the home screen navigates here to open it. */
    val entryRoute: String

    /** Adds this module's screens to the app's navigation graph. */
    fun register(builder: NavGraphBuilder, navController: NavHostController)
}

object ModuleRegistry {
    val modules: List<HomeModule> = listOf(
        MijiaCameraModule,
        PrinterModule,
        LedModule,
    )
}

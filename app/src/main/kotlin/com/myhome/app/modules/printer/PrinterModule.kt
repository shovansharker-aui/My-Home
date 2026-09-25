package com.myhome.app.modules.printer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.TextFields
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.myhome.app.R
import com.myhome.app.home.HomeModule
import com.myhome.app.modules.printer.ui.CodeTool
import com.myhome.app.modules.printer.ui.LabelTool
import com.myhome.app.modules.printer.ui.NoteTool
import com.myhome.app.modules.printer.ui.PhotoTool
import com.myhome.app.modules.printer.ui.PrinterHomeScreen
import com.myhome.app.modules.printer.ui.ScanScreen
import com.myhome.app.modules.printer.ui.TextTool
import com.myhome.app.modules.printer.ui.ToolEntry

/** The MX05 mini thermal printer: text, photos, notes and lists, QR codes and barcodes, and fixed-size labels. */
object PrinterModule : HomeModule {
    override val id = "printer"
    override val title = "Mini Printer"
    override val description = "MX05 thermal printer"
    override val iconRes = R.drawable.ic_module_printer
    override val entryRoute = Routes.ROOT

    object Routes {
        const val ROOT = "printer"
        const val HOME = "printer/home"
        const val SCAN = "printer/scan"
        const val TEXT = "printer/text"
        const val PHOTO = "printer/photo"
        const val NOTE = "printer/note"
        const val CODE = "printer/code"
        const val LABEL = "printer/label"
    }

    private val tools = listOf(
        ToolEntry("Text", "Print any message", Icons.Filled.TextFields, Routes.TEXT),
        ToolEntry("Photo", "From your gallery", Icons.Filled.Image, Routes.PHOTO),
        ToolEntry("Note or list", "Checklists and notes", Icons.Filled.Checklist, Routes.NOTE),
        ToolEntry("QR & barcode", "Links, codes, tags", Icons.Filled.QrCode2, Routes.CODE),
        ToolEntry("Label", "Fixed-size labels", Icons.Filled.Label, Routes.LABEL),
    )

    override fun register(builder: NavGraphBuilder, navController: NavHostController) {
        val back: () -> Unit = { navController.popBackStack() }
        val scan: () -> Unit = { navController.navigate(Routes.SCAN) }
        builder.navigation(startDestination = Routes.HOME, route = Routes.ROOT) {
            composable(Routes.HOME) {
                PrinterHomeScreen(
                    onBack = back,
                    tools = tools,
                    onOpenTool = { navController.navigate(it) },
                    onOpenScan = scan,
                )
            }
            composable(Routes.SCAN) { ScanScreen(onBack = back, onConnected = back) }
            composable(Routes.TEXT) { TextTool(onBack = back, onOpenScan = scan) }
            composable(Routes.PHOTO) { PhotoTool(onBack = back, onOpenScan = scan) }
            composable(Routes.NOTE) { NoteTool(onBack = back, onOpenScan = scan) }
            composable(Routes.CODE) { CodeTool(onBack = back, onOpenScan = scan) }
            composable(Routes.LABEL) { LabelTool(onBack = back, onOpenScan = scan) }
        }
    }
}

package com.myhome.app.modules.printer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.myhome.app.modules.printer.render.PrintRenderer
import com.myhome.app.modules.printer.render.PrintRenderer.Align
import com.myhome.app.modules.printer.render.PrintRenderer.ListStyle
import com.myhome.app.modules.printer.render.PrintRenderer.TextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ---- notes and lists ----------------------------------------------------------

@Composable
fun NoteTool(onBack: () -> Unit, onOpenScan: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var items by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf(ListStyle.CHECKS) }
    var date by rememberSaveable { mutableStateOf(true) }
    val lines = remember(items) { items.lines() }
    val bitmap = remember(title, lines, style, date) {
        if (title.isBlank() && lines.all { it.isBlank() }) null else PrintRenderer.note(title, lines, style, date)
    }

    PrintTool("Note or list", onBack, onOpenScan, bitmap, "Add a title and some items") {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        OutlinedTextField(
            value = items,
            onValueChange = { items = it },
            label = { Text("Items — one per line") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        ChoiceRow("Style", ListStyle.entries, style, { it.label }) { style = it }
        SwitchRow("Add date and time", date) { date = it }
    }
}

// ---- QR and barcodes ----------------------------------------------------------

private enum class CodeKind(val label: String) { QR("QR code"), BARCODE("Barcode") }
private enum class QrSize(val label: String, val px: Int) { SMALL("Small", 200), MEDIUM("Medium", 290), LARGE("Large", 350) }

@Composable
fun CodeTool(onBack: () -> Unit, onOpenScan: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(CodeKind.QR) }
    var content by rememberSaveable { mutableStateOf("") }
    var caption by rememberSaveable { mutableStateOf("") }
    var qrSize by rememberSaveable { mutableStateOf(QrSize.MEDIUM) }
    var showText by rememberSaveable { mutableStateOf(true) }
    val bitmap = remember(kind, content, caption, qrSize, showText) {
        when {
            content.isBlank() -> null
            kind == CodeKind.QR -> PrintRenderer.qr(content, caption, qrSize.px)
            else -> PrintRenderer.barcode(content.trim(), showText)
        }
    }
    val hint = when {
        content.isBlank() -> "Type the text or link to encode"
        kind == CodeKind.BARCODE -> "This can't be made into a barcode — use letters, digits and common symbols"
        else -> "This can't be encoded"
    }

    PrintTool("QR code and barcode", onBack, onOpenScan, bitmap, hint) {
        ChoiceRow("Type", CodeKind.entries, kind, { it.label }) { kind = it }
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text(if (kind == CodeKind.QR) "Text or link" else "Barcode text") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (kind == CodeKind.QR) {
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption under the code (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceRow("Size", QrSize.entries, qrSize, { it.label }) { qrSize = it }
        } else {
            SwitchRow("Show the text under the bars", showText) { showText = it }
        }
    }
}

// ---- labels -------------------------------------------------------------------

private data class LabelSize(val w: Int, val h: Int) {
    val text get() = "$w×$h mm"
}

private val LABEL_SIZES = listOf(LabelSize(30, 20), LabelSize(40, 30), LabelSize(48, 30), LabelSize(48, 40))

@Composable
fun LabelTool(onBack: () -> Unit, onOpenScan: () -> Unit) {
    var sizeIndex by rememberSaveable { mutableIntStateOf(1) }
    var title by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var qr by rememberSaveable { mutableStateOf("") }
    var border by rememberSaveable { mutableStateOf(true) }
    var textSize by rememberSaveable { mutableStateOf(TextSize.LARGE) }
    val size = LABEL_SIZES[sizeIndex]
    val bitmap = remember(size, title, subtitle, qr, border, textSize) {
        if (title.isBlank() && subtitle.isBlank() && qr.isBlank()) null
        else PrintRenderer.label(PrintRenderer.LabelSpec(size.w, size.h, title, subtitle, qr, border, textSize))
    }

    PrintTool("Label", onBack, onOpenScan, bitmap, "Fill in a title, a second line, or a QR code") {
        ChoiceRow("Label size", LABEL_SIZES, size, { it.text }) { sizeIndex = LABEL_SIZES.indexOf(it) }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        OutlinedTextField(
            value = subtitle,
            onValueChange = { subtitle = it },
            label = { Text("Second line (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = qr,
            onValueChange = { qr = it },
            label = { Text("QR code text (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceRow("Text size", TextSize.entries, textSize, { it.label }) { textSize = it }
        SwitchRow("Border", border) { border = it }
    }
}

// ---- photos -------------------------------------------------------------------

@Composable
fun PhotoTool(onBack: () -> Unit, onOpenScan: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var quarterTurns by rememberSaveable { mutableIntStateOf(0) }
    var brightness by rememberSaveable { mutableFloatStateOf(0f) }
    var contrast by rememberSaveable { mutableFloatStateOf(1.2f) }
    var dither by rememberSaveable { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { source = withContext(Dispatchers.IO) { decodeScaled(context, uri) } }
    }

    val bitmap by produceState<Bitmap?>(null, source, quarterTurns, brightness, contrast, dither) {
        val src = source
        value = if (src == null) null else withContext(Dispatchers.Default) {
            val turned = if (quarterTurns % 4 == 0) src else {
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f * (quarterTurns % 4)) }, true)
            }
            PrintRenderer.photo(turned, brightness, contrast, dither)
        }
    }

    PrintTool("Print a photo", onBack, onOpenScan, bitmap, "Choose a picture from your gallery") {
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (source == null) "Choose from gallery" else "Choose another") }
        if (source != null) {
            OutlinedButton(onClick = { quarterTurns = (quarterTurns + 1) % 4 }, modifier = Modifier.fillMaxWidth()) { Text("Rotate") }
            Text("Brightness", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -0.3f..0.3f)
            Text("Contrast", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.6f..2.2f)
            SwitchRow("Dither (smoother shades)", dither) { dither = it }
        }
    }
}

/** Reads a picture at a size the printer can use, applying the camera's rotation tag. */
internal fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val bmp = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
}.getOrNull()

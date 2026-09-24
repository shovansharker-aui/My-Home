package com.mijia4k.app.modules.printer.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.mijia4k.app.home.ShareInbox
import com.mijia4k.app.modules.printer.render.DocumentRenderer
import com.mijia4k.app.modules.printer.render.DocumentRenderer.Border
import com.mijia4k.app.modules.printer.render.DocumentRenderer.Bullets
import com.mijia4k.app.modules.printer.render.DocumentRenderer.Font
import com.mijia4k.app.modules.printer.render.DocumentRenderer.PicturePlace
import com.mijia4k.app.modules.printer.render.DocumentRenderer.PictureSize
import com.mijia4k.app.modules.printer.render.PrintRenderer.Align
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Text page: type or paste (or receive from Google Keep's "Send"), then
 * choose a font and size, styling, alignment, list markers, a border and an
 * optional picture. The preview is the paper.
 */
@Composable
fun TextTool(onBack: () -> Unit, onOpenScan: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var font by rememberSaveable { mutableStateOf(Font.SANS) }
    var size by rememberSaveable { mutableFloatStateOf(32f) }
    var bold by rememberSaveable { mutableStateOf(false) }
    var italic by rememberSaveable { mutableStateOf(false) }
    var underline by rememberSaveable { mutableStateOf(false) }
    var align by rememberSaveable { mutableStateOf(Align.LEFT) }
    var bullets by rememberSaveable { mutableStateOf(Bullets.NONE) }
    var border by rememberSaveable { mutableStateOf(Border.NONE) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var picturePlace by rememberSaveable { mutableStateOf(PicturePlace.ABOVE) }
    var pictureSize by rememberSaveable { mutableStateOf(PictureSize.FULL) }
    var dither by rememberSaveable { mutableStateOf(true) }

    // Text or a picture shared in from another app.
    val incoming by ShareInbox.pending.collectAsState()
    LaunchedEffect(incoming?.id) {
        val item = incoming ?: return@LaunchedEffect
        item.text?.let { text = it }
        item.image?.let { uri -> picture = withContext(Dispatchers.IO) { decodeScaled(context, uri) } }
        ShareInbox.clear()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { picture = withContext(Dispatchers.IO) { decodeScaled(context, uri) } }
    }

    val spec = DocumentRenderer.Spec(
        text = text, font = font, sizePx = size, bold = bold, italic = italic, underline = underline,
        align = align, bullets = bullets, border = border, picture = picture,
        picturePlace = picturePlace, pictureSize = pictureSize, dither = dither,
    )
    val bitmap by produceState<Bitmap?>(null, spec) {
        value = if (spec.text.isBlank() && spec.picture == null) null
        else withContext(Dispatchers.Default) { DocumentRenderer.render(spec) }
    }

    PrintTool("Print text", onBack, onOpenScan, bitmap, "Type something, or add a picture") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Text") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        Section("Picture")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (picture == null) "Add a picture" else "Change picture")
            }
            if (picture != null) OutlinedButton(onClick = { picture = null }) { Text("Remove") }
        }
        if (picture != null) {
            ChoiceRow("Position", PicturePlace.entries, picturePlace, { it.label }) { picturePlace = it }
            ChoiceRow("Picture size", PictureSize.entries, pictureSize, { it.label }) { pictureSize = it }
            SwitchLine("Dither (smoother shades)", dither) { dither = it }
        }

        Section("Font")
        ChoiceRow("Typeface", Font.entries, font, { it.label }) { font = it }
        Text("Size  ${size.toInt()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = size, onValueChange = { size = it }, valueRange = 16f..90f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Bold") })
            FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("Italic") })
            FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text("Underline") })
        }

        Section("Layout")
        ChoiceRow("Alignment", Align.entries, align, { it.label }) { align = it }
        ChoiceRow("List markers (one per line)", Bullets.entries, bullets, { it.label }) { bullets = it }
        ChoiceRow("Border", Border.entries, border, { it.label }) { border = it }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

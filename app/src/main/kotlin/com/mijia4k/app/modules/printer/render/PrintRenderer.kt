package com.mijia4k.app.modules.printer.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mijia4k.app.modules.printer.protocol.CatProtocol
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns content into the exact black-and-white bitmap that gets printed, 384
 * dots wide. What the preview shows is what the paper gets: every function
 * ends by snapping to pure black and white.
 */
object PrintRenderer {
    const val W = CatProtocol.WIDTH_DOTS
    private const val PAD = 12

    enum class TextSize(val label: String, val px: Float) {
        SMALL("S", 22f), MEDIUM("M", 30f), LARGE("L", 44f), XL("XL", 64f)
    }

    enum class Align(val label: String, val layout: Layout.Alignment) {
        LEFT("Left", Layout.Alignment.ALIGN_NORMAL),
        CENTER("Center", Layout.Alignment.ALIGN_CENTER),
        RIGHT("Right", Layout.Alignment.ALIGN_OPPOSITE),
    }

    enum class ListStyle(val label: String) { BULLETS("Bullets"), CHECKS("Checkboxes"), NUMBERS("Numbers") }

    // ---- notes and lists --------------------------------------------------

    fun note(title: String, items: List<String>, style: ListStyle, showDate: Boolean): Bitmap {
        val titlePaint = paint(36f, true)
        val bodyPaint = paint(28f, false)
        val smallPaint = paint(20f, false)
        val indent = 44
        val cleanItems = items.map { it.trim() }.filter { it.isNotEmpty() }

        val titleLayout = if (title.isNotBlank()) layout(title.trim(), titlePaint, W - 2 * PAD, Layout.Alignment.ALIGN_NORMAL) else null
        val itemLayouts = cleanItems.map { layout(it, bodyPaint, W - 2 * PAD - indent, Layout.Alignment.ALIGN_NORMAL) }
        val date = if (showDate) SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date()) else null

        var height = 2 * PAD
        titleLayout?.let { height += it.height + 22 }
        itemLayouts.forEach { height += maxOf(it.height, 30) + 10 }
        if (date != null) height += 34
        val bmp = blank(height)
        val canvas = Canvas(bmp)
        var y = PAD.toFloat()

        if (titleLayout != null) {
            canvas.save(); canvas.translate(PAD.toFloat(), y); titleLayout.draw(canvas); canvas.restore()
            y += titleLayout.height + 8
            canvas.drawRect(PAD.toFloat(), y, (W - PAD).toFloat(), y + 3f, fill())
            y += 14
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 3f; this.style = Paint.Style.STROKE }
        itemLayouts.forEachIndexed { index, l ->
            val rowH = maxOf(l.height, 30)
            val cy = y + 15f
            when (style) {
                ListStyle.BULLETS -> canvas.drawCircle(PAD + 10f, cy, 5f, fill())
                ListStyle.CHECKS -> canvas.drawRoundRect(RectF(PAD.toFloat(), cy - 12f, PAD + 24f, cy + 12f), 4f, 4f, line)
                ListStyle.NUMBERS -> canvas.drawText("${index + 1}.", PAD.toFloat(), y + 24f, bodyPaint)
            }
            canvas.save(); canvas.translate((PAD + indent).toFloat(), y); l.draw(canvas); canvas.restore()
            y += rowH + 10
        }
        if (date != null) canvas.drawText(date, PAD.toFloat(), y + 22f, smallPaint)
        return binarize(bmp)
    }

    // ---- QR and barcodes --------------------------------------------------

    fun qr(content: String, caption: String, sizePx: Int = 300): Bitmap? {
        if (content.isBlank()) return null
        val matrix = runCatching {
            MultiFormatWriter().encode(
                content, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(
                    EncodeHintType.MARGIN to 0,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        }.getOrNull() ?: return null
        val module = maxOf(1, sizePx / matrix.width)
        val qrSize = matrix.width * module
        val captionLayout = caption.takeIf { it.isNotBlank() }?.let { layout(it.trim(), paint(24f, false), W - 2 * PAD, Layout.Alignment.ALIGN_CENTER) }
        val bmp = blank(PAD + qrSize + PAD + (captionLayout?.height?.plus(PAD) ?: 0))
        val canvas = Canvas(bmp)
        drawMatrix(canvas, matrix, (W - qrSize) / 2f, PAD.toFloat(), module.toFloat(), module.toFloat())
        captionLayout?.let {
            canvas.save(); canvas.translate(PAD.toFloat(), (PAD + qrSize + PAD).toFloat()); it.draw(canvas); canvas.restore()
        }
        return binarize(bmp)
    }

    /** Code 128, which takes letters, digits and common symbols; null if the text can't be encoded. */
    fun barcode(content: String, showText: Boolean): Bitmap? {
        if (content.isBlank()) return null
        val width = W - 2 * PAD
        val matrix = runCatching {
            MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, width, 110, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull() ?: return null
        val textPaint = paint(24f, false).apply { textAlign = Paint.Align.CENTER }
        val bmp = blank(PAD + 110 + (if (showText) 40 else 0) + PAD)
        val canvas = Canvas(bmp)
        drawMatrix(canvas, matrix, PAD.toFloat(), PAD.toFloat(), 1f, 1f)
        if (showText) canvas.drawText(content, W / 2f, (PAD + 110 + 30).toFloat(), textPaint)
        return binarize(bmp)
    }

    private fun drawMatrix(canvas: Canvas, m: BitMatrix, left: Float, top: Float, sx: Float, sy: Float) {
        val p = fill()
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) canvas.drawRect(left + x * sx, top + y * sy, left + (x + 1) * sx, top + (y + 1) * sy, p)
        }
    }

    // ---- photos -----------------------------------------------------------

    /** Scales to the print width and turns a picture into black and white, either plain or dithered. */
    fun photo(source: Bitmap, brightness: Float, contrast: Float, dither: Boolean, targetWidth: Int = W): Bitmap {
        val tw = targetWidth.coerceIn(8, W)
        val h = maxOf(1, (source.height * tw.toFloat() / source.width).toInt())
        val scaled = Bitmap.createScaledBitmap(source, tw, h, true)
        val px = IntArray(tw * h)
        scaled.getPixels(px, 0, tw, 0, 0, tw, h)
        val gray = FloatArray(tw * h) { i ->
            val p = px[i]
            val alpha = (p ushr 24) / 255f
            val lum = 0.299f * (p shr 16 and 0xFF) + 0.587f * (p shr 8 and 0xFF) + 0.114f * (p and 0xFF)
            val onWhite = lum * alpha + 255f * (1 - alpha)
            ((onWhite - 128f) * contrast + 128f + brightness * 255f).coerceIn(0f, 255f)
        }
        val out = IntArray(tw * h)
        if (dither) {
            // Floyd–Steinberg: push each pixel's rounding error onto its neighbours.
            for (y in 0 until h) for (x in 0 until tw) {
                val i = y * tw + x
                val old = gray[i]
                val new = if (old < 128f) 0f else 255f
                out[i] = if (new == 0f) Color.BLACK else Color.WHITE
                val err = old - new
                if (x + 1 < tw) gray[i + 1] += err * 7f / 16f
                if (y + 1 < h) {
                    if (x > 0) gray[i + tw - 1] += err * 3f / 16f
                    gray[i + tw] += err * 5f / 16f
                    if (x + 1 < tw) gray[i + tw + 1] += err * 1f / 16f
                }
            }
        } else {
            for (i in gray.indices) out[i] = if (gray[i] < 128f) Color.BLACK else Color.WHITE
        }
        val bmp = Bitmap.createBitmap(tw, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, tw, 0, 0, tw, h)
        return bmp
    }

    // ---- labels -----------------------------------------------------------

    data class LabelSpec(
        val widthMm: Int,
        val heightMm: Int,
        val title: String,
        val subtitle: String,
        val qr: String,
        val border: Boolean,
        val size: TextSize,
    )

    /** A fixed-size label centred on the head: optional border, a QR on the left, text shrunk to fit. */
    fun label(spec: LabelSpec): Bitmap {
        val labelW = minOf(W, spec.widthMm * CatProtocol.DOTS_PER_MM)
        val labelH = spec.heightMm * CatProtocol.DOTS_PER_MM
        val bmp = blank(labelH)
        val canvas = Canvas(bmp)
        val left = (W - labelW) / 2f
        val inset = 10f
        if (spec.border) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }
            canvas.drawRoundRect(RectF(left + 2f, 2f, left + labelW - 2f, labelH - 2f), 14f, 14f, stroke)
        }
        var textLeft = left + inset + 6f
        var textWidth = labelW - 2 * (inset + 6f)
        val qrMatrix = spec.qr.takeIf { it.isNotBlank() }?.let {
            runCatching {
                MultiFormatWriter().encode(it, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8"))
            }.getOrNull()
        }
        if (qrMatrix != null) {
            val avail = (labelH - 2 * (inset + 4f)).toInt()
            val module = maxOf(1, minOf(avail, (labelW * 0.45f).toInt()) / qrMatrix.width)
            val qrSize = module * qrMatrix.width
            drawMatrix(canvas, qrMatrix, left + inset + 6f, (labelH - qrSize) / 2f, module.toFloat(), module.toFloat())
            textLeft = left + inset + 6f + qrSize + 14f
            textWidth = labelW - (textLeft - left) - (inset + 6f)
        }
        val boxH = labelH - 2 * (inset + 4f)
        val parts = listOf(spec.title to true, spec.subtitle to false).filter { it.first.isNotBlank() }
        if (parts.isNotEmpty() && textWidth > 20) {
            var px = spec.size.px
            var layouts: List<StaticLayout>
            while (true) {
                layouts = parts.mapIndexed { i, (t, bold) ->
                    layout(t.trim(), paint(if (i == 0) px else px * 0.7f, bold), textWidth.toInt(), Layout.Alignment.ALIGN_NORMAL)
                }
                if (layouts.sumOf { it.height } + 6 * (layouts.size - 1) <= boxH || px <= 12f) break
                px -= 2f
            }
            val total = layouts.sumOf { it.height } + 6 * (layouts.size - 1)
            var y = (labelH - total) / 2f
            layouts.forEach { l ->
                canvas.save(); canvas.translate(textLeft, y); l.draw(canvas); canvas.restore()
                y += l.height + 6
            }
        }
        return binarize(bmp)
    }

    // ---- helpers ----------------------------------------------------------

    private fun paint(px: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = px
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fill() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }

    private fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)
            .build()

    private fun blank(height: Int): Bitmap =
        Bitmap.createBitmap(W, maxOf(1, height), Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }

    /** Snaps every pixel to pure black or white so the preview matches the paper. */
    private fun binarize(src: Bitmap, threshold: Int = 165): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            px[i] = if (lum < threshold) Color.BLACK else Color.WHITE
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}

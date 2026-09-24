package com.mijia4k.app.modules.printer.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mijia4k.app.modules.printer.render.PrintRenderer.Align
import com.mijia4k.app.modules.printer.render.PrintRenderer.W

/**
 * The general-purpose page behind the Text tool: styled text (font, size,
 * bold, italic, underline, alignment), optional bullets, one picture above or
 * below, and a border round the whole thing. Snaps to pure black and white
 * like everything else so the preview matches the paper.
 */
object DocumentRenderer {
    enum class Font(val label: String, val family: String) {
        SANS("Sans", "sans-serif"),
        SERIF("Serif", "serif"),
        MONO("Mono", "monospace"),
        SCRIPT("Script", "cursive"),
        NARROW("Narrow", "sans-serif-condensed"),
        CASUAL("Casual", "casual"),
    }

    enum class Bullets(val label: String) {
        NONE("None"), DOTS("Bullets"), DASHES("Dashes"), NUMBERS("Numbers"), CHECKS("Checkboxes")
    }

    enum class Border(val label: String) {
        NONE("None"), THIN("Thin"), THICK("Thick"), DOUBLE("Double"),
        ROUNDED("Rounded"), DASHED("Dashed"), DOTTED("Dotted"), CORNERS("Corners")
    }

    enum class PicturePlace(val label: String) { ABOVE("Above text"), BELOW("Below text") }
    enum class PictureSize(val label: String, val fraction: Float) {
        FULL("Full width", 1f), TWO_THIRDS("Two thirds", 0.66f), HALF("Half", 0.5f)
    }

    data class Spec(
        val text: String,
        val font: Font = Font.SANS,
        val sizePx: Float = 32f,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val align: Align = Align.LEFT,
        val bullets: Bullets = Bullets.NONE,
        val border: Border = Border.NONE,
        val picture: Bitmap? = null,
        val picturePlace: PicturePlace = PicturePlace.ABOVE,
        val pictureSize: PictureSize = PictureSize.FULL,
        val dither: Boolean = true,
    )

    private class Block(val height: Int, val draw: (Canvas, Float) -> Unit)

    fun render(s: Spec): Bitmap {
        val margin = 10 + if (s.border == Border.NONE) 0 else 18
        val contentW = W - 2 * margin
        val gap = 12

        val style = when {
            s.bold && s.italic -> Typeface.BOLD_ITALIC
            s.bold -> Typeface.BOLD
            s.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = s.sizePx
            typeface = Typeface.create(s.font.family, style)
            isUnderlineText = s.underline
        }

        val picture = s.picture?.let {
            PrintRenderer.photo(it, 0f, 1.2f, s.dither, (contentW * s.pictureSize.fraction).toInt())
        }
        val pictureBlock = picture?.let { pic ->
            Block(pic.height) { c, y -> c.drawBitmap(pic, margin + (contentW - pic.width) / 2f, y, null) }
        }

        val text = s.text.trimEnd()
        val textBlocks = if (text.isBlank()) emptyList() else if (s.bullets == Bullets.NONE) {
            listOf(layoutBlock(text, paint, contentW, s.align.layout, margin))
        } else {
            bulletBlocks(text, paint, contentW, s, margin)
        }

        val blocks = buildList {
            if (pictureBlock != null && s.picturePlace == PicturePlace.ABOVE) add(pictureBlock)
            addAll(textBlocks)
            if (pictureBlock != null && s.picturePlace == PicturePlace.BELOW) add(pictureBlock)
        }

        val height = margin * 2 + blocks.sumOf { it.height } + gap * maxOf(0, blocks.size - 1) + if (blocks.isEmpty()) 60 else 0
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
        val canvas = Canvas(bmp)
        var y = margin.toFloat()
        for (b in blocks) {
            b.draw(canvas, y)
            y += b.height + gap
        }
        drawBorder(canvas, W, height, s.border)
        return binarize(bmp)
    }

    private fun layoutBlock(text: String, paint: TextPaint, width: Int, align: Layout.Alignment, left: Int): Block {
        val l = layout(text, paint, width, align)
        return Block(l.height) { c, y -> c.save(); c.translate(left.toFloat(), y); l.draw(c); c.restore() }
    }

    /** One block per non-blank line, each with its marker; a blank line becomes extra space. */
    private fun bulletBlocks(text: String, paint: TextPaint, contentW: Int, s: Spec, margin: Int): List<Block> {
        val indent = (s.sizePx * 1.3f).toInt().coerceAtLeast(34)
        val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, s.sizePx / 12f)
        }
        val filled = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val blocks = ArrayList<Block>()
        var number = 0
        for (line in text.lines()) {
            if (line.isBlank()) {
                blocks.add(Block((s.sizePx * 0.6f).toInt()) { _, _ -> })
                continue
            }
            number++
            val n = number
            val l = layout(line.trim(), paint, contentW - indent, s.align.layout)
            blocks.add(
                Block(l.height) { c, y ->
                    val cy = y + (l.getLineTop(0) + l.getLineBottom(0)) / 2f
                    val left = margin.toFloat()
                    when (s.bullets) {
                        Bullets.DOTS -> c.drawCircle(left + indent * 0.3f, cy, s.sizePx * 0.13f, filled)
                        Bullets.DASHES -> c.drawLine(left + indent * 0.1f, cy, left + indent * 0.55f, cy, marker)
                        Bullets.CHECKS -> {
                            val box = s.sizePx * 0.7f
                            c.drawRoundRect(RectF(left, cy - box / 2, left + box, cy + box / 2), 4f, 4f, marker)
                        }
                        Bullets.NUMBERS -> c.drawText("$n.", left, y + l.getLineBaseline(0), paint)
                        Bullets.NONE -> Unit
                    }
                    c.save(); c.translate(left + indent, y); l.draw(c); c.restore()
                },
            )
        }
        return blocks
    }

    private fun drawBorder(c: Canvas, w: Int, h: Int, border: Border) {
        if (border == Border.NONE) return
        fun stroke(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = width
        }
        fun rect(inset: Float) = RectF(inset, inset, w - inset, h - inset)
        when (border) {
            Border.THIN -> c.drawRect(rect(6f), stroke(2f))
            Border.THICK -> c.drawRect(rect(8f), stroke(7f))
            Border.DOUBLE -> {
                c.drawRect(rect(5f), stroke(3f))
                c.drawRect(rect(14f), stroke(3f))
            }
            Border.ROUNDED -> c.drawRoundRect(rect(7f), 28f, 28f, stroke(4f))
            Border.DASHED -> c.drawRect(rect(8f), stroke(4f).apply { pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f) })
            Border.DOTTED -> c.drawRect(
                rect(8f),
                stroke(6f).apply {
                    strokeCap = Paint.Cap.ROUND
                    pathEffect = DashPathEffect(floatArrayOf(0.1f, 13f), 0f)
                },
            )
            Border.CORNERS -> {
                val p = stroke(6f).apply { strokeCap = Paint.Cap.SQUARE }
                val i = 9f
                val len = 46f
                for ((cx, dx) in listOf(i to 1f, (w - i) to -1f)) for ((cy, dy) in listOf(i to 1f, (h - i) to -1f)) {
                    c.drawLine(cx, cy, cx + dx * len, cy, p)
                    c.drawLine(cx, cy, cx, cy + dy * len, p)
                }
            }
            Border.NONE -> Unit
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .build()

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

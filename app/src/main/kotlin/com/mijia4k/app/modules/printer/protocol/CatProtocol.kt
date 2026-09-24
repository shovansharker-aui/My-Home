package com.mijia4k.app.modules.printer.protocol

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * The frame protocol spoken by the MX05 and its "cat printer" relatives, worked
 * out from a captured print and checked against it: every message is
 * `51 78 · command · 00 · length(2, LE) · data · CRC-8 · ff`, the CRC being the
 * plain polynomial-0x07 CRC-8 over the data bytes. A picture is sent as lines
 * of 48 bytes (384 dots), one bit per dot with the *leftmost* dot in the least
 * significant bit, 1 meaning "burn".
 */
object CatProtocol {
    const val WIDTH_DOTS = 384
    const val LINE_BYTES = WIDTH_DOTS / 8
    const val DOTS_PER_MM = 8

    const val CMD_FEED = 0xA1
    const val CMD_LINE = 0xA2
    const val CMD_STATUS = 0xA3
    const val CMD_QUALITY = 0xA4
    const val CMD_LATTICE = 0xA6
    const val CMD_INFO = 0xA8
    const val CMD_FLOW = 0xAE
    const val CMD_ENERGY = 0xAF
    const val CMD_SPEED = 0xBD
    const val CMD_MODE = 0xBE
    const val CMD_DENSITY = 0xF2

    private val LATTICE_BEGIN = hex("aa551738445f5f5f44382c")
    private val LATTICE_END = hex("aa55170000000000000017")

    private val CRC_TABLE = IntArray(256) { i ->
        var c = i
        repeat(8) { c = if (c and 0x80 != 0) (c shl 1) xor 0x07 else c shl 1 }
        c and 0xFF
    }

    fun crc8(data: ByteArray): Int {
        var crc = 0
        for (b in data) crc = CRC_TABLE[(crc xor (b.toInt() and 0xFF)) and 0xFF]
        return crc
    }

    fun frame(cmd: Int, data: ByteArray = byteArrayOf(0)): ByteArray {
        val out = ByteArray(8 + data.size)
        out[0] = 0x51
        out[1] = 0x78
        out[2] = cmd.toByte()
        out[3] = 0
        out[4] = (data.size and 0xFF).toByte()
        out[5] = (data.size shr 8).toByte()
        data.copyInto(out, 6)
        out[6 + data.size] = crc8(data).toByte()
        out[7 + data.size] = 0xFF.toByte()
        return out
    }

    fun infoRequest() = frame(CMD_INFO)
    fun statusRequest() = frame(CMD_STATUS)

    /** How dark the print is: what the stock app sends as `f2 01 <n>`, where 130 was its medium. */
    enum class Density(val label: String, val value: Int) {
        LIGHT("Light", 100),
        MEDIUM("Medium", 130),
        DARK("Dark", 170),
    }

    /**
     * Splits a black-and-white bitmap into the printer's lines: 48 bytes each,
     * leftmost dot in the least significant bit, black = 1. Anything narrower
     * than the head is centred; wider is cropped.
     */
    fun toLines(bitmap: Bitmap): List<ByteArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val xOffset = (WIDTH_DOTS - w) / 2
        val lines = ArrayList<ByteArray>(h)
        for (y in 0 until h) {
            val line = ByteArray(LINE_BYTES)
            for (x in 0 until w) {
                val dest = x + xOffset
                if (dest !in 0 until WIDTH_DOTS) continue
                val p = pixels[y * w + x]
                val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                val alpha = p ushr 24
                if (alpha > 127 && lum < 128) {
                    line[dest shr 3] = (line[dest shr 3].toInt() or (1 shl (dest and 7))).toByte()
                }
            }
            lines.add(line)
        }
        return lines
    }

    /**
     * One complete print job as a single byte stream (the sender chops it into
     * BLE writes): settings, the picture, a short feed so the text clears the
     * tear bar, and a status poll. Same order the stock app used.
     */
    fun printJob(lines: List<ByteArray>, density: Density, feedDots: Int = 48): ByteArray {
        val out = ByteArrayOutputStream(lines.size * (LINE_BYTES + 8) + 128)
        out.write(frame(CMD_DENSITY, byteArrayOf(0x01, density.value.toByte())))
        out.write(frame(CMD_QUALITY, byteArrayOf(0x34)))
        out.write(frame(CMD_LATTICE, LATTICE_BEGIN))
        out.write(frame(CMD_ENERGY, byteArrayOf(0x98.toByte(), 0x3A)))
        out.write(frame(CMD_MODE, byteArrayOf(0x00)))
        out.write(frame(CMD_SPEED, byteArrayOf(0x0A)))
        out.write(frame(CMD_SPEED, byteArrayOf(0x0A)))
        for (line in lines) out.write(frame(CMD_LINE, line))
        out.write(frame(CMD_FEED, byteArrayOf((feedDots and 0xFF).toByte(), (feedDots shr 8).toByte())))
        out.write(frame(CMD_LATTICE, LATTICE_END))
        out.write(statusRequest())
        return out.toByteArray()
    }

    /** What the printer's status reply (`a3`) says, from the first data byte. */
    data class Status(val raw: Int) {
        val paperOut get() = raw and 0x01 != 0
        val overheated get() = raw and 0x04 != 0
        val lowBattery get() = raw and 0x08 != 0
        val busy get() = raw and 0x80 != 0
        val ok get() = !paperOut && !overheated && !lowBattery
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

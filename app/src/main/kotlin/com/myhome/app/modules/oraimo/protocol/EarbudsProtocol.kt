package com.myhome.app.modules.oraimo.protocol

import java.util.UUID

/**
 * The protocol of oraimo FreePods (a Jieli chip), worked out from a Bluetooth
 * capture of the stock app. It runs over a classic Bluetooth serial (RFCOMM)
 * channel found through the service UUID below. Every message is
 * `seq · group · command · length(2, big-endian) · payload`, where `seq` counts
 * 0..15 separately in each direction, and the payload is mostly `tag · length · value` items.
 */
object EarbudsProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    const val GROUP_SET = 0x20
    const val GROUP_GAME = 0x25
    const val GROUP_GET = 0x27
    const val GROUP_NOTIFY = 0x28

    const val CMD_REQUEST = 0x01
    const val CMD_REPLY = 0x02
    const val CMD_NOTIFY = 0x03

    const val TAG_BATTERY = 0x01
    const val TAG_NAME = 0x03
    const val TAG_EQ = 0x04
    const val TAG_GAME_MODE = 0x08

    fun frame(seq: Int, group: Int, cmd: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(5 + payload.size)
        out[0] = (seq and 0x0F).toByte()
        out[1] = group.toByte()
        out[2] = cmd.toByte()
        out[3] = (payload.size shr 8).toByte()
        out[4] = payload.size.toByte()
        payload.copyInto(out, 5)
        return out
    }

    /** The two requests the stock app opens with: a hello, then "tell me everything". */
    fun helloPayload() = byteArrayOf(0xFF.toByte(), 0)

    fun infoPayload(): ByteArray {
        val tags = (0x01..0x18).toList() + 0xFE
        return ByteArray(tags.size * 2) { if (it % 2 == 0) tags[it / 2].toByte() else 0 }
    }

    fun gamePayload(on: Boolean) = byteArrayOf(if (on) 1 else 0)

    /** Ten equaliser band gains (signed) for each preset, as the stock app sends them. */
    private val EQ_GAINS = listOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(-3, -2, 4, 1, 2, 1, 3, 0, 0, 0),
        intArrayOf(10, 6, 4, -1, -2, -3, 1, 0, 0, 0),
        intArrayOf(3, 2, -3, 3, -1, 3, 2, 5, 0, 0),
        intArrayOf(-6, -3, -1, 1, 1, 4, 6, 0, 0, 0),
        intArrayOf(-6, -3, 6, 4, 3, -5, -6, 0, 0, 0),
    )

    val EQ_COUNT get() = EQ_GAINS.size

    fun eqPayload(preset: Int): ByteArray {
        val g = EQ_GAINS[preset.coerceIn(0, EQ_GAINS.lastIndex)]
        return ByteArray(2 + g.size) {
            when (it) {
                0 -> 0x0A
                1 -> preset.toByte()
                else -> g[it - 2].toByte()
            }
        }
    }

    /** The stock app's "Create" slot is preset 1; [bands] are seven gains (dB, -10..10) for 50 Hz .. 16 kHz. */
    const val CUSTOM_PRESET = 1

    fun customEqPayload(bands: List<Int>): ByteArray =
        ByteArray(12) { i -> when (i) { 0 -> 0x0A; 1 -> CUSTOM_PRESET.toByte(); in 2..8 -> (bands.getOrElse(i - 2) { 0 }.coerceIn(-10, 10)).toByte(); else -> 0 } }

    /** Splits a payload of `tag · length · value` items. */
    fun items(payload: ByteArray): Map<Int, ByteArray> {
        val map = LinkedHashMap<Int, ByteArray>()
        var i = 0
        while (i + 2 <= payload.size) {
            val tag = payload[i].toInt() and 0xFF
            val len = payload[i + 1].toInt() and 0xFF
            if (i + 2 + len > payload.size) break
            map[tag] = payload.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
        }
        return map
    }

    class Message(val seq: Int, val group: Int, val cmd: Int, val payload: ByteArray)

    /** Pulls whole messages off the front of [buffer], returning them and what is left over. */
    fun parse(buffer: ByteArray): Pair<List<Message>, ByteArray> {
        val out = ArrayList<Message>()
        var pos = 0
        while (buffer.size - pos >= 5) {
            val group = buffer[pos + 1].toInt() and 0xFF
            if (group !in intArrayOf(GROUP_SET, GROUP_GAME, GROUP_GET, GROUP_NOTIFY)) {
                pos++ // not the start of a message: skip a byte and look again
                continue
            }
            val len = ((buffer[pos + 3].toInt() and 0xFF) shl 8) or (buffer[pos + 4].toInt() and 0xFF)
            if (buffer.size - pos < 5 + len) break
            out.add(
                Message(
                    seq = buffer[pos].toInt() and 0x0F,
                    group = group,
                    cmd = buffer[pos + 2].toInt() and 0xFF,
                    payload = buffer.copyOfRange(pos + 5, pos + 5 + len),
                ),
            )
            pos += 5 + len
        }
        return out to buffer.copyOfRange(pos, buffer.size)
    }
}

package com.myhome.app.modules.led.protocol

/**
 * The command set of ELK-BLEDOM style LED controllers (what Lotus Lantern speaks),
 * confirmed from a Bluetooth capture of the stock app. Every command is nine
 * bytes, `7E … EF`, written without response to characteristic 0xFFF3.
 */
object BledomProtocol {

    private fun cmd(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    fun power(on: Boolean): ByteArray =
        if (on) cmd(0x7E, 0x04, 0x04, 0xF0, 0x00, 0x01, 0xFF, 0x00, 0xEF)
        else cmd(0x7E, 0x04, 0x04, 0x00, 0x00, 0x00, 0xFF, 0x00, 0xEF)

    fun color(r: Int, g: Int, b: Int): ByteArray =
        cmd(0x7E, 0x07, 0x05, 0x03, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), 0x10, 0xEF)

    /** [percent] 0..100. */
    fun brightness(percent: Int): ByteArray =
        cmd(0x7E, 0x04, 0x01, percent.coerceIn(0, 100), 0x01, 0xFF, 0xFF, 0x00, 0xEF)

    /** [id] is a built-in mode number. */
    fun effect(id: Int): ByteArray =
        cmd(0x7E, 0x05, 0x03, id.coerceIn(0x80, 0xFF), 0x03, 0xFF, 0xFF, 0x00, 0xEF)

    /** [percent] 0..100 (how fast an effect runs). */
    fun speed(percent: Int): ByteArray =
        cmd(0x7E, 0x04, 0x02, percent.coerceIn(0, 100), 0xFF, 0xFF, 0xFF, 0x00, 0xEF)
}

/** One entry of the stock app's style list: a built-in mode, or a plain colour (the app's "Static …" rows). */
class LedEffect(val name: String, val mode: Int = 0, val rgb: Int? = null)

/** The stock app's list in its own order: seven static colours, then the built-in modes 0x87..0x9C. */
val LED_EFFECTS: List<LedEffect> = listOf(
    LedEffect("Static Red", rgb = 0xFF0000), LedEffect("Static Blue", rgb = 0x0000FF),
    LedEffect("Static Green", rgb = 0x00FF00), LedEffect("Static Cyan", rgb = 0x00FFFF),
    LedEffect("Static Yellow", rgb = 0xFFFF00), LedEffect("Static Purple", rgb = 0xFF00FF),
    LedEffect("Static White", rgb = 0xFFFFFF),
    LedEffect("Three Color Jumping Change", 0x87), LedEffect("Seven Color Jumping Change", 0x88),
    LedEffect("Three Color Cross Fade", 0x89), LedEffect("Seven Color Cross Fade", 0x8A),
    LedEffect("Red Gradual Change", 0x8B), LedEffect("Green Gradual Change", 0x8C),
    LedEffect("Blue Gradual Change", 0x8D), LedEffect("Yellow Gradual Change", 0x8E),
    LedEffect("Cyan Gradual Change", 0x8F), LedEffect("Purple Gradual Change", 0x90),
    LedEffect("White Gradual Change", 0x91),
    LedEffect("Red Green Cross Fade", 0x92), LedEffect("Red Blue Cross Fade", 0x93),
    LedEffect("Green Blue Cross Fade", 0x94),
    LedEffect("Seven Color Strobe Flash", 0x95), LedEffect("Red Strobe Flash", 0x96),
    LedEffect("Green Strobe Flash", 0x97), LedEffect("Blue Strobe Flash", 0x98),
    LedEffect("Yellow Strobe Flash", 0x99), LedEffect("Cyan Strobe Flash", 0x9A),
    LedEffect("Purple Strobe Flash", 0x9B), LedEffect("White Strobe Flash", 0x9C),
)

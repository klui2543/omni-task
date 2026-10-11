package app.omnitask.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The calm palettes stay readable: the grey they are made of must not sink text below a comfortable contrast. */
class PaletteContrastTest {

    private fun luminance(c: Color): Double {
        val argb = c.toArgb()
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> if (la >= lb) la to lb else lb to la } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, p: Palette) {
        fun atLeast(min: Double, what: String, fg: Color, bg: Color) {
            val got = contrast(fg, bg)
            assertTrue("$name $what is $got, wants $min", got >= min)
        }
        for ((surface, bg) in listOf("card" to p.card, "page" to p.bg)) {
            atLeast(7.0, "text on $surface", p.text, bg)
            atLeast(7.0, "text2 on $surface", p.text2, bg)
            atLeast(4.5, "muted on $surface", p.muted, bg)
            // The floor for anything that must still be read.
            atLeast(4.0, "faint on $surface", p.faint, bg)
            atLeast(4.0, "red on $surface", p.red, bg)
            atLeast(4.0, "amber on $surface", p.amber, bg)
        }
        atLeast(4.0, "lime on card", p.lime, p.card)
        atLeast(4.5, "onAccent on accent", p.onAccent, p.accent)
        atLeast(4.5, "accentText on accentSoft", p.accentText, p.accentSoft)
    }

    @Test
    fun graphiteDarkIsReadable() = check("graphite dark", Palettes.graphiteDark)

    @Test
    fun graphiteLightIsReadable() = check("graphite light", Palettes.graphiteLight)

    @Test
    fun theCalmLookIsOnlyGraphite() {
        assertTrue(Palettes.graphiteDark.calm && Palettes.graphiteLight.calm)
        assertTrue(!Palettes.midnight.calm)
    }
}

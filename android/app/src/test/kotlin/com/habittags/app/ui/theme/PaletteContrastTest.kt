package com.habittags.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    private fun check(name: String, fg: Long, bg: Long, min: Double) {
        val ratio = contrast(fg, bg)
        assertTrue("$name: ${"%.2f".format(ratio)} < $min", ratio >= min)
    }

    @Test
    fun everyPairing() {
        check("ink on base", MiseColors.INK, MiseColors.BASE, 7.0)
        check("ink on base (dark)", MiseColors.INK_DARK, MiseColors.BASE_DARK, 7.0)
        check("soft ink on base", MiseColors.INK_SOFT, MiseColors.BASE, 4.5)
        check("soft ink on base (dark)", MiseColors.INK_SOFT_DARK, MiseColors.BASE_DARK, 4.5)
        check("accent on base", MiseColors.ACCENT, MiseColors.BASE, 4.5)
        check("accent on base (dark)", MiseColors.ACCENT_ON_DARK, MiseColors.BASE_DARK, 4.5)
        check("white on accent", MiseColors.WHITE, MiseColors.ACCENT, 4.5)
        for (c in SpaceColour.entries) {
            check("${c.id} text on field", c.onField, c.field, 4.5)
            check("ink on ${c.id} tint", MiseColors.INK, c.tint, 7.0)
            check("ink on ${c.id} tint (dark)", MiseColors.INK_DARK, c.tintDark, 7.0)
        }
    }
}

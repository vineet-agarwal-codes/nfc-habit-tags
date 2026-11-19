package com.habittags.app.ui.theme

import androidx.compose.ui.graphics.Color

/** ARGB values kept as Longs so the contrast test runs on the JVM without Compose. */
object MiseColors {
    const val BASE = 0xFFF5F5F2
    const val BASE_DARK = 0xFF151716
    const val SURFACE = 0xFFEBEBE6
    const val SURFACE_DARK = 0xFF202321
    const val INK = 0xFF141614
    const val INK_DARK = 0xFFEDEDE8
    const val INK_SOFT = 0xFF4A4F4B
    const val INK_SOFT_DARK = 0xFFB4B8B4
    /** Tomato, tuned so white text on it and it on the base both pass AA. */
    const val ACCENT = 0xFFC93A22
    /** Tomato for text and marks on the dark base. */
    const val ACCENT_ON_DARK = 0xFFFF7A5C
    const val WHITE = 0xFFFFFFFF
}

/**
 * The six space colours, assigned automatically in this order ([com.habittags.core.space.Spaces.COLOURS]).
 * [field] fills a running space; [tint] is the idle card on the light base, [tintDark] on the dark one.
 */
enum class SpaceColour(val id: String, val field: Long, val onField: Long, val tint: Long, val tintDark: Long) {
    Tomato("tomato", 0xFFC93A22, MiseColors.WHITE, 0xFFEFDBD5, 0xFF4B221A),
    DeepBlue("deep_blue", 0xFF23408E, MiseColors.WHITE, 0xFFD8DCE4, 0xFF19233A),
    Green("green", 0xFF1D6E41, MiseColors.WHITE, 0xFFD7E2D9, 0xFF173123),
    Ochre("ochre", 0xFFE2A93B, MiseColors.INK, 0xFFF2EAD8, 0xFF524321),
    Teal("teal", 0xFF0E6E72, MiseColors.WHITE, 0xFFD5E2E0, 0xFF133132),
    Plum("plum", 0xFF8A2C50, MiseColors.WHITE, 0xFFE6D9DB, 0xFF381D27),
    ;

    companion object {
        fun of(id: String?): SpaceColour = entries.firstOrNull { it.id == id } ?: Tomato
    }
}

fun Long.toColor(): Color = Color(this.toInt())

/** WCAG contrast ratio between two opaque ARGB colours. */
fun contrast(a: Long, b: Long): Double {
    fun channel(v: Long, shift: Int): Double {
        val c = ((v shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    fun lum(v: Long) = 0.2126 * channel(v, 16) + 0.7152 * channel(v, 8) + 0.0722 * channel(v, 0)
    val (la, lb) = lum(a) to lum(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

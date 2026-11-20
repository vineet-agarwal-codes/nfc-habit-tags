package com.habittags.core.tag

import kotlin.test.Test
import kotlin.test.assertEquals

class SecureNfcTest {
    private fun nfc(supported: Boolean, enabled: Boolean) = object : SecureNfcState {
        override val supported = supported
        override val enabled = enabled
    }

    @Test
    fun tipShowsOnlyWhileSecureNfcIsOn() {
        data class Case(val supported: Boolean, val enabled: Boolean, val place: TipPlace, val hasTag: Boolean, val dismissed: Boolean, val show: Boolean)
        val cases = listOf(
            Case(true, true, TipPlace.AddFlow, false, false, true),
            Case(true, true, TipPlace.AddFlow, true, true, true), // dismissing on Home doesn't hide it in the add flow
            Case(true, false, TipPlace.AddFlow, false, false, false),
            Case(false, true, TipPlace.AddFlow, false, false, false),
            Case(true, true, TipPlace.Home, true, false, true),
            Case(true, true, TipPlace.Home, false, false, false), // no tag written yet
            Case(true, true, TipPlace.Home, true, true, false), // dismissed
            Case(true, false, TipPlace.Home, true, false, false), // turned off: hides
        )
        for (c in cases) assertEquals(c.show, showSecureNfcTip(nfc(c.supported, c.enabled), c.place, c.hasTag, c.dismissed), "$c")
    }
}

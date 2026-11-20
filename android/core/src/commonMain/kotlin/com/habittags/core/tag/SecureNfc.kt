package com.habittags.core.tag

/** What the phone reports about Secure NFC ("Require device unlock for NFC"). */
interface SecureNfcState {
    val supported: Boolean
    val enabled: Boolean
}

enum class TipPlace { AddFlow, Home }

/**
 * The "Tags without unlocking" tip shows only while Secure NFC is supported and on. In the add flow it
 * shows every time; on Home only once a tag exists and until the user dismisses it.
 */
fun showSecureNfcTip(nfc: SecureNfcState, place: TipPlace, hasTag: Boolean, dismissedOnHome: Boolean): Boolean =
    nfc.supported && nfc.enabled && when (place) {
        TipPlace.AddFlow -> true
        TipPlace.Home -> hasTag && !dismissedOnHome
    }

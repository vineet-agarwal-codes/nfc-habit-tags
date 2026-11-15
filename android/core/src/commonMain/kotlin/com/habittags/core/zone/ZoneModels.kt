package com.habittags.core.zone

import kotlinx.serialization.Serializable

/** One access point from a Wi-Fi scan: BSSID, signal strength in dBm, frequency in MHz. */
@Serializable
data class WifiReading(val b: String, val r: Int, val f: Int)

/**
 * One aggregated reading of the phone's surroundings. [scanAgeMs] is how old the newest scan result
 * was when read; [scanFresh] is whether a new scan completed for this sample (Android throttles scans).
 */
@Serializable
data class ZoneSample(
    val wifi: List<WifiReading>,
    val scanAgeMs: Long? = null,
    val scanFresh: Boolean = true,
    val startScanReturned: Boolean? = null,
    val magMean: Float? = null,
    val magStd: Float? = null,
    val gravity: List<Float>? = null,
    val light: Float? = null,
    val charging: Boolean = false,
    val screenOn: Boolean = true,
)

const val MODE_RESTING = "resting"
const val MODE_POCKET = "pocket"

@Serializable
data class ZoneRecording(val id: String, val spot: String, val mode: String, val atMs: Long, val samples: List<ZoneSample>)

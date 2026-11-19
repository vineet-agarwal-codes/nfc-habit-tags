package com.habittags.core.zone

import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

enum class FeatureSet(val magnet: Boolean, val orientation: Boolean) {
    WIFI(false, false),
    WIFI_MAG(true, false),
    WIFI_MAG_ORIENT(true, true),
}

/** Reported when the sample is far from every recorded one. */
const val UNKNOWN_SPOT = "Unknown place"

/**
 * [scores] cover every candidate spot plus [UNKNOWN_SPOT] and sum to 1: each spot's share of the
 * neighbours' weight scaled by how well the nearest recording matches, the rest going to unknown.
 * [nearest] is the distance to the closest recorded sample.
 */
data class Guess(val spot: String, val confidence: Double, val scores: Map<String, Double>, val nearest: Double = 0.0)

/**
 * Nearest recorded sample per spot. Keep in step with backend/analysis/zone_lab.py, which replays the
 * same classifier on uploaded data.
 */
object ZoneClassifier {
    /** dB of Wi-Fi distance one µT of magnet difference is worth. */
    const val MAG_WEIGHT = 1.0
    /** dB of Wi-Fi distance one degree of orientation difference is worth. */
    const val ORIENT_WEIGHT = 0.1
    /** Penalty, in dB, for two scans sharing no access points at all (scaled by the share not in common). */
    const val OVERLAP_PENALTY_DB = 20.0
    /** How sharply confidence favours the closest spot: each [TEMPERATURE_DB] of extra distance divides a spot's weight by e. */
    const val TEMPERATURE_DB = 3.0
    /**
     * Nearest recording within this distance: a full match; at or beyond [UNKNOWN_DB]: somewhere unrecorded.
     * From Vineet's first data (2026-11-19): same spot ≤ 12, other spot ≥ 18.8.
     */
    const val KNOWN_DB = 14.0
    const val UNKNOWN_DB = 22.0

    /** The user's own Wi-Fi never moves, so it counts this many times an ordinary access point. */
    const val HOME_WEIGHT = 4.0
    /**
     * BSSIDs with the locally-administered bit set are mostly phone hotspots and other randomised
     * networks that move around, so they count for little.
     */
    const val RANDOMISED_WEIGHT = 0.25

    fun isRandomised(bssid: String): Boolean =
        bssid.take(2).toIntOrNull(16)?.let { it and 0x02 != 0 } ?: false

    private fun weight(bssid: String, home: Boolean) = when {
        home -> HOME_WEIGHT
        isRandomised(bssid) -> RANDOMISED_WEIGHT
        else -> 1.0
    }

    /**
     * Weighted mean absolute dBm difference over the access points both scans saw, plus
     * [OVERLAP_PENALTY_DB] times the weighted share of access points only one of them saw. Weak,
     * flickering access points can't dominate; the home network counts most, likely hotspots least.
     */
    fun wifiDistance(a: List<WifiReading>, b: List<WifiReading>): Double {
        val ra = a.associateBy { it.b }
        val rb = b.associateBy { it.b }
        val union = ra.keys + rb.keys
        if (union.isEmpty()) return 0.0
        val w = union.associateWith { weight(it, ra[it]?.h == true || rb[it]?.h == true) }
        val shared = ra.keys intersect rb.keys
        if (shared.isEmpty()) return OVERLAP_PENALTY_DB + UNKNOWN_DB
        val sharedWeight = shared.sumOf { w.getValue(it) }
        val diff = shared.sumOf { w.getValue(it) * abs(ra.getValue(it).r - rb.getValue(it).r) } / sharedWeight
        return diff + OVERLAP_PENALTY_DB * (1.0 - sharedWeight / union.sumOf { w.getValue(it) })
    }

    fun distance(a: ZoneSample, b: ZoneSample, features: FeatureSet): Double {
        val w = wifiDistance(a.wifi, b.wifi)
        var total = w * w
        if (features.magnet && a.magMean != null && b.magMean != null) {
            val m = MAG_WEIGHT * abs(a.magMean - b.magMean)
            total += m * m
        }
        if (features.orientation) {
            angleDegrees(a.gravity, b.gravity)?.let { total += (ORIENT_WEIGHT * it).let { o -> o * o } }
        }
        return sqrt(total)
    }

    fun angleDegrees(a: List<Float>?, b: List<Float>?): Double? {
        if (a == null || b == null || a.size != 3 || b.size != 3) return null
        val na = sqrt(a.sumOf { (it * it).toDouble() })
        val nb = sqrt(b.sumOf { (it * it).toDouble() })
        if (na == 0.0 || nb == 0.0) return null
        val cos = (a.indices.sumOf { (a[it] * b[it]).toDouble() } / (na * nb)).coerceIn(-1.0, 1.0)
        return acos(cos) * 180.0 / kotlin.math.PI
    }

    /** 1 when the nearest recording is close, falling linearly to 0 between [KNOWN_DB] and [UNKNOWN_DB]. */
    fun match(nearest: Double): Double = ((UNKNOWN_DB - nearest) / (UNKNOWN_DB - KNOWN_DB)).coerceIn(0.0, 1.0)

    /** Null when nothing has been recorded yet. */
    fun guess(recordings: List<ZoneRecording>, sample: ZoneSample, features: FeatureSet = FeatureSet.WIFI_MAG_ORIENT): Guess? {
        val nearestBySpot = recordings
            .flatMap { rec -> rec.samples.map { rec.spot to distance(sample, it, features) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ds) -> ds.min() }
        if (nearestBySpot.isEmpty()) return null
        val nearest = nearestBySpot.values.min()
        val weights = nearestBySpot.mapValues { exp(-(it.value - nearest) / TEMPERATURE_DB) }
        val total = weights.values.sum()
        val m = match(nearest)
        val scores = weights.mapValues { it.value / total * m } + (UNKNOWN_SPOT to 1.0 - m)
        val best = scores.maxByOrNull { it.value }!!
        return Guess(best.key, best.value, scores, nearest)
    }
}

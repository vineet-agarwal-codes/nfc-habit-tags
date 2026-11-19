package com.habittags.core.zone

import kotlin.math.acos
import kotlin.math.abs
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
 * Weighted k-nearest neighbours over every recorded sample. Keep in step with
 * backend/analysis/zone_lab.py, which replays the same classifier on uploaded data.
 */
object ZoneClassifier {
    const val MISSING_DBM = -100
    const val DEFAULT_K = 5
    /** dB of Wi-Fi distance one µT of magnet difference is worth. */
    const val MAG_WEIGHT = 1.0
    /** dB of Wi-Fi distance one degree of orientation difference is worth. */
    const val ORIENT_WEIGHT = 0.1
    /** Nearest recording within this distance: a full match. Initial values; tune from lab data. */
    const val KNOWN_DB = 10.0
    /** Nearest recording this far or further: certainly somewhere unrecorded. */
    const val UNKNOWN_DB = 25.0

    /** 1 when the nearest recording is close, falling linearly to 0 between [KNOWN_DB] and [UNKNOWN_DB]. */
    fun match(nearest: Double): Double = ((UNKNOWN_DB - nearest) / (UNKNOWN_DB - KNOWN_DB)).coerceIn(0.0, 1.0)

    /** RMS difference in dBm over the union of BSSIDs, a missing AP counting as [MISSING_DBM]. */
    fun wifiDistance(a: List<WifiReading>, b: List<WifiReading>): Double {
        val ra = a.associate { it.b to it.r }
        val rb = b.associate { it.b to it.r }
        val keys = ra.keys + rb.keys
        if (keys.isEmpty()) return 0.0
        val sum = keys.sumOf { k ->
            val d = (ra[k] ?: MISSING_DBM) - (rb[k] ?: MISSING_DBM)
            (d * d).toDouble()
        }
        return sqrt(sum / keys.size)
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

    /** Null when nothing has been recorded yet. */
    fun guess(recordings: List<ZoneRecording>, sample: ZoneSample, features: FeatureSet = FeatureSet.WIFI_MAG_ORIENT, k: Int = DEFAULT_K): Guess? {
        val neighbours = recordings
            .flatMap { rec -> rec.samples.map { rec.spot to distance(sample, it, features) } }
            .sortedBy { it.second }
            .take(k)
        if (neighbours.isEmpty()) return null
        val weights = mutableMapOf<String, Double>()
        for ((spot, d) in neighbours) weights[spot] = (weights[spot] ?: 0.0) + 1.0 / (1.0 + d)
        val total = weights.values.sum()
        val nearest = neighbours.first().second
        val m = match(nearest)
        val scores = weights.mapValues { it.value / total * m } + (UNKNOWN_SPOT to 1.0 - m)
        val best = scores.maxByOrNull { it.value }!!
        return Guess(best.key, best.value, scores, nearest)
    }
}

package com.habittags.core.zone

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoneClassifierTest {
    private fun s(vararg aps: Pair<String, Int>, mag: Float? = null, gravity: List<Float>? = null) =
        ZoneSample(wifi = aps.map { WifiReading(it.first, it.second, 2412) }, magMean = mag, gravity = gravity)

    private fun rec(spot: String, vararg samples: ZoneSample) = ZoneRecording("r-$spot", spot, MODE_RESTING, 0, samples.toList())

    @Test
    fun wifiDistanceCases() {
        val cases = listOf(
            // a, b, expected RMS dBm
            Triple(s("a" to -50), s("a" to -50), 0.0),
            Triple(s("a" to -50), s("a" to -60), 10.0),
            Triple(s("a" to -50), s(), 50.0), // missing AP counts as -100
            Triple(s("a" to -50, "b" to -70), s("a" to -50, "c" to -70), sqrt((900.0 + 900.0) / 3)),
            Triple(s(), s(), 0.0),
        )
        for ((a, b, expected) in cases) assertEquals(expected, ZoneClassifier.wifiDistance(a.wifi, b.wifi), 1e-9, "$a vs $b")
    }

    @Test
    fun guessPicksNearestSpot() {
        val recordings = listOf(
            rec("Desk", s("a" to -45, "b" to -70), s("a" to -47, "b" to -72)),
            rec("Bed", s("a" to -65, "b" to -50), s("a" to -63, "b" to -52)),
        )
        val cases = listOf(
            s("a" to -46, "b" to -71) to "Desk",
            s("a" to -64, "b" to -51) to "Bed",
            s("a" to -50, "b" to -66) to "Desk",
        )
        for ((sample, spot) in cases) assertEquals(spot, ZoneClassifier.guess(recordings, sample)!!.spot, "$sample")
    }

    @Test
    fun scoresSumToOneAndIdenticalSampleIsConfident() {
        val desk = s("a" to -45, "b" to -70)
        val recordings = listOf(rec("Desk", desk, desk, desk), rec("Bed", s("a" to -80, "b" to -40)))
        val g = ZoneClassifier.guess(recordings, desk, k = 3)!!
        assertEquals("Desk", g.spot)
        assertEquals(1.0, g.scores.values.sum(), 1e-9)
        assertEquals(1.0, g.confidence, 1e-9)
    }

    @Test
    fun magnetAndOrientationBreakAWifiTie() {
        val wifi = arrayOf("a" to -50)
        val recordings = listOf(
            rec("Desk", s(*wifi, mag = 30f, gravity = listOf(0f, 0f, 9.8f))),
            rec("Bed", s(*wifi, mag = 60f, gravity = listOf(0f, 9.8f, 0f))),
        )
        val nearDeskMagnet = s(*wifi, mag = 32f)
        assertEquals("Desk", ZoneClassifier.guess(recordings, nearDeskMagnet, FeatureSet.WIFI_MAG, k = 1)!!.spot)
        val bedOrientation = s(*wifi, gravity = listOf(0f, 9.7f, 0.5f))
        assertEquals("Bed", ZoneClassifier.guess(recordings, bedOrientation, FeatureSet.WIFI_MAG_ORIENT, k = 1)!!.spot)
        // Wi-Fi only: a tie, both spots share the weight
        val g = ZoneClassifier.guess(recordings, nearDeskMagnet, FeatureSet.WIFI, k = 2)!!
        assertEquals(0.5, g.confidence, 1e-9)
    }

    @Test
    fun angleDegrees() {
        assertEquals(90.0, ZoneClassifier.angleDegrees(listOf(0f, 0f, 1f), listOf(0f, 1f, 0f))!!, 1e-6)
        assertEquals(0.0, ZoneClassifier.angleDegrees(listOf(0f, 0f, 2f), listOf(0f, 0f, 9f))!!, 1e-6)
        assertNull(ZoneClassifier.angleDegrees(null, listOf(0f, 0f, 1f)))
        assertNull(ZoneClassifier.angleDegrees(listOf(0f, 0f, 0f), listOf(0f, 0f, 1f)))
    }

    @Test
    fun farFromEveryRecordingIsUnknown() {
        val recordings = listOf(rec("Desk", s("a" to -45, "b" to -70), s("a" to -47, "b" to -72)))
        val cases = listOf(
            // sample, expected spot, confidence range
            Triple(s("a" to -46, "b" to -71), "Desk", 0.9..1.0),            // at the desk
            Triple(s("c" to -50, "d" to -60), UNKNOWN_SPOT, 0.9..1.0),     // other APs entirely
            Triple(s("a" to -75, "b" to -90), UNKNOWN_SPOT, 0.5..1.0),     // same APs, much weaker
        )
        for ((sample, spot, range) in cases) {
            val g = ZoneClassifier.guess(recordings, sample)!!
            assertEquals(spot, g.spot, "$sample")
            assertTrue(g.confidence in range, "$sample confidence ${g.confidence}")
            assertEquals(1.0, g.scores.values.sum(), 1e-9)
        }
    }

    @Test
    fun matchFallsOffWithDistance() {
        val cases = listOf(0.0 to 1.0, ZoneClassifier.KNOWN_DB to 1.0, (ZoneClassifier.KNOWN_DB + ZoneClassifier.UNKNOWN_DB) / 2 to 0.5, ZoneClassifier.UNKNOWN_DB to 0.0, 99.0 to 0.0)
        for ((d, expected) in cases) assertEquals(expected, ZoneClassifier.match(d), 1e-9, "d=$d")
    }

    @Test
    fun noRecordingsMeansNoGuess() {
        assertNull(ZoneClassifier.guess(emptyList(), s("a" to -50)))
        assertTrue(ZoneClassifier.guess(listOf(rec("Desk")), s("a" to -50)) == null)
    }
}

package io.github.carrombot.core.vision

import org.junit.Assert.assertThrows
import org.junit.Test

class ObjectProfileTest {
    private val color = HsvRange(0, 10, 180, 255, 180, 255)
    private val profile = ObjectProfile(ObjectKind.QUEEN, color, 0.03, 0.08)

    @Test
    fun invalidColorIntervalsAreRejected() {
        listOf(
            { color.copy(minHue = -1) },
            { color.copy(maxHue = 180) },
            { color.copy(maxSaturation = 179) },
            { color.copy(minSaturation = 256) },
            { color.copy(maxValue = 179) },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun invalidGeometryAndNonfiniteThresholdsAreRejected() {
        listOf(
            { profile.copy(minRadiusFraction = 0.0) },
            { profile.copy(maxRadiusFraction = 0.02) },
            { profile.copy(maxRadiusFraction = 0.5) },
            { profile.copy(minRadiusFraction = Double.NaN) },
            { profile.copy(minFill = Double.POSITIVE_INFINITY) },
            { profile.copy(minCircularity = 0.0) },
            { profile.copy(cornerFraction = 0.5) },
            { profile.copy(minFill = 0.8) },
            { profile.copy(minCircularity = 0.7) },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }
}

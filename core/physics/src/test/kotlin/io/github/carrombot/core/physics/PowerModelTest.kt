package io.github.carrombot.core.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerModelTest {
    @Test
    fun `normalized power has exact endpoints and inverse`() {
        val model = PowerModel(1000.0)
        assertEquals(0.0, model.speedForPower(0.0), 0.0)
        assertEquals(1000.0, model.speedForPower(1.0), 0.0)
        assertEquals(250.0, model.speedForPower(0.25), 0.0)
        assertEquals(0.25, model.powerForSpeed(250.0), 0.0)
        assertEquals(0.0, model.powerForSpeed(0.0), 0.0)
        assertEquals(1.0, model.powerForSpeed(1000.0), 0.0)
    }

    @Test
    fun `mapping and inverse are monotonic across calibration scales`() {
        for (maximum in listOf(0.1, 1000.0, 1e100)) {
            val model = PowerModel(maximum)
            var previous = 0.0
            repeat(1001) { index ->
                val power = index / 1000.0
                val speed = model.speedForPower(power)
                assertTrue(speed >= previous)
                assertEquals(power, model.powerForSpeed(speed), 1e-14)
                previous = speed
            }
        }
    }

    @Test
    fun `invalid calibration power and unreachable speeds fail without clamping`() {
        for (maximum in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { PowerModel(maximum) }
        }
        val model = PowerModel(100.0)
        for (power in listOf(-0.1, 1.01, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { model.speedForPower(power) }
        }
        for (speed in listOf(-1.0, 100.01, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { model.powerForSpeed(speed) }
        }
    }
}

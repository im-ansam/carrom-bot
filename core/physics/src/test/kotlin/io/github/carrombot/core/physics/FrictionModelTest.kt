package io.github.carrombot.core.physics

import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties
import java.util.Random
import kotlin.math.hypot

class FrictionModelTest {
    @Test
    fun `constant drag integrates moving and stopped portions without reversing`() {
        val model = FrictionModel(10.0)
        assertEquals(1.0, model.stoppingTime(10.0), 0.0)
        assertEquals(5.0, model.stoppingDistance(10.0), 0.0)
        val moving = model.step(Vec2(6.0, 8.0), 0.5)
        assertEquals(Vec2(3.0, 4.0), moving.velocity)
        assertEquals(Vec2(2.25, 3.0), moving.displacement)
        val stopped = model.step(Vec2(6.0, 8.0), 2.0)
        assertEquals(Vec2(0.0, 0.0), stopped.velocity)
        assertEquals(Vec2(3.0, 4.0), stopped.displacement)
    }

    @Test
    fun `zero speed and zero interval are stable`() {
        val model = FrictionModel(1.0)
        assertEquals(0.0, model.stoppingTime(0.0), 0.0)
        assertEquals(0.0, model.stoppingDistance(0.0), 0.0)
        assertEquals(FrictionStep(Vec2(0.0, 0.0), Vec2(0.0, 0.0)), model.step(Vec2(-0.0, 0.0), 10.0))
        assertEquals(FrictionStep(Vec2(0.0, 0.0), Vec2(-3.0, 4.0)), model.step(Vec2(-3.0, 4.0), 0.0))
    }

    @Test
    fun `versioned seeded fixture matches analytic drag and monotonic stopping distance`() {
        val fixture = Properties()
        javaClass.getResourceAsStream("/drag-v1.properties").use { fixture.load(requireNotNull(it)) }
        assertEquals("1", fixture.getProperty("version"))
        val model = FrictionModel(fixture.getProperty("deceleration").toDouble())
        val random = Random(fixture.getProperty("seed").toLong())
        val tolerance = fixture.getProperty("tolerance").toDouble()
        val speeds = List(128) { random.nextDouble() * 100.0 }.sorted()
        assertTrue(speeds.zipWithNext().all { (a, b) -> model.stoppingDistance(b) >= model.stoppingDistance(a) })
        for (speed in speeds) {
            val duration = random.nextDouble() * 5.0
            val time = minOf(duration, speed / model.deceleration)
            val result = model.step(Vec2(-speed, 0.0), duration)
            val expectedDistance = speed * time - model.deceleration * time * time / 2.0
            val expectedSpeed = maxOf(0.0, speed - model.deceleration * duration)
            assertEquals(-expectedDistance, result.displacement.x, tolerance)
            assertEquals(-expectedSpeed, result.velocity.x, tolerance)
            assertEquals(speed * speed / (2.0 * model.deceleration), model.stoppingDistance(speed), tolerance)
            assertEquals(result, model.step(Vec2(-speed, 0.0), duration))
            assertTrue(hypot(result.velocity.x, result.velocity.y) <= speed)
        }
    }

    @Test
    fun `split steps preserve distance and velocity including a stop inside a step`() {
        val model = FrictionModel(10.0)
        var velocity = Vec2(-6.0, 8.0)
        var x = 0.0
        var y = 0.0
        repeat(7) {
            val step = model.step(velocity, 0.3)
            x += step.displacement.x
            y += step.displacement.y
            velocity = step.velocity
        }
        assertEquals(-3.0, x, 1e-12)
        assertEquals(4.0, y, 1e-12)
        assertEquals(Vec2(0.0, 0.0), velocity)
    }

    @Test
    fun `invalid inputs and nonrepresentable results fail explicitly`() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { FrictionModel(value) }
        }
        val model = FrictionModel(1.0)
        for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { model.stoppingTime(value) }
            assertThrows(IllegalArgumentException::class.java) { model.stoppingDistance(value) }
            assertThrows(IllegalArgumentException::class.java) { model.step(Vec2(1.0, 0.0), value) }
        }
        assertThrows(IllegalArgumentException::class.java) { model.stoppingDistance(Double.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) {
            model.step(Vec2(Double.MAX_VALUE, Double.MAX_VALUE), 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) { FrictionModel(Double.MIN_VALUE).stoppingTime(1.0) }
    }

    @Test
    fun `large and tiny finite speeds avoid squaring overflow and tolerate underflow`() {
        val huge = FrictionModel(Double.MAX_VALUE)
        assertEquals(Double.MAX_VALUE / 2.0, huge.stoppingDistance(Double.MAX_VALUE), 0.0)
        assertEquals(Vec2(0.0, 0.0), huge.step(Vec2(Double.MAX_VALUE, 0.0), 2.0).velocity)
        val tiny = FrictionModel(Double.MIN_VALUE).step(Vec2(Double.MIN_VALUE, 0.0), 1.0)
        assertEquals(Vec2(0.0, 0.0), tiny.velocity)
        assertTrue(tiny.displacement.x.isFinite())
    }
}

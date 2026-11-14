package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.PredictionFrame
import io.github.carrombot.core.model.PredictionTermination
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties
import java.util.Random

class MotionSimulatorTest {
    private fun board(
        velocity: Vec2 = Vec2(10.0, -5.0),
        discs: List<Disc> = emptyList(),
    ): BoardState =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(1000.0, 1000.0)),
            emptyList(),
            discs,
            Striker("s", Circle(Vec2(100.0, 100.0), 5.0), velocity),
        )

    @Test
    fun `straight motion advances clock and preserves velocity`() {
        val initial = board()
        val result = MotionSimulator(PhysicsConfig(0.25, 4)).simulate(initial)
        assertEquals(5, result.size)
        assertEquals(1.0, result.last().elapsedSeconds, 0.0)
        assertEquals(
            Vec2(110.0, 95.0),
            result
                .last()
                .boardState.striker.circle.center,
        )
        assertEquals(
            initial.striker.velocity,
            result
                .last()
                .boardState.striker.velocity,
        )
        assertEquals(Vec2(100.0, 100.0), initial.striker.circle.center)
    }

    @Test
    fun `zero and signed zero terminate immediately even with maximum budget`() {
        val initial = board(Vec2(-0.0, 0.0))
        assertEquals(listOf(SimulationState(initial)), MotionSimulator(PhysicsConfig(0.01, 100_000)).simulate(initial))
    }

    @Test
    fun `moving disc prevents rest and preserves stable input ordering`() {
        val discs =
            listOf(
                Disc("z", DiscKind.QUEEN, Circle(Vec2(500.0, 500.0), 5.0), Vec2(-2.0, 3.0)),
                Disc("a", DiscKind.WHITE, Circle(Vec2(700.0, 700.0), 5.0)),
            )
        val final = MotionSimulator(PhysicsConfig(0.5, 2)).simulate(board(Vec2(0.0, 0.0), discs)).last()
        assertEquals(listOf("z", "a"), final.boardState.discs.map { it.id })
        assertEquals(
            Vec2(498.0, 503.0),
            final.boardState.discs
                .first()
                .circle.center,
        )
        assertEquals(discs.last(), final.boardState.discs.last())
        assertEquals(1.0, final.elapsedSeconds, 0.0)
    }

    @Test
    fun `prediction retains pre impulse frame and reports time limit`() {
        val initial = board(Vec2(0.0, 0.0))
        val shot = ShotInput(initial, Vec2(1.0, 2.0))
        val prediction = MotionSimulator(PhysicsConfig(0.1, 3)).predict(shot)
        assertEquals(PredictionFrame(0.0, initial), prediction.frames.first())
        assertEquals(PredictionTermination.TIME_LIMIT, prediction.termination)
        assertEquals(4, prediction.frames.size)
        assertEquals(0.1 * 3, prediction.frames.last().elapsedSeconds, 0.0)
        assertEquals(
            shot.initialVelocity,
            prediction.frames
                .last()
                .boardState.striker.velocity,
        )
        assertTrue(prediction.frames.zipWithNext().all { (a, b) -> b.elapsedSeconds > a.elapsedSeconds })
    }

    @Test
    fun `smaller timestep preserves analytic endpoint with more samples`() {
        val coarse = MotionSimulator(PhysicsConfig(0.25, 4)).simulate(board())
        val fine = MotionSimulator(PhysicsConfig(0.01, 100)).simulate(board())
        assertEquals(101, fine.size)
        assertEquals(coarse.last().elapsedSeconds, fine.last().elapsedSeconds, 0.0)
        assertEquals(
            110.0,
            fine
                .last()
                .boardState.striker.circle.center.x,
            1e-10,
        )
        assertEquals(
            95.0,
            fine
                .last()
                .boardState.striker.circle.center.y,
            1e-10,
        )
    }

    @Test
    fun `versioned seeded fixture repeats exactly and matches every analytic sample`() {
        val fixture = Properties()
        javaClass.getResourceAsStream("/free-motion-v1.properties").use { fixture.load(requireNotNull(it)) }
        assertEquals("1", fixture.getProperty("version"))
        val random = Random(fixture.getProperty("seed").toLong())
        val config = PhysicsConfig(fixture.getProperty("dt").toDouble(), fixture.getProperty("steps").toInt())
        val tolerance = fixture.getProperty("tolerance").toDouble()
        repeat(32) {
            val initial = board(Vec2(random.nextDouble() * 10.0, random.nextDouble() * -10.0))
            val simulator = MotionSimulator(config)
            val states = simulator.simulate(initial)
            assertEquals(states, simulator.simulate(initial))
            states.forEachIndexed { index, state ->
                assertEquals(index * config.timeStepSeconds, state.elapsedSeconds, 0.0)
                val center = state.boardState.striker.circle.center
                assertEquals(100.0 + initial.striker.velocity.x * state.elapsedSeconds, center.x, tolerance)
                assertEquals(100.0 + initial.striker.velocity.y * state.elapsedSeconds, center.y, tolerance)
            }
        }
    }

    @Test
    fun `trajectory and board collections cannot be mutated through aliases`() {
        val discs = mutableListOf(Disc("d", DiscKind.BLACK, Circle(Vec2(500.0, 500.0), 5.0)))
        val initial = board(discs = discs)
        val states = MotionSimulator(PhysicsConfig(0.1, 1)).simulate(initial)
        discs.clear()
        assertEquals(
            1,
            states
                .last()
                .boardState.discs.size,
        )
        assertThrows(UnsupportedOperationException::class.java) { (states as MutableList).clear() }
        val ownedDiscs = states.last().boardState.discs
        assertThrows(UnsupportedOperationException::class.java) { (ownedDiscs as MutableList).clear() }
    }

    @Test
    fun `invalid configuration and clocks fail explicitly`() {
        for (dt in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(dt, 1) }
        }
        for (steps in listOf(0, -1, 100_001, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(0.1, steps) }
        }
        assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(Double.MAX_VALUE, 2) }
        for (time in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { SimulationState(board(), time) }
        }
    }

    @Test
    fun `out of bounds and overflowing motion fail without modifying input`() {
        val initial = board(Vec2(Double.MAX_VALUE, 0.0))
        assertThrows(IllegalArgumentException::class.java) { MotionSimulator(PhysicsConfig(2.0, 1)).simulate(initial) }
        assertThrows(IllegalArgumentException::class.java) { MotionSimulator(PhysicsConfig(1.0, 1)).simulate(initial) }
        assertEquals(Vec2(100.0, 100.0), initial.striker.circle.center)
    }

    @Test
    fun `tiny nonzero velocity is not falsely reported as rest`() {
        val result = MotionSimulator(PhysicsConfig(1.0, 1)).simulate(board(Vec2(Double.MIN_VALUE, 0.0)))
        assertEquals(2, result.size)
        assertEquals(1.0, result.last().elapsedSeconds, 0.0)
    }
}

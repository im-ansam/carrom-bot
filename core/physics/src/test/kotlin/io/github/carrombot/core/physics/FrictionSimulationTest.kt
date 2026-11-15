package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.PredictionTermination
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrictionSimulationTest {
    private val initial =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(1000.0, 1000.0)),
            emptyList(),
            listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(500.0, 500.0), 5.0), Vec2(-6.0, 8.0))),
            Striker("s", Circle(Vec2(100.0, 100.0), 5.0)),
        )

    @Test
    fun `friction updates discs and striker and prediction stops at rest`() {
        val shot = ShotInput(initial, Vec2(10.0, 0.0))
        val prediction = MotionSimulator(PhysicsConfig(0.25, 100, 4.0)).predict(shot)
        val final = prediction.frames.last()
        assertEquals(PredictionTermination.REST, prediction.termination)
        // Repeated vector normalization can leave a rounding residual at the exact stop tick.
        assertTrue(final.elapsedSeconds in 2.5..2.75)
        assertEquals(Vec2(112.5, 100.0), final.boardState.striker.circle.center)
        val discCenter =
            final.boardState.discs
                .single()
                .circle.center
        assertEquals(492.5, discCenter.x, 1e-10)
        assertEquals(510.0, discCenter.y, 1e-10)
        assertEquals(Vec2(0.0, 0.0), final.boardState.striker.velocity)
        assertEquals(
            Vec2(0.0, 0.0),
            final.boardState.discs
                .single()
                .velocity,
        )
        assertEquals(initial, prediction.frames.first().boardState)
    }

    @Test
    fun `different timesteps agree with analytic stop inside last interval`() {
        for (dt in listOf(0.03, 0.25, 0.7, 4.0)) {
            val states = MotionSimulator(PhysicsConfig(dt, 1000, 4.0)).simulate(initial)
            for (state in states) {
                val time = minOf(state.elapsedSeconds, 2.5)
                val distance = 10.0 * time - 2.0 * time * time
                val disc = state.boardState.discs.single()
                assertEquals(500.0 - 0.6 * distance, disc.circle.center.x, 1e-9)
                assertEquals(500.0 + 0.8 * distance, disc.circle.center.y, 1e-9)
            }
            assertEquals(
                Vec2(0.0, 0.0),
                states
                    .last()
                    .boardState.discs
                    .single()
                    .velocity,
            )
            assertTrue(states.last().elapsedSeconds <= 2.5 + dt * 2.0)
            assertEquals(states, MotionSimulator(PhysicsConfig(dt, 1000, 4.0)).simulate(initial))
        }
    }

    @Test
    fun `time cap reports truncation when drag has not stopped all bodies`() {
        val shot = ShotInput(initial, Vec2(1.0, 0.0))
        val result = MotionSimulator(PhysicsConfig(0.25, 1, 4.0)).predict(shot)
        assertEquals(PredictionTermination.TIME_LIMIT, result.termination)
        assertEquals(2, result.frames.size)
        assertEquals(0.25, result.frames.last().elapsedSeconds, 0.0)
    }

    @Test
    fun `power speed feeds shared shot contract and zero power stays a no launch`() {
        val power = PowerModel(20.0)
        val shot = ShotInput(initial, Vec2(power.speedForPower(0.5), 0.0))
        val result = MotionSimulator(PhysicsConfig(0.25, 100, 4.0)).predict(shot)
        assertEquals(PredictionTermination.REST, result.termination)
        assertEquals(
            112.5,
            result.frames
                .last()
                .boardState.striker.circle.center.x,
            1e-10,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ShotInput(initial, Vec2(power.speedForPower(0.0), 0.0))
        }
    }

    @Test
    fun `negative and nonfinite configured drag are rejected`() {
        for (drag in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(0.1, 100, drag) }
        }
        assertEquals(0.0, PhysicsConfig(0.1, 100).deceleration, 0.0)
    }
}

package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties
import java.util.Random
import kotlin.math.abs

class ReboundSimulationTest {
    @Test
    fun `all walls reflect straight diagonal and oblique approaches`() {
        for (velocity in listOf(Vec2(40.0, 0.0), Vec2(40.0, 40.0), Vec2(40.0, 10.0))) {
            for (sign in listOf(-1.0, 1.0)) {
                val center = Vec2(if (sign > 0.0) 80.0 else 20.0, 50.0)
                val v = Vec2(velocity.x * sign, velocity.y)
                val final = run(board(center, v))
                assertEquals(if (sign > 0.0) 70.0 else 30.0, final.striker.circle.center.x, 1e-7)
                assertEquals(50.0 + v.y, final.striker.circle.center.y, 1e-7)
                assertEquals(-v.x, final.striker.velocity.x, 1e-7)
                assertEquals(v.y, final.striker.velocity.y, 1e-7)
                val transposed = run(board(Vec2(center.y, center.x), Vec2(v.y, v.x)))
                assertEquals(final.striker.circle.center.x, transposed.striker.circle.center.y, 1e-7)
                assertEquals(final.striker.velocity.x, transposed.striker.velocity.y, 1e-7)
            }
        }
    }

    @Test
    fun `corner reflections use X then Y including tangential losses`() {
        val input = board(Vec2(95.0, 95.0), Vec2(40.0, 40.0))
        val config = PhysicsConfig(1.0, 1, wallRestitution = 0.5, wallTangentialRestitution = 0.5)
        val final = run(input, config)
        assertEquals(-10.0, final.striker.velocity.x, 1e-7)
        assertEquals(-10.0, final.striker.velocity.y, 1e-7)
        assertEquals(Vec2(85.0, 85.0), final.striker.circle.center)
        val elastic = run(board(Vec2(80.0, 80.0), Vec2(40.0, 40.0)))
        assertEquals(Vec2(70.0, 70.0), elastic.striker.circle.center)
    }

    @Test
    fun `double and many banks match versioned unfolded trajectories`() {
        val fixture = Properties()
        javaClass.getResourceAsStream("/rebound-v1.properties").use { fixture.load(requireNotNull(it)) }
        assertEquals("1", fixture.getProperty("version"))
        val random = Random(fixture.getProperty("seed").toLong())
        val tolerance = fixture.getProperty("tolerance").toDouble()
        val velocities =
            listOf(Vec2(200.0, 0.0), Vec2(1000.0, 0.0)) +
                List(fixture.getProperty("cases").toInt()) {
                    Vec2(random.nextDouble() * 4000.0 - 2000.0, random.nextDouble() * 4000.0 - 2000.0)
                }
        for (v in velocities) {
            val input = board(Vec2(50.0, 50.0), v)
            val final = run(input)
            assertEquals(final, run(input))
            assertEquals(fold(50.0 + v.x), final.striker.circle.center.x, tolerance)
            assertEquals(fold(50.0 + v.y), final.striker.circle.center.y, tolerance)
            assertEquals(v.x * v.x + v.y * v.y, final.striker.velocity.let { it.x * it.x + it.y * it.y }, tolerance)
        }
    }

    @Test
    fun `wall normal tangent and friction coefficients dissipate without tangent reversal`() {
        val input = board(Vec2(95.0, 50.0), Vec2(40.0, 20.0))
        val config = PhysicsConfig(1.0, 1, wallRestitution = 0.5, wallTangentialRestitution = 0.8, wallFriction = 0.1)
        val final = run(input, config)
        assertEquals(Vec2(-20.0, 10.0), final.striker.velocity)
        assertEquals(Vec2(75.0, 60.0), final.striker.circle.center)
        val rough = run(input, config.copy(wallFriction = 1.0))
        assertEquals(0.0, rough.striker.velocity.y, 0.0)
        assertEquals(50.0, rough.striker.circle.center.y, 0.0)
        val resting = run(input, config.copy(wallRestitution = 0.0, wallFriction = 1.0))
        assertEquals(0.0, abs(resting.striker.velocity.x) + abs(resting.striker.velocity.y), 0.0)
    }

    @Test
    fun `slow outward contact reflects and inward or tangent contact is unchanged`() {
        for (v in listOf(Vec2(1e-12, 0.0), Vec2(-1e-12, 0.0), Vec2(0.0, 1e-12))) {
            val final = run(board(Vec2(95.0, 50.0), v))
            assertEquals(-abs(v.x), final.striker.velocity.x, 0.0)
            assertEquals(v.y, final.striker.velocity.y, 0.0)
        }
    }

    @Test
    fun `drag banks agree across sample clocks and stopping before wall does not bounce`() {
        val input = board(Vec2(80.0, 50.0), Vec2(140.0, 7.0))
        val config = PhysicsConfig(1.0, 1, deceleration = 50.0, wallRestitution = 0.7, wallFriction = 0.02)
        val coarse = run(input, config)
        val fine = run(input, config.copy(timeStepSeconds = 0.001, maxSteps = 1000))
        assertEquals(coarse.striker.circle.center.x, fine.striker.circle.center.x, 1e-6)
        assertEquals(coarse.striker.circle.center.y, fine.striker.circle.center.y, 1e-6)
        assertEquals(coarse.striker.velocity.x, fine.striker.velocity.x, 1e-6)
        val stopped = run(board(Vec2(80.0, 50.0), Vec2(10.0, 0.0)), config.copy(deceleration = 10.0))
        assertEquals(85.0, stopped.striker.circle.center.x, 1e-7)
        assertEquals(Vec2(0.0, 0.0), stopped.striker.velocity)
        val normal = run(board(Vec2(80.0, 50.0), Vec2(40.0, 0.0)), PhysicsConfig(1.0, 1, deceleration = 10.0))
        assertEquals(75.0, normal.striker.circle.center.x, 1e-7)
        assertEquals(-30.0, normal.striker.velocity.x, 1e-7)
        val exactStop = run(board(Vec2(90.0, 50.0), Vec2(10.0, 0.0)), PhysicsConfig(2.0, 1, deceleration = 10.0))
        assertEquals(95.0, exactStop.striker.circle.center.x, 1e-7)
        assertEquals(Vec2(0.0, 0.0), exactStop.striker.velocity)
    }

    @Test
    fun `wall then body and same-time body then wall events transfer velocity`() {
        val initial = board(Vec2(80.0, 50.0), Vec2(40.0, 0.0))
        val target = Disc("d", DiscKind.BLACK, Circle(Vec2(50.0, 50.0), 5.0))
        val input = BoardState(initial.bounds, emptyList(), listOf(target), initial.striker)
        val final = run(input, PhysicsConfig(1.5, 1))
        assertEquals(60.0, final.striker.circle.center.x, 1e-7)
        assertEquals(
            40.0,
            final.discs
                .single()
                .circle.center.x,
            1e-7,
        )
        assertEquals(
            -40.0,
            final.discs
                .single()
                .velocity.x,
            1e-7,
        )
        val touching =
            BoardState(
                initial.bounds,
                emptyList(),
                listOf(target.copy(circle = Circle(Vec2(95.0, 50.0), 5.0))),
                initial.striker.copy(circle = Circle(Vec2(85.0, 50.0), 5.0)),
            )
        val after = run(touching)
        assertEquals(-40.0, after.striker.velocity.x, 1e-7)
        assertEquals(
            0.0,
            after.discs
                .single()
                .velocity.x,
            1e-7,
        )
    }

    @Test
    fun `disc banks preserve prediction input frame and identity`() {
        val initial = board(Vec2(50.0, 80.0), Vec2(1.0, 0.0))
        val disc = Disc("d", DiscKind.BLACK, Circle(Vec2(50.0, 20.0), 5.0), Vec2(1000.0, 0.0))
        val input = BoardState(initial.bounds, emptyList(), listOf(disc), initial.striker)
        val simulator = MotionSimulator(PhysicsConfig(1.0, 1))
        val prediction = simulator.predict(ShotInput(input, initial.striker.velocity))
        assertEquals(input, prediction.frames.first().boardState)
        val final = prediction.frames.last().boardState
        assertEquals(
            40.0,
            final.discs
                .single()
                .circle.center.x,
            1e-7,
        )
        assertEquals("d", final.discs.single().id)
        assertEquals(disc, input.discs.single())
        assertTrue(final.bounds.contains(final.discs.single().circle))
    }

    @Test
    fun `invalid wall coefficients and exhausted rebound budget fail explicitly`() {
        for (v in listOf(-1.0, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(1.0, 1, wallRestitution = v) }
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(1.0, 1, wallTangentialRestitution = v) }
        }
        for (v in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(1.0, 1, wallFriction = v) }
        }
        val input = board(Vec2(50.0, 50.0), Vec2(1000.0, 0.0))
        assertThrows(IllegalArgumentException::class.java) {
            run(input, PhysicsConfig(1.0, 1, maxCollisionIterations = 5))
        }
        assertEquals(Vec2(50.0, 50.0), input.striker.circle.center)
    }

    private fun fold(position: Double): Double {
        val wrapped = ((position - 5.0) % 180.0 + 180.0) % 180.0
        return 5.0 + if (wrapped <= 90.0) wrapped else 180.0 - wrapped
    }

    private fun run(
        input: BoardState,
        config: PhysicsConfig = PhysicsConfig(1.0, 1),
    ): BoardState = MotionSimulator(config).simulate(input).last().boardState

    @Test
    fun `straight rebound uses remaining step without leaving board`() {
        val board = board(Vec2(80.0, 50.0), Vec2(40.0, 0.0))
        val final = MotionSimulator(PhysicsConfig(1.0, 1)).simulate(board).last().boardState
        assertEquals(70.0, final.striker.circle.center.x, 1e-7)
        assertEquals(-40.0, final.striker.velocity.x, 1e-7)
        assertEquals(Vec2(80.0, 50.0), board.striker.circle.center)
    }

    private fun board(
        center: Vec2,
        velocity: Vec2,
    ): BoardState =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            emptyList(),
            emptyList(),
            Striker("s", Circle(center, 5.0), velocity),
        )
}

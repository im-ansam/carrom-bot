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
import kotlin.math.hypot

class CollisionSimulationTest {
    private fun run(
        initial: BoardState,
        config: PhysicsConfig = PhysicsConfig(0.5, 1),
    ): BoardState = MotionSimulator(config).simulate(initial).last().boardState

    private fun withDiscs(
        initial: BoardState,
        discs: List<Disc>,
    ): BoardState = BoardState(initial.bounds, initial.pockets, discs, initial.striker)

    @Test
    fun `unequal masses and restitution follow analytic normal impulse`() {
        val final = run(board(), PhysicsConfig(0.5, 1, strikerMass = 2.0, restitution = 0.5))
        assertEquals(50.0, final.striker.velocity.x, 1e-9)
        assertEquals(
            100.0,
            final.discs
                .single()
                .velocity.x,
            1e-9,
        )
        assertEquals(
            200.0,
            2.0 * final.striker.velocity.x +
                final.discs
                    .single()
                    .velocity.x,
            1e-9,
        )
    }

    @Test
    fun `touching chain propagates through repeated same-time contacts`() {
        val initial = board()
        val discs = (0..3).map { Disc("d$it", DiscKind.BLACK, Circle(Vec2(110.0 + it * 10.0, 100.0), 5.0)) }
        val final = run(withDiscs(initial, discs))
        assertEquals(0.0, final.striker.velocity.x, 1e-9)
        final.discs.dropLast(1).forEach { assertEquals(0.0, it.velocity.x, 1e-9) }
        assertEquals(
            100.0,
            final.discs
                .last()
                .velocity.x,
            1e-9,
        )
        assertEquals(
            190.0,
            final.discs
                .last()
                .circle.center.x,
            1e-7,
        )
    }

    @Test
    fun `simultaneous and near contacts repeat independently of disc storage order`() {
        val initial = board()
        val discs =
            listOf(-1.0, 1.0).mapIndexed { i, sign ->
                Disc("d$i", DiscKind.WHITE, Circle(Vec2(130.0 + i * 1e-10, 100.0 + sign * 6.0), 5.0))
            }
        val a = withDiscs(initial, discs)
        val simulator = MotionSimulator(PhysicsConfig(0.3, 1))
        assertEquals(simulator.simulate(a), simulator.simulate(a))
        val forward = run(a, simulator.config)
        val reverse = run(withDiscs(initial, discs.reversed()), simulator.config)
        assertEquals(forward.striker, reverse.striker)
        assertEquals(forward.discs, reverse.discs.reversed())
        assertSeparated(forward)
    }

    @Test
    fun `grazing tangent and near miss retain velocity`() {
        for (offset in listOf(10.0, 10.000001)) {
            val initial = board()
            val target = initial.discs.single().copy(circle = Circle(Vec2(130.0, 100.0 + offset), 5.0))
            val final = run(withDiscs(initial, listOf(target)))
            assertEquals(initial.striker.velocity, final.striker.velocity)
            assertEquals(target.velocity, final.discs.single().velocity)
        }
    }

    @Test
    fun `initial stationary coincident bodies separate without changing frame zero`() {
        val initial = board()
        val overlapped =
            BoardState(
                initial.bounds,
                emptyList(),
                initial.discs,
                initial.striker.copy(circle = initial.discs.single().circle, velocity = Vec2(0.0, 0.0)),
            )
        val states = MotionSimulator(PhysicsConfig(0.1, 2)).simulate(overlapped)
        assertEquals(overlapped, states.first().boardState)
        assertEquals(2, states.size)
        assertSeparated(states.last().boardState)
        assertEquals(
            Vec2(0.0, 0.0),
            states
                .last()
                .boardState.striker.velocity,
        )
    }

    @Test
    fun `versioned glancing fixtures conserve momentum and elastic energy`() {
        val fixture = Properties()
        javaClass.getResourceAsStream("/contact-v1.properties").use { fixture.load(requireNotNull(it)) }
        assertEquals("1", fixture.getProperty("version"))
        val random = Random(fixture.getProperty("seed").toLong())
        val tolerance = fixture.getProperty("tolerance").toDouble()
        repeat(fixture.getProperty("cases").toInt()) {
            val initial = board()
            val target =
                initial.discs.single().copy(
                    circle = Circle(Vec2(130.0, 100.0 + random.nextDouble() * 9.9), 5.0),
                )
            val input = withDiscs(initial, listOf(target))
            val mass = 0.5 + random.nextDouble() * 3.0
            val config = PhysicsConfig(0.5, 1, strikerMass = mass)
            val final = run(input, config)
            assertEquals(final, run(input, config))
            val s = final.striker.velocity
            val d = final.discs.single().velocity
            assertEquals(mass * 100.0, mass * s.x + d.x, tolerance)
            assertEquals(0.0, mass * s.y + d.y, tolerance)
            assertEquals(mass * 10000.0, mass * (s.x * s.x + s.y * s.y) + d.x * d.x + d.y * d.y, tolerance)
            assertSeparated(final)
        }
    }

    @Test
    fun `drag collision and prediction preserve contracts`() {
        val initial = board()
        val config = PhysicsConfig(0.05, 20, deceleration = 10.0)
        val simulator = MotionSimulator(config)
        val shot =
            ShotInput(initial, initial.striker.velocity)
        val prediction = simulator.predict(shot)
        assertEquals(initial, prediction.frames.first().boardState)
        assertTrue(
            prediction.frames
                .last()
                .boardState.discs
                .single()
                .velocity.x > 0.0,
        )
        prediction.frames.drop(1).forEach { assertSeparated(it.boardState) }
    }

    @Test
    fun `invalid parameters and exhausted work budget fail explicitly`() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(0.1, 1, discMass = value) }
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(0.1, 1, strikerMass = value) }
        }
        for (value in listOf(-1.0, 1.1, Double.NaN)) {
            assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(0.1, 1, restitution = value) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            run(board(), PhysicsConfig(0.5, 1, maxCollisionIterations = 1))
        }
        assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(0.1, 1, contactTolerance = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { PhysicsConfig(0.1, 1, maxCollisionIterations = 100001) }
    }

    @Test
    fun `drag glancing contacts agree across timesteps for each restitution`() {
        for (restitution in listOf(0.0, 0.5, 1.0)) {
            val initial = board()
            val target = initial.discs.single().copy(circle = Circle(Vec2(130.0, 106.0), 5.0))
            val input = withDiscs(initial, listOf(target))
            val coarse = run(input, PhysicsConfig(0.5, 1, deceleration = 20.0, restitution = restitution))
            val fine = run(input, PhysicsConfig(0.005, 100, deceleration = 20.0, restitution = restitution))
            assertSeparated(coarse)
            assertTrue(
                coarse.discs
                    .single()
                    .velocity.x > 0.0,
            )
            assertEquals(coarse.striker.circle.center.x, fine.striker.circle.center.x, 1e-6)
            assertEquals(coarse.striker.circle.center.y, fine.striker.circle.center.y, 1e-6)
            assertEquals(
                coarse.discs
                    .single()
                    .velocity.x,
                fine.discs
                    .single()
                    .velocity.x,
                1e-6,
            )
        }
    }

    @Test
    fun `sweep splits at stopping times and finds later impact on stopped body`() {
        val initial = board()
        val target = initial.discs.single().copy(velocity = Vec2(10.0, 0.0))
        val input = withDiscs(initial, listOf(target))
        val coarse = run(input, PhysicsConfig(1.0, 1, deceleration = 50.0))
        val fine = run(input, PhysicsConfig(0.01, 100, deceleration = 50.0))
        assertTrue(
            coarse.discs
                .single()
                .circle.center.x > 140.0,
        )
        assertEquals(
            coarse.discs
                .single()
                .circle.center.x,
            fine.discs
                .single()
                .circle.center.x,
            1e-6,
        )
        assertEquals(coarse.striker.circle.center.x, fine.striker.circle.center.x, 1e-6)
        assertSeparated(coarse)
    }

    private fun assertSeparated(board: BoardState) {
        val circles = board.discs.map { it.circle } + board.striker.circle
        for (i in circles.indices) {
            for (j in i + 1 until circles.size) {
                val a = circles[i]
                val b = circles[j]
                assertTrue(hypot(a.center.x - b.center.x, a.center.y - b.center.y) >= a.radius + b.radius - 1e-7)
            }
        }
    }

    @Test
    fun `normal restitution dissipates energy and preserves tangent and momentum`() {
        val a = ContactBody("a", Circle(Vec2(100.0, 100.0), 5.0), Vec2(20.0, 7.0), 2.0)
        val b = ContactBody("b", Circle(Vec2(110.0, 100.0), 5.0), Vec2(-10.0, -3.0), 3.0)
        val (afterA, afterB) = CollisionResolver(PhysicsConfig(0.1, 1, restitution = 0.5)).resolve(a, b)
        assertEquals(7.0, afterA.velocity.y, 0.0)
        assertEquals(-3.0, afterB.velocity.y, 0.0)
        assertEquals(10.0, 2.0 * afterA.velocity.x + 3.0 * afterB.velocity.x, 1e-10)
        assertEquals(15.0, afterB.velocity.x - afterA.velocity.x, 1e-10)
        val beforeEnergy = 2.0 * 400.0 + 3.0 * 100.0
        val afterEnergy = 2.0 * afterA.velocity.x * afterA.velocity.x + 3.0 * afterB.velocity.x * afterB.velocity.x
        assertEquals(0.5 * (beforeEnergy - afterEnergy), 0.5 * 1.2 * (1.0 - 0.25) * 900.0, 1e-10)
        assertEquals(afterA to afterB, CollisionResolver(PhysicsConfig(0.1, 1)).resolve(afterA, afterB))
    }

    @Test
    fun `exact simultaneous contacts conserve energy and remain separated`() {
        val input = board()
        val discs =
            listOf(-6.0, 6.0).mapIndexed { i, y ->
                Disc("d$i", DiscKind.BLACK, Circle(Vec2(130.0, 100.0 + y), 5.0))
            }
        val initial = withDiscs(input, discs)
        val simulator = MotionSimulator(PhysicsConfig(0.3, 1))
        val final = simulator.simulate(initial).last().boardState
        assertEquals(final, simulator.simulate(initial).last().boardState)
        val velocities = final.discs.map { it.velocity } + final.striker.velocity
        assertEquals(100.0, velocities.sumOf { it.x }, 1e-7)
        assertEquals(0.0, velocities.sumOf { it.y }, 1e-7)
        assertEquals(10000.0, velocities.sumOf { it.x * it.x + it.y * it.y }, 1e-7)
        assertSeparated(final)
    }

    @Test
    fun `separation violating board bounds fails without changing input`() {
        val initial = board()
        val circle = Circle(Vec2(5.0, 100.0), 5.0)
        val input =
            BoardState(
                initial.bounds,
                emptyList(),
                listOf(initial.discs.single().copy(circle = circle)),
                initial.striker.copy(circle = circle, velocity = Vec2(0.0, 0.0)),
            )
        assertThrows(IllegalArgumentException::class.java) { run(input) }
        assertEquals(circle, input.striker.circle)
        assertEquals(circle, input.discs.single().circle)
    }

    private fun board(): BoardState =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(1000.0, 1000.0)),
            emptyList(),
            listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(130.0, 100.0), 5.0))),
            Striker("s", Circle(Vec2(100.0, 100.0), 5.0), Vec2(100.0, 0.0)),
        )

    @Test
    fun `sweep transfers velocity to stationary target without tunneling`() {
        val initial = board()
        val final = MotionSimulator(PhysicsConfig(0.5, 1)).simulate(initial).last().boardState
        assertEquals(120.0, final.striker.circle.center.x, 1e-9)
        assertEquals(
            160.0,
            final.discs
                .single()
                .circle.center.x,
            1e-9,
        )
        assertEquals(0.0, final.striker.velocity.x, 1e-9)
        assertEquals(
            100.0,
            final.discs
                .single()
                .velocity.x,
            1e-9,
        )
        assertEquals(100.0, initial.striker.circle.center.x, 0.0)
    }
}

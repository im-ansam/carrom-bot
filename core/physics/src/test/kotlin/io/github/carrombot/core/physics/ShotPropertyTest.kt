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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties
import java.util.Random

class ShotPropertyTest {
    @Test
    fun `seeded lossy shots preserve bounds separation and nonincreasing energy`() {
        val properties =
            Properties().apply {
                val stream = ShotPropertyTest::class.java.getResourceAsStream("/shot-properties-v1.properties")
                requireNotNull(stream).use(::load)
            }
        val random = Random(properties.getProperty("seed").toLong())
        val tolerance = properties.getProperty("tolerance").toDouble()
        repeat(properties.getProperty("cases").toInt()) { index ->
            val board = syntheticBoard()
            val velocity = Vec2(100.0 + random.nextDouble() * 300.0, random.nextDouble() * 300.0 - 150.0)
            val config =
                PhysicsConfig(
                    0.02,
                    100,
                    deceleration = 5.0 + random.nextDouble() * 20.0,
                    restitution = random.nextDouble(),
                    wallRestitution = random.nextDouble(),
                    wallTangentialRestitution = random.nextDouble(),
                )
            val result = ShotSimulator().simulate(board, ShotInput(board, velocity), config)
            var energy = squaredSpeed(velocity)
            // Frame zero is the pre-launch input, so energy checks begin with the first moved frame.
            result.prediction.frames.drop(1).forEach { frame ->
                val state = frame.boardState
                val circles = state.discs.map { it.circle } + state.striker.circle
                circles.forEach { assertTrue("case $index containment", state.bounds.contains(it)) }
                circles.forEachIndexed { i, a ->
                    circles.drop(i + 1).forEach { b ->
                        assertTrue(
                            "case $index separation",
                            kotlin.math.hypot(a.center.x - b.center.x, a.center.y - b.center.y) + tolerance >=
                                a.radius + b.radius,
                        )
                    }
                }
                val next = state.discs.sumOf { squaredSpeed(it.velocity) } + squaredSpeed(state.striker.velocity)
                assertTrue("case $index energy $next <= $energy", next <= energy + tolerance)
                energy = next
            }
            val reordered = BoardState(board.bounds, board.pockets, board.discs.reversed(), board.striker)
            val repeated = ShotSimulator().simulate(reordered, ShotInput(reordered, velocity), config)
            assertEquals("case $index events", result.events, repeated.events)
            assertEquals(result.finalState.striker, repeated.finalState.striker)
            assertEquals(result.finalState.discs.sortedBy { it.id }, repeated.finalState.discs.sortedBy { it.id })
        }
    }

    private fun squaredSpeed(v: Vec2): Double = v.x * v.x + v.y * v.y

    private fun syntheticBoard(): BoardState =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            emptyList(),
            listOf(
                Disc("a", DiscKind.BLACK, Circle(Vec2(60.0, 30.0), 5.0)),
                Disc("b", DiscKind.WHITE, Circle(Vec2(60.0, 50.0), 5.0)),
                Disc("c", DiscKind.QUEEN, Circle(Vec2(60.0, 70.0), 5.0)),
            ),
            Striker("s", Circle(Vec2(20.0, 50.0), 5.0)),
        )
}

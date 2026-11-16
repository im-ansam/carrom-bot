package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.ShotEventType
import io.github.carrombot.core.physics.ShotSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Original stationary split-v1 fixture; derivation and units in test-fixtures/end-to-end. */
class PhaseOneAcceptanceTest {
    private val root = sqrt(3.0)
    private val firstContactX = 40.0 - 5.0 * root
    private val second = Vec2(firstContactX + 15.0, 50.0 - 15.0 * root)
    private val board =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(
                Pocket("p", Circle(Vec2(40.0 + 20.0 * root, 75.0), 10.0)),
                Pocket("q", Circle(Vec2(second.x + 10.0, second.y - 10.0 * root), 10.0)),
            ),
            listOf(
                Disc("a", DiscKind.BLACK, Circle(Vec2(40.0, 55.0), 5.0)),
                Disc("b", DiscKind.WHITE, Circle(second, 5.0)),
            ),
            Striker("s", Circle(Vec2(20.0, 50.0), 5.0)),
        )
    private val candidate =
        CandidateShot(
            "stationary-split-v1",
            ShotType.MULTI_POCKET,
            ShotInput(board, Vec2(100.0, 0.0)),
            0.5,
            listOf(PocketTarget("a", "p"), PocketTarget("b", "q")),
        )
    private val policy = PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(1.0, 0.0, 0.0))
    private val physics = PhysicsConfig(0.1, 200, deceleration = 1.0)

    @Test
    fun `one launch on a stationary board captures two discs in distinct pockets`() {
        assertTrue(board.discs.all { it.velocity == Vec2(0.0, 0.0) })
        val result = ShotSimulator().simulate(board, candidate.shot, physics)
        assertTrue(ShotEvaluation.fromSimulation(candidate, result, policy).accepted)
        assertFalse(result.strikerCaptured)
        assertTrue(result.finalState.discs.isEmpty())
        assertEquals(listOf("a", "b"), result.captures.map { it.bodyId })
        assertEquals(listOf("p", "q"), result.captures.map { it.pocketId })
        val contacts = result.events.filter { it.type == ShotEventType.BODY_CONTACT }
        assertEquals(listOf(setOf("a", "s"), setOf("b", "s")), contacts.map { setOf(it.bodyId, it.target) })
        assertEquals(4, result.events.size)
        val distance = firstContactX - 20.0
        val impactSpeed = sqrt(10000.0 - 2.0 * distance)
        val firstTime = travelTime(100.0, distance)
        val secondTime = firstTime + travelTime(impactSpeed / 2.0, 20.0)
        val secondSpeed = sqrt(impactSpeed * impactSpeed / 4.0 - 40.0)
        val expected =
            listOf(
                firstTime,
                secondTime,
                firstTime + travelTime(impactSpeed * root / 2.0, 35.0),
                secondTime + travelTime(secondSpeed, 15.0),
            )
        expected.zip(result.events).forEach { (time, event) -> assertEquals(time, event.elapsedSeconds, 1e-7) }
    }

    @Test
    fun `stationary split repeats and preserves event evidence at a finer clock`() {
        val simulator = ShotSimulator()
        val coarse = simulator.simulate(board, candidate.shot, physics)
        assertEquals(coarse.events, simulator.simulate(board, candidate.shot, physics).events)
        val fine = simulator.simulate(board, candidate.shot, physics.copy(timeStepSeconds = 0.01, maxSteps = 2000))
        assertTrue(ShotEvaluation.fromSimulation(candidate, fine, policy).accepted)
        assertEquals(coarse.events.size, fine.events.size)
        coarse.events.zip(fine.events).forEach { (a, b) ->
            assertEquals(a.copy(elapsedSeconds = b.elapsedSeconds), b)
            assertEquals(a.elapsedSeconds, b.elapsedSeconds, 1e-7)
        }
    }

    @Test
    fun `partial stationary split is rejected rather than presented as multi pocket success`() {
        val partial = ShotSimulator().simulate(board, candidate.shot, physics.copy(maxSteps = 6))
        assertEquals(listOf("a"), partial.captures.map { it.bodyId })
        val evaluation = ShotEvaluation.fromSimulation(candidate, partial, policy)
        assertFalse(evaluation.accepted)
        assertTrue(evaluation.rejections.contains(RejectionReason.TARGET_NOT_CAPTURED))
    }

    private fun travelTime(
        speed: Double,
        distance: Double,
    ): Double = 2.0 * distance / (speed + sqrt(speed * speed - 2.0 * distance))
}

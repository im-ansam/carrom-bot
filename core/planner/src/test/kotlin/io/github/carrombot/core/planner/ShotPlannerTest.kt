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
import io.github.carrombot.core.physics.PowerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotPlannerTest {
    private val board =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))),
            listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(60.0, 50.0), 5.0))),
            Striker("s", Circle(Vec2(30.0, 50.0), 5.0)),
        )
    private val policy = PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(2.0, 1.0, 1.0))
    private val physics = PhysicsConfig(0.1, 200, deceleration = 10.0)

    @Test
    fun `power sweep retains misses and ranks lowest successful power first`() {
        val result = plan(listOf(sample("high", 1.0), sample("low", 0.1), sample("middle", 0.5)))
        assertEquals(listOf("middle", "high"), result.plans.map { it.candidate.id })
        assertEquals(listOf("low"), result.rejected.map { it.candidate.id })
        assertTrue(result.plans.all { it.simulation?.captures?.any { c -> c.bodyId == "d" } == true })
        assertEquals(result.plans.first(), result.best)
        assertEquals(
            result.plans.map {
                it.candidate
            },
            plan(listOf(sample("high", 1.0), sample("low", 0.1), sample("middle", 0.5))).plans.map { it.candidate },
        )
    }

    @Test
    fun `empty and unsuccessful searches return no best plan`() {
        assertEquals(null, plan(emptyList()).best)
        assertEquals(null, plan(listOf(sample("weak", 0.1))).best)
    }

    @Test
    fun `duplicate IDs illegal placements and exhausted search budget fail`() {
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(sample("x", 0.5), sample("x", 1.0))) }
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(sample("x", 0.5).copy(position = Vec2(20.0, 20.0))))
        }
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(sample("x", 0.5)), 0) }
    }

    @Test
    fun `optimizer recalibrates velocity and keeps fresh simulated evidence`() {
        val template = candidate("template", Vec2(7.0, 0.0))
        val result =
            PowerOptimizer().optimize(
                template,
                listOf(PowerSample("weak", 0.1), PowerSample("best", 0.5), PowerSample("strong", 1.0)),
                PowerModel(200.0),
                policy,
                physics,
                3,
            )
        assertEquals(listOf("best", "strong"), result.plans.map { it.candidate.id })
        val best = requireNotNull(result.best)
        assertEquals(Vec2(100.0, 0.0), best.candidate.shot.initialVelocity)
        assertEquals(best.candidate.shot, requireNotNull(best.simulation).prediction.shot)
        assertEquals(template.targets, best.candidate.targets)
        assertEquals(Vec2(7.0, 0.0), template.shot.initialVelocity)
        assertThrows(UnsupportedOperationException::class.java) { (result.plans as MutableList).clear() }
        assertThrows(IllegalArgumentException::class.java) {
            PowerOptimizer().optimize(
                template,
                listOf(PowerSample("x", 0.5), PowerSample("x", 0.8)),
                PowerModel(200.0),
                policy,
                physics,
                2,
            )
        }
    }

    @Test
    fun `golden direct and bank utility ranks reward before power and identity`() {
        val candidates =
            listOf(
                candidate("bank", Vec2(-100.0, 0.0)),
                candidate("z", Vec2(100.0, 0.0)),
                candidate("a", Vec2(100.0, 0.0)),
            )
        val result = ShotPlanner().evaluate(candidates, policy, physics, 3)
        assertEquals(listOf("a", "z", "bank"), result.plans.map { it.candidate.id })
        assertEquals(listOf(2.0, 2.0, 1.0), result.plans.map { requireNotNull(it.score).total })
    }

    @Test
    fun `collision and terminal control penalties expose independently known terms`() {
        val config = PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(10.0, 0.0, 0.0, 2.0, 0.1), Vec2(30.0, 50.0))
        val evaluation = CandidateEvaluator().evaluate(candidate("direct", Vec2(100.0, 0.0)), config, physics)
        val score = requireNotNull(evaluation.score)
        assertEquals(2.0, score.bodyContactPenalty, 0.0)
        assertEquals(2.0, score.controlDistancePenalty, 1e-7)
        assertEquals(6.0, score.total, 1e-7)
        assertThrows(IllegalArgumentException::class.java) {
            PlannerConfig(policy.powerBounds, policy.weights.copy(controlDistancePenalty = 1.0))
        }
    }

    @Test
    fun `fouls truncation and blocked target remain diagnostic rejections`() {
        val foulBoard =
            BoardState(
                board.bounds,
                board.pockets + Pocket("foul", Circle(Vec2(30.0, 50.0), 10.0)),
                board.discs,
                board.striker,
            )
        val foul =
            CandidateShot(
                "foul",
                ShotType.DIRECT,
                ShotInput(foulBoard, Vec2(100.0, 0.0)),
                0.5,
                listOf(PocketTarget("d", "p")),
            )
        val result = ShotPlanner().evaluate(listOf(foul), policy, physics, 1)
        assertEquals(null, result.best)
        assertTrue(
            result.rejected
                .single()
                .rejections
                .contains(RejectionReason.STRIKER_CAPTURED),
        )
        val truncated =
            CandidateEvaluator().evaluate(
                candidate = candidate("short", Vec2(100.0, 0.0)),
                config = policy,
                physics = PhysicsConfig(0.01, 1),
            )
        assertTrue(truncated.rejections.contains(RejectionReason.TIME_LIMIT))
        val blockedBoard =
            BoardState(
                board.bounds,
                board.pockets,
                board.discs + Disc("block", DiscKind.WHITE, Circle(Vec2(45.0, 50.0), 5.0)),
                board.striker,
            )
        val blocked =
            CandidateShot(
                "blocked",
                ShotType.DIRECT,
                ShotInput(blockedBoard, Vec2(20.0, 0.0)),
                0.1,
                listOf(PocketTarget("d", "p")),
            )
        val blockedResult = CandidateEvaluator().evaluate(blocked, policy, physics)
        assertTrue(blockedResult.rejections.contains(RejectionReason.TARGET_NOT_CAPTURED))
    }

    @Test
    fun `sample angles and legal placements are used without mutating original board`() {
        val sample = LaunchSample("up", Vec2(20.0, 20.0), -Math.PI / 2.0, 0.1)
        val result =
            ShotPlanner().plan(
                board,
                ShotType.CUT,
                listOf(PocketTarget("d", "p")),
                listOf(sample),
                listOf(sample.position),
                PowerModel(200.0),
                policy,
                physics,
                1,
            )
        val input =
            result.rejected
                .single()
                .candidate.shot
        assertEquals(sample.position, input.boardState.striker.circle.center)
        assertEquals(-20.0, input.initialVelocity.y, 1e-12)
        assertEquals(0.0, input.initialVelocity.x, 1e-12)
        assertEquals(Vec2(30.0, 50.0), board.striker.circle.center)
    }

    @Test
    fun `invalid numeric samples and simulation work failures propagate`() {
        for (power in listOf(0.0, -1.0, Double.NaN, 1.1)) {
            assertThrows(IllegalArgumentException::class.java) { sample("bad", power) }
            assertThrows(IllegalArgumentException::class.java) { PowerSample("bad", power) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CandidateEvaluator().evaluate(
                candidate("budget", Vec2(100.0, 0.0)),
                policy,
                PhysicsConfig(1.0, 2, maxCollisionIterations = 1),
            )
        }
    }

    private fun candidate(
        id: String,
        velocity: Vec2,
    ): CandidateShot =
        CandidateShot(
            id = id,
            type = ShotType.DIRECT,
            shot = ShotInput(board, velocity),
            power = 0.5,
            targets = listOf(PocketTarget("d", "p")),
        )

    private fun sample(
        id: String,
        power: Double,
    ): LaunchSample = LaunchSample(id, Vec2(30.0, 50.0), 0.0, power)

    private fun plan(
        samples: List<LaunchSample>,
        budget: Int = 10,
    ): PlanningResult =
        ShotPlanner().plan(
            board,
            ShotType.DIRECT,
            listOf(PocketTarget("d", "p")),
            samples,
            listOf(Vec2(30.0, 50.0)),
            PowerModel(200.0),
            policy,
            physics,
            budget,
        )
}

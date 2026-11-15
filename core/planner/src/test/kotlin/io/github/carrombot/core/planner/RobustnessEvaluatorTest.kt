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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustnessEvaluatorTest {
    private val physics = PhysicsConfig(0.1, 200, deceleration = 10.0)
    private val config = PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(2.0, 0.0, 0.0))
    private val board =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0)), Pocket("q", Circle(Vec2(100.0, 80.0), 15.0))),
            listOf(
                Disc("d", DiscKind.BLACK, Circle(Vec2(60.0, 50.0), 5.0)),
                Disc("e", DiscKind.WHITE, Circle(Vec2(70.0, 80.0), 5.0), Vec2(100.0, 0.0)),
            ),
            Striker("s", Circle(Vec2(30.0, 50.0), 5.0)),
        )

    @Test
    fun `multi pocket reward selects both captures with full evidence`() {
        val result =
            MultiPocketEvaluator().evaluate(
                listOf(candidate("single", false), candidate("multi", true)),
                config,
                physics,
                listOf(UncertaintyScenario("base", physics)),
                1.0,
                4,
            )
        assertEquals(
            "multi",
            result.preferred
                ?.nominal
                ?.candidate
                ?.id,
        )
        assertEquals(
            4.0,
            result.preferred
                ?.nominal
                ?.score
                ?.total,
        )
        assertEquals(
            2,
            result.preferred
                ?.nominal
                ?.simulation
                ?.captures
                ?.size,
        )
    }

    @Test
    fun `fragile higher reward exposes uncertainty and safer single target fallback`() {
        val offset = PositionOffset("q", Vec2(0.0, -20.0))
        val scenarios = listOf(UncertaintyScenario("detection", physics, offsets = listOf(offset)))
        val result =
            MultiPocketEvaluator().evaluate(
                listOf(candidate("multi", true), candidate("single", false)),
                config,
                physics,
                scenarios,
                1.0,
                4,
            )
        assertTrue(requireNotNull(result.preferred).uncertain)
        assertEquals(0.0, requireNotNull(result.preferred).confidence, 0.0)
        assertEquals(
            "single",
            result.saferFallback
                ?.nominal
                ?.candidate
                ?.id,
        )
        assertEquals(1.0, requireNotNull(result.saferFallback).confidence, 0.0)
    }

    @Test
    fun `explicit parameter samples repeat and retain failed power evidence`() {
        val scenarios =
            listOf(
                UncertaintyScenario("drag", physics.copy(deceleration = 1000.0)),
                UncertaintyScenario(
                    id = "restitution",
                    physics = physics.copy(restitution = 0.8, wallRestitution = 0.7, wallFriction = 0.2),
                ),
                UncertaintyScenario("power", physics, powerScale = 0.2),
            )
        val evaluator = RobustnessEvaluator()
        val first = evaluator.evaluate(candidate("x", false), config, physics, scenarios, 1.0, 4)
        val second = evaluator.evaluate(candidate("x", false), config, physics, scenarios, 1.0, 4)
        assertEquals(first.confidence, second.confidence, 0.0)
        assertEquals(first.scenarios.map { it.evaluation.candidate }, second.scenarios.map { it.evaluation.candidate })
        val firstEvents = first.scenarios.map { it.evaluation.simulation?.events }
        assertEquals(firstEvents, second.scenarios.map { it.evaluation.simulation?.events })
        assertEquals(scenarios.map { it.physics }, first.scenarios.map { it.scenario.physics })
        assertTrue(
            first.scenarios
                .first()
                .evaluation.rejections
                .contains(RejectionReason.TARGET_NOT_CAPTURED),
        )
        assertEquals(
            0.1,
            first.scenarios
                .last()
                .evaluation.candidate.power,
            1e-12,
        )
        assertThrows(UnsupportedOperationException::class.java) { (first.scenarios as MutableList).clear() }
    }

    @Test
    fun `empty duplicate invalid and over budget scenarios fail explicitly`() {
        val scenario = UncertaintyScenario("x", physics)
        for (scenarios in listOf(emptyList(), listOf(scenario, scenario))) {
            assertThrows(IllegalArgumentException::class.java) {
                RobustnessEvaluator().evaluate(candidate("x", false), config, physics, scenarios, 1.0, 4)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RobustnessEvaluator().evaluate(candidate("x", false), config, physics, listOf(scenario), 1.0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UncertaintyScenario(id = "x", physics = physics, powerScale = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RobustnessEvaluator().evaluate(
                candidate("x", false),
                config,
                physics,
                listOf(UncertaintyScenario("x", physics, offsets = listOf(PositionOffset("missing", Vec2(1.0, 0.0))))),
                1.0,
                2,
            )
        }
    }

    @Test
    fun `fraction threshold is inclusive and nominal rejection never becomes preferred`() {
        val scenarios =
            listOf(
                UncertaintyScenario("base", physics),
                UncertaintyScenario("weak", physics, powerScale = 0.2),
            )
        val evaluator = RobustnessEvaluator()
        val proposal = candidate("x", false)
        val inclusive = evaluator.evaluate(proposal, config, physics, scenarios, 0.5, 3)
        assertEquals(0.5, inclusive.confidence, 0.0)
        assertEquals(false, inclusive.uncertain)
        assertEquals(true, evaluator.evaluate(proposal, config, physics, scenarios, 0.51, 3).uncertain)
        val truncated = physics.copy(maxSteps = 1)
        val rejected = MultiPocketEvaluator().evaluate(listOf(proposal), config, truncated, scenarios, 0.0, 3)
        assertEquals(null, rejected.preferred)
        assertEquals(null, rejected.saferFallback)
        assertTrue(rejected.evaluations.single().uncertain)
        assertEquals(null, MultiPocketEvaluator().evaluate(emptyList(), config, physics, scenarios, 1.0, 1).preferred)
    }

    @Test
    fun `global budget duplicate candidate and simulation failure propagate`() {
        val proposals = listOf(candidate("a", false), candidate("b", true))
        val scenarios = listOf(UncertaintyScenario("base", physics))
        assertThrows(IllegalArgumentException::class.java) {
            MultiPocketEvaluator().evaluate(proposals, config, physics, scenarios, 1.0, 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val duplicates = listOf(proposals.first(), proposals.first())
            MultiPocketEvaluator().evaluate(duplicates, config, physics, scenarios, 1.0, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RobustnessEvaluator().evaluate(
                proposals.first(),
                config,
                physics,
                listOf(UncertaintyScenario("budget", PhysicsConfig(1.0, 2, maxCollisionIterations = 1))),
                1.0,
                2,
            )
        }
    }

    @Test
    fun `offset ownership unknown geometry and overflowing power fail without clamping`() {
        val offsets = mutableListOf(PositionOffset("d", Vec2(0.0, 1.0)))
        val scenario = UncertaintyScenario("owned", physics, offsets = offsets)
        offsets.clear()
        assertEquals(1, scenario.offsets.size)
        assertThrows(UnsupportedOperationException::class.java) { (scenario.offsets as MutableList).clear() }
        for (invalid in listOf(
            UncertaintyScenario("outside", physics, offsets = listOf(PositionOffset("d", Vec2(100.0, 0.0)))),
            UncertaintyScenario("power", physics, powerScale = 3.0),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                RobustnessEvaluator().evaluate(candidate("x", false), config, physics, listOf(invalid), 1.0, 2)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            val duplicates = listOf(scenario.offsets.single(), scenario.offsets.single())
            UncertaintyScenario("duplicate", physics, offsets = duplicates)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RobustnessEvaluator().evaluate(candidate("x", false), config, physics, listOf(scenario), Double.NaN, 2)
        }
    }

    private fun candidate(
        id: String,
        multi: Boolean,
    ): CandidateShot =
        CandidateShot(
            id,
            if (multi) ShotType.MULTI_POCKET else ShotType.DIRECT,
            ShotInput(board, Vec2(100.0, 0.0)),
            0.5,
            if (multi) listOf(PocketTarget("d", "p"), PocketTarget("e", "q")) else listOf(PocketTarget("d", "p")),
        )
}

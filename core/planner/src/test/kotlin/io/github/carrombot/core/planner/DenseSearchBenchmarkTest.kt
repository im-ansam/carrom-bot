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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic 19-disc workload, diagnostic timing only; no machine-dependent latency assertion. */
class DenseSearchBenchmarkTest {
    @Test
    fun `dense board search reports work timing and cooperative deadline`() =
        runBlocking {
            val discs =
                (0 until 19).map { index ->
                    Disc(
                        "d$index",
                        DiscKind.BLACK,
                        Circle(Vec2(300.0 + index % 5 * 22.0, 300.0 + index / 5 * 22.0), 8.0),
                    )
                }
            val board =
                BoardState(
                    BoardBounds(Vec2(0.0, 0.0), Vec2(1000.0, 1000.0)),
                    listOf(Pocket("p", Circle(Vec2(1000.0, 500.0), 30.0))),
                    discs,
                    Striker("s", Circle(Vec2(200.0, 330.0), 10.0)),
                )
            val candidates =
                (0 until 64).map { index ->
                    CandidateShot(
                        "sample-$index",
                        ShotType.DIRECT,
                        ShotInput(board, Vec2(100.0 + index, 0.0)),
                        0.5,
                        listOf(PocketTarget("d0", "p")),
                    )
                }
            val policy = PlannerConfig(PowerBounds(0.0, 1.0), ScoringWeights(1.0, 1.0, 1.0))
            val physics = PhysicsConfig(0.01, 300, deceleration = 50.0)
            val scheduler = PlannerScheduler()
            scheduler.search(candidates.take(4), policy, physics, SearchBudget(4, 10000))
            val full = scheduler.search(candidates, policy, physics, SearchBudget(64, 30000))
            assertEquals(SearchStopReason.COMPLETE, full.stopReason)
            assertEquals(64, full.metrics.completedSimulations)
            val bounded = scheduler.search(candidates, policy, physics, SearchBudget(64, 5))
            if (bounded.stopReason == SearchStopReason.DEADLINE) {
                assertTrue(bounded.unexaminedIds.isNotEmpty())
            } else {
                assertEquals(SearchStopReason.COMPLETE, bounded.stopReason)
                assertEquals(64, bounded.metrics.completedSimulations)
            }
            println("dense-search-v1 discs=19 proposals=64 full=${full.metrics} deadline5ms=${bounded.metrics}")
        }
}

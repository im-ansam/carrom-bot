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

class SearchDeadlineTest {
    @Test
    fun `deadline keeps completed evidence and excludes active trajectory`() =
        runBlocking {
            val board =
                BoardState(
                    BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
                    listOf(Pocket("p", Circle(Vec2(90.0, 50.0), 10.0))),
                    listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(90.0, 50.0), 5.0))),
                    Striker("s", Circle(Vec2(20.0, 50.0), 5.0)),
                )

            fun candidate(
                id: String,
                speed: Double,
            ) = CandidateShot(
                id,
                ShotType.DIRECT,
                ShotInput(board, Vec2(speed, 0.0)),
                0.5,
                listOf(PocketTarget("d", "p")),
            )
            var reads = 0L
            val result =
                PlannerScheduler { reads++ * 10_000 }.search(
                    listOf(candidate("completed", 0.000001), candidate("active", 1.0)),
                    PlannerConfig(PowerBounds(0.0, 1.0), ScoringWeights(1.0, 0.0, 0.0)),
                    PhysicsConfig(0.01, 100000, deceleration = 0.001),
                    SearchBudget(2, 1),
                )
            assertEquals(SearchStopReason.DEADLINE, result.stopReason)
            assertEquals(
                "completed",
                result.planning.best
                    ?.candidate
                    ?.id,
            )
            assertEquals(listOf("active"), result.unexaminedIds)
            assertEquals(2, result.metrics.startedSimulations)
            assertEquals(1, result.metrics.completedSimulations)
            assertTrue(result.planning.rejected.isEmpty())
        }
}

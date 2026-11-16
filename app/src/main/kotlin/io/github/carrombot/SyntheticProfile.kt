package io.github.carrombot

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.overlay.OverlayScene
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.PowerModel
import io.github.carrombot.core.planner.ContactShotConfig
import io.github.carrombot.core.planner.ContactShotRequest
import io.github.carrombot.core.planner.CutShotGenerator
import io.github.carrombot.core.planner.DirectShotGenerator
import io.github.carrombot.core.planner.PlannerConfig
import io.github.carrombot.core.planner.PlannerScheduler
import io.github.carrombot.core.planner.PocketTarget
import io.github.carrombot.core.planner.PowerBounds
import io.github.carrombot.core.planner.ScoringWeights
import io.github.carrombot.core.planner.SearchBudget
import io.github.carrombot.core.vision.HsvRange
import io.github.carrombot.core.vision.ObjectKind
import io.github.carrombot.core.vision.ObjectProfile
import io.github.carrombot.core.vision.TrackingConfig
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Original synthetic-fixture-v1 settings ONLY. Not measured settings for any third-party game. */
@Suppress("MagicNumber")
internal object SyntheticProfile {
    val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(400.0, 400.0))
    val inventory = mapOf(ObjectKind.POCKET to 1, ObjectKind.BLACK_DISC to 1, ObjectKind.STRIKER to 1)
    val tracking = TrackingConfig(10.0, 1.0, 0.5, 5.0, 250_000_000, 1_500_000_000)
    val profiles =
        listOf(
            ObjectProfile(
                ObjectKind.POCKET,
                HsvRange(45, 75, 180, 255, 180, 255),
                0.07,
                0.095,
                cornerFraction = 0.20,
            ),
            ObjectProfile(ObjectKind.BLACK_DISC, HsvRange(0, 10, 180, 255, 180, 255), 0.045, 0.065),
            ObjectProfile(ObjectKind.STRIKER, HsvRange(105, 125, 180, 255, 180, 255), 0.055, 0.075),
        )
    val physics = PhysicsConfig(0.02, 300, deceleration = 80.0)
    private val power = PowerModel(600.0)
    private val contact = ContactShotConfig(power, 0.5, 2.0)
    private val policy = PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(1.0, 1.0, 0.01))

    suspend fun plan(board: BoardState): OverlayScene {
        val legalPositions = listOf(board.striker.circle.center)
        val candidates =
            board.discs.flatMap { disc ->
                board.pockets.flatMap { pocket ->
                    listOf(0.4, 0.5, 0.6).mapNotNull { launchPower ->
                        coroutineContext.ensureActive()
                        val request =
                            ContactShotRequest(
                                "synthetic-${disc.id}-${pocket.id}-$launchPower",
                                board,
                                PocketTarget(disc.id, pocket.id),
                                legalPositions.single(),
                                pocket.circle.center,
                                launchPower,
                            )
                        (
                            DirectShotGenerator().generate(request, legalPositions, contact)
                                ?: CutShotGenerator().generate(request, legalPositions, contact)
                        )?.candidate
                    }
                }
            }
        val result = PlannerScheduler().search(candidates, policy, physics, SearchBudget(32, 500))
        return PlannerSceneAdapter.scene(result.planning, board.bounds)
    }
}

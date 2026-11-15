package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.PowerModel
import kotlin.math.cos
import kotlin.math.sin

internal const val MAX_SEARCH_SIMULATIONS: Int = 4096

internal fun validateSearchBudget(
    size: Int,
    maximum: Int,
) {
    require(maximum in 1..MAX_SEARCH_SIMULATIONS && size <= maximum) { "Search budget exceeded or invalid" }
}

/** One caller-identified sample: board millimetres, clockwise radians from +X, normalized power. */
public data class LaunchSample(
    public val id: String,
    public val position: Vec2,
    public val angleRadians: Double,
    public val power: Double,
) {
    init {
        require(id.isNotBlank() && angleRadians.isFinite()) { "Invalid sample identity or angle" }
        require(power.isFinite() && power > 0.0 && power <= 1.0) { "Sample power must be in (0,1]" }
    }
}

/** Ranked feasible plans and diagnostic rejections; empty [plans] explicitly means no sampled solution. */
public class PlanningResult internal constructor(
    evaluations: List<ShotEvaluation>,
) {
    public val plans: List<ShotEvaluation> = ownedCopy(evaluations.filter { it.accepted })
    public val rejected: List<ShotEvaluation> = ownedCopy(evaluations.filterNot { it.accepted })
    public val best: ShotEvaluation? get() = plans.firstOrNull()
}

/**
 * Exhaustive evaluation of an explicit finite sample set, never a completeness or global-optimum claim.
 * Legal positions, calibration, target intent and work budget are caller policy. No rules are inferred.
 * Invalid inputs and simulator failures throw; input lists are read synchronously and not retained.
 */
public class ShotPlanner {
    @Suppress("LongParameterList")
    public fun plan(
        board: BoardState,
        type: ShotType,
        targets: List<PocketTarget>,
        samples: List<LaunchSample>,
        legalPositions: List<Vec2>,
        powerModel: PowerModel,
        config: PlannerConfig,
        physics: PhysicsConfig,
        maxSimulations: Int,
        constraints: TrajectoryConstraints = TrajectoryConstraints(),
    ): PlanningResult {
        validateSearchBudget(samples.size, maxSimulations)
        require(samples.map { it.id }.distinct().size == samples.size) { "Sample IDs must be unique" }
        val candidates =
            samples.map { sample ->
                require(sample.position in legalPositions) { "Sample placement is not legal" }
                val striker = board.striker.copy(circle = board.striker.circle.copy(center = sample.position))
                require(
                    board.discs.all {
                        kotlin.math.hypot(
                            it.circle.center.x - sample.position.x,
                            it.circle.center.y - sample.position.y,
                        ) >
                            it.circle.radius + striker.circle.radius
                    },
                ) { "Sample placement overlaps or touches a disc" }
                val positioned = BoardState(board.bounds, board.pockets, board.discs, striker)
                val speed = powerModel.speedForPower(sample.power)
                CandidateShot(
                    sample.id,
                    type,
                    ShotInput(positioned, Vec2(cos(sample.angleRadians) * speed, sin(sample.angleRadians) * speed)),
                    sample.power,
                    targets,
                    constraints,
                )
            }
        return evaluate(candidates, config, physics, maxSimulations)
    }

    /** Evaluates existing generator proposals without changing their launch or ordered route claims. */
    public fun evaluate(
        candidates: List<CandidateShot>,
        config: PlannerConfig,
        physics: PhysicsConfig,
        maxSimulations: Int,
    ): PlanningResult {
        validateSearchBudget(candidates.size, maxSimulations)
        require(candidates.map { it.id }.distinct().size == candidates.size) { "Candidate IDs must be unique" }
        return PlanningResult(
            candidates
                .map { CandidateEvaluator().evaluate(it, config, physics) }
                .sortedWith(ShotEvaluation.RANKING),
        )
    }
}

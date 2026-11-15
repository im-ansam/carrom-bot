package io.github.carrombot.core.planner

import io.github.carrombot.core.model.Vec2

/** Inclusive admissible normalized power; zero may be a bound but cannot launch a CandidateShot. */
public data class PowerBounds(
    public val minimum: Double,
    public val maximum: Double,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum in 0.0..maximum && maximum <= 1.0) {
            "Power bounds must be ordered within [0,1]"
        }
    }
}

/**
 * Caller-selected nonnegative utility per target, incidental capture, wall/body impulse and control distance.
 * Control distance is terminal striker-centre distance in mm to [PlannerConfig.controlTarget].
 * The optional new penalties default to zero to preserve the CB-25 scoring policy.
 * Utility is advisory, not player points, win probability, queen-cover rules or calibrated confidence.
 */
public data class ScoringWeights(
    public val targetReward: Double,
    public val incidentalCapturePenalty: Double,
    public val wallContactPenalty: Double,
    public val bodyContactPenalty: Double = 0.0,
    public val controlDistancePenalty: Double = 0.0,
) {
    init {
        require(
            listOf(
                targetReward,
                incidentalCapturePenalty,
                wallContactPenalty,
                bodyContactPenalty,
                controlDistancePenalty,
            ).all { it.isFinite() && it >= 0.0 },
        ) {
            "Weights must be finite and nonnegative"
        }
    }
}

/** Evaluation policy only; physics configuration and simulation work budgets stay explicit at the call site. */
public data class PlannerConfig(
    public val powerBounds: PowerBounds,
    public val weights: ScoringWeights,
    public val controlTarget: Vec2? = null,
) {
    init {
        require(controlTarget != null || weights.controlDistancePenalty == 0.0) { "Control penalty requires a target" }
    }
}

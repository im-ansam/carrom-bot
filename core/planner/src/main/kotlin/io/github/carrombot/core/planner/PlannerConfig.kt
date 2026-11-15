package io.github.carrombot.core.planner

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
 * Caller-selected nonnegative utility per target, incidental disc capture and wall impulse.
 * Utility is advisory, not player points, win probability, queen-cover rules or calibrated confidence.
 */
public data class ScoringWeights(
    public val targetReward: Double,
    public val incidentalCapturePenalty: Double,
    public val wallContactPenalty: Double,
) {
    init {
        require(listOf(targetReward, incidentalCapturePenalty, wallContactPenalty).all { it.isFinite() && it >= 0.0 }) {
            "Weights must be finite and nonnegative"
        }
    }
}

/** Evaluation policy only; physics configuration and simulation work budgets stay explicit at the call site. */
public data class PlannerConfig(
    public val powerBounds: PowerBounds,
    public val weights: ScoringWeights,
)

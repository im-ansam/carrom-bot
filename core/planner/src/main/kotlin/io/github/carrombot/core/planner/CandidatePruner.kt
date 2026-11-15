package io.github.carrombot.core.planner

/** Proven policy-only pruning; no geometric approximation or score bound discards feasible shots. */
public class CandidatePruner {
    public fun reason(
        candidate: CandidateShot,
        config: PlannerConfig,
    ): PruningReason? =
        if (candidate.power !in config.powerBounds.minimum..config.powerBounds.maximum) {
            PruningReason.POWER_OUT_OF_BOUNDS
        } else {
            null
        }
}

public enum class PruningReason {
    POWER_OUT_OF_BOUNDS,
}

/** Pruned proposals have no simulated evidence and are separate from completed rejections. */
public data class PrunedCandidate(
    public val candidate: CandidateShot,
    public val reason: PruningReason,
)

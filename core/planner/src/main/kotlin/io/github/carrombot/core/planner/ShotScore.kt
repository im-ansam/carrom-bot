package io.github.carrombot.core.planner

import io.github.carrombot.core.model.PredictionTermination
import io.github.carrombot.core.physics.ShotEventType
import io.github.carrombot.core.physics.SimulationResult

/** Finite signed utility terms; penalties are nonnegative and subtracted from reward. Overflow fails explicitly. */
public data class ShotScore(
    public val targetReward: Double,
    public val incidentalCapturePenalty: Double,
    public val wallContactPenalty: Double,
) {
    public val total: Double = targetReward - incidentalCapturePenalty - wallContactPenalty

    init {
        require(listOf(targetReward, incidentalCapturePenalty, wallContactPenalty).all { it.isFinite() && it >= 0.0 }) {
            "Score terms must be finite and nonnegative"
        }
        require(total.isFinite()) { "Score overflow" }
    }
}

/** Stable rejection codes, independent of localized explanations or exception messages. */
public enum class RejectionReason {
    POWER_OUT_OF_BOUNDS,
    TIME_LIMIT,
    STRIKER_CAPTURED,
    TARGET_NOT_CAPTURED,
    TRAJECTORY_CONSTRAINT,
    SIMULATION_FAILED,
}

/**
 * Owned evaluation of exactly one proposal. Only accepted, completed simulations carry scores.
 * A rejected completed simulation is retained for diagnostics; failures carry neither simulation nor score.
 * This boundary never catches exceptions, including cancellation. Callers may report a known simulation
 * failure with [simulationFailed]; they must propagate cancellation rather than convert it to a rejection.
 */
public class ShotEvaluation private constructor(
    public val candidate: CandidateShot,
    public val simulation: SimulationResult?,
    public val score: ShotScore?,
    rejections: List<RejectionReason>,
) {
    public val rejections: List<RejectionReason> = ownedCopy(rejections)
    public val accepted: Boolean get() = rejections.isEmpty()

    public companion object {
        /**
         * Accepted first, descending total utility, ascending power, then lexicographic caller ID.
         * Rejected candidates sort by power then ID. IDs must be unique within a ranking batch;
         * identical IDs compare equal and must not be used as a set key to deduplicate proposals.
         */
        public val RANKING: Comparator<ShotEvaluation> =
            compareByDescending<ShotEvaluation> { it.accepted }
                .thenByDescending { it.score?.total }
                .thenBy { it.candidate.power }
                .thenBy { it.candidate.id }

        public fun simulationFailed(candidate: CandidateShot): ShotEvaluation =
            ShotEvaluation(candidate, null, null, listOf(RejectionReason.SIMULATION_FAILED))

        /** Rejects truncation, fouls, missed targets and violated bounds; never infers captures from removal. */
        public fun fromSimulation(
            candidate: CandidateShot,
            simulation: SimulationResult,
            config: PlannerConfig,
        ): ShotEvaluation {
            require(simulation.prediction.shot == candidate.shot) { "Simulation belongs to a different shot" }
            val walls = simulation.events.count { it.type == ShotEventType.WALL_CONTACT }
            val capturedTargets =
                candidate.targets.count { target ->
                    simulation.captures.any {
                        it.bodyId == target.discId && it.pocketId == target.pocketId && it.kind != null
                    }
                }
            val reasons = rejectionReasons(candidate, simulation, config, capturedTargets)
            val score =
                if (reasons.isEmpty()) {
                    val incidental = simulation.captures.count { it.kind != null } - capturedTargets
                    ShotScore(
                        capturedTargets * config.weights.targetReward,
                        incidental * config.weights.incidentalCapturePenalty,
                        walls * config.weights.wallContactPenalty,
                    )
                } else {
                    null
                }
            return ShotEvaluation(candidate, simulation, score, reasons)
        }
    }
}

private fun rejectionReasons(
    candidate: CandidateShot,
    simulation: SimulationResult,
    config: PlannerConfig,
    capturedTargets: Int,
): List<RejectionReason> =
    buildList {
        if (candidate.power !in config.powerBounds.minimum..config.powerBounds.maximum) {
            add(RejectionReason.POWER_OUT_OF_BOUNDS)
        }
        if (simulation.prediction.termination == PredictionTermination.TIME_LIMIT) add(RejectionReason.TIME_LIMIT)
        if (simulation.strikerCaptured) add(RejectionReason.STRIKER_CAPTURED)
        if (capturedTargets != candidate.targets.size) add(RejectionReason.TARGET_NOT_CAPTURED)
        val constraints = candidate.constraints
        val walls = simulation.events.count { it.type == ShotEventType.WALL_CONTACT }
        val bodies = simulation.events.count { it.type == ShotEventType.BODY_CONTACT }
        if (walls !in constraints.minWallContacts..constraints.maxWallContacts ||
            bodies !in constraints.minBodyContacts..constraints.maxBodyContacts
        ) {
            add(RejectionReason.TRAJECTORY_CONSTRAINT)
        }
    }

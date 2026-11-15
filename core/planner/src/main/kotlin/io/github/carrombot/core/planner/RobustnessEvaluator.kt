package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig

/** Detection displacement in board mm for one existing disc, striker or pocket. */
public data class PositionOffset(
    public val entityId: String,
    public val delta: Vec2,
) {
    init {
        require(entityId.isNotBlank()) { "Offset identity must not be blank" }
    }
}

/**
 * One explicit equally weighted sensitivity sample, not a random draw or calibrated distribution.
 * Physics supplies drag, body/wall restitution and friction. Positive powerScale multiplies both
 * commanded power and launch velocity, assuming the caller's linear calibration. No values clamp.
 * Offsets translate detected centres only; radii, bounds and existing body motion remain unchanged.
 */
public class UncertaintyScenario(
    public val id: String,
    public val physics: PhysicsConfig,
    public val powerScale: Double = 1.0,
    offsets: List<PositionOffset> = emptyList(),
) {
    public val offsets: List<PositionOffset> = ownedCopy(offsets)

    init {
        require(id.isNotBlank() && powerScale.isFinite() && powerScale > 0.0) { "Invalid scenario" }
        require(
            this.offsets
                .map { it.entityId }
                .distinct()
                .size == this.offsets.size,
        ) { "Duplicate offset identity" }
    }
}

/** Retains sample identity and complete simulated evidence, including hard rejections. */
public data class ScenarioEvaluation(
    public val scenario: UncertaintyScenario,
    public val evaluation: ShotEvaluation,
) {
    public val scenarioId: String get() = scenario.id
}

/**
 * Confidence is the fraction of explicit scenarios accepted, excluding the separate nominal run.
 * It is sensitivity evidence, not a real-game probability or statistical confidence interval.
 * A nominal rejection or a fraction below the caller's threshold always marks the plan uncertain.
 */
public class RobustShotEvaluation internal constructor(
    public val nominal: ShotEvaluation,
    scenarios: List<ScenarioEvaluation>,
    minimumConfidence: Double,
) {
    public val scenarios: List<ScenarioEvaluation> = ownedCopy(scenarios)
    public val confidence: Double = this.scenarios.count { it.evaluation.accepted }.toDouble() / this.scenarios.size
    public val uncertain: Boolean = !nominal.accepted || confidence < minimumConfidence
}

/** Stateless deterministic full simulation of every explicit scenario. All failures propagate. */
public class RobustnessEvaluator {
    @Suppress("LongParameterList")
    public fun evaluate(
        candidate: CandidateShot,
        config: PlannerConfig,
        physics: PhysicsConfig,
        scenarios: List<UncertaintyScenario>,
        minimumConfidence: Double,
        maxSimulations: Int,
    ): RobustShotEvaluation {
        validateScenarios(scenarios, minimumConfidence)
        validateSearchBudget(scenarios.size + 1, maxSimulations)
        val perturbed = scenarios.map { perturb(candidate, it) }
        val evaluator = CandidateEvaluator()
        val nominal = evaluator.evaluate(candidate, config, physics)
        val results =
            scenarios.zip(perturbed) { scenario, proposal ->
                ScenarioEvaluation(scenario, evaluator.evaluate(proposal, config, scenario.physics))
            }
        return RobustShotEvaluation(nominal, results, minimumConfidence)
    }
}

internal fun validateScenarios(
    scenarios: List<UncertaintyScenario>,
    minimumConfidence: Double,
) {
    require(scenarios.isNotEmpty() && scenarios.size < MAX_SEARCH_SIMULATIONS) { "Invalid scenario count" }
    require(scenarios.map { it.id }.distinct().size == scenarios.size) { "Scenario IDs must be unique" }
    require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0) { "Invalid confidence threshold" }
}

private fun perturb(
    candidate: CandidateShot,
    scenario: UncertaintyScenario,
): CandidateShot {
    val board = candidate.shot.boardState
    val offsets = scenario.offsets.associate { it.entityId to it.delta }
    val ids = board.discs.map { it.id } + board.pockets.map { it.id } + board.striker.id
    require(offsets.keys.all { it in ids }) { "Unknown offset identity" }

    fun moved(
        id: String,
        circle: Circle,
    ): Circle {
        val delta = offsets[id] ?: return circle
        return circle.copy(center = Vec2(circle.center.x + delta.x, circle.center.y + delta.y))
    }
    val perturbed =
        BoardState(
            board.bounds,
            board.pockets.map { it.copy(circle = moved(it.id, it.circle)) },
            board.discs.map { it.copy(circle = moved(it.id, it.circle)) },
            board.striker.copy(circle = moved(board.striker.id, board.striker.circle)),
        )
    val velocity = candidate.shot.initialVelocity
    return CandidateShot(
        candidate.id,
        candidate.type,
        ShotInput(perturbed, Vec2(velocity.x * scenario.powerScale, velocity.y * scenario.powerScale)),
        candidate.power * scenario.powerScale,
        candidate.targets,
        candidate.constraints,
    )
}

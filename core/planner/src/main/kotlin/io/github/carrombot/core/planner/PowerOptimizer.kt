package io.github.carrombot.core.planner

import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.PowerModel
import kotlin.math.hypot

/** Caller-owned unique identity for a tested power; powers need not be monotonic or uniformly spaced. */
public data class PowerSample(
    public val id: String,
    public val power: Double,
) {
    init {
        require(id.isNotBlank() && power.isFinite() && power > 0.0 && power <= 1.0) { "Invalid power sample" }
    }
}

/**
 * Discrete full-physics sweep. Preserves placement/direction/intent; no monotonic success assumption.
 * Evaluates captures and coarse contact counts, not the original generator's ordered route proof.
 * Every output contains fresh physical evidence; changed powers can change contact identities/order.
 */
public class PowerOptimizer {
    @Suppress("LongParameterList")
    public fun optimize(
        template: CandidateShot,
        samples: List<PowerSample>,
        powerModel: PowerModel,
        config: PlannerConfig,
        physics: PhysicsConfig,
        maxSimulations: Int,
    ): PlanningResult {
        validateSearchBudget(samples.size, maxSimulations)
        val velocity = template.shot.initialVelocity
        val magnitude = hypot(velocity.x, velocity.y)
        require(magnitude.isFinite() && magnitude > 0.0) { "Unrepresentable launch direction" }
        val candidates =
            samples.map { sample ->
                val speed = powerModel.speedForPower(sample.power)
                val shot =
                    ShotInput(
                        boardState = template.shot.boardState,
                        initialVelocity = Vec2(velocity.x / magnitude * speed, velocity.y / magnitude * speed),
                    )
                CandidateShot(sample.id, template.type, shot, sample.power, template.targets, template.constraints)
            }
        return ShotPlanner().evaluate(candidates, config, physics, maxSimulations)
    }
}

package io.github.carrombot.core.planner

import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.ShotSimulator

/** Runs the complete shared simulator; arithmetic, budget and cancellation failures propagate. */
public class CandidateEvaluator {
    public fun evaluate(
        candidate: CandidateShot,
        config: PlannerConfig,
        physics: PhysicsConfig,
    ): ShotEvaluation =
        ShotEvaluation.fromSimulation(
            candidate,
            ShotSimulator().simulate(candidate.shot.boardState, candidate.shot, physics),
            config,
        )
}

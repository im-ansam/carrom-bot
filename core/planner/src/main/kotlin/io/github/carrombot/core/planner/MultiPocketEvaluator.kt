package io.github.carrombot.core.planner

import io.github.carrombot.core.physics.PhysicsConfig

/** Nominal utility order with sensitivity evidence; rejected proposals remain diagnostic entries. */
public class MultiPocketResult internal constructor(
    evaluations: List<RobustShotEvaluation>,
) {
    public val evaluations: List<RobustShotEvaluation> = ownedCopy(evaluations)
    public val preferred: RobustShotEvaluation? = this.evaluations.firstOrNull { it.nominal.accepted }

    /**
     * Highest-confidence nominally feasible alternative meeting the threshold and strictly improving
     * on an uncertain preferred plan. Ties retain nominal utility order. Null means no tested fallback.
     */
    public val saferFallback: RobustShotEvaluation? =
        preferred?.takeIf { it.uncertain }?.let { primary ->
            this.evaluations
                .filter {
                    it.nominal.accepted && !it.uncertain && it.confidence > primary.confidence
                }.maxByOrNull { it.confidence }
        }
}

/**
 * Compares caller-proposed single/multiple target sets using existing all-target capture scoring.
 * Incidental captures retain caller penalties; no ownership, queen or player points are inferred.
 * Runs (1 + scenario count) simulations per candidate; budget is checked before any simulation.
 * This selects tested proposals, without generating target subsets or claiming global optimality.
 */
public class MultiPocketEvaluator {
    @Suppress("LongParameterList")
    public fun evaluate(
        candidates: List<CandidateShot>,
        config: PlannerConfig,
        physics: PhysicsConfig,
        scenarios: List<UncertaintyScenario>,
        minimumConfidence: Double,
        maxSimulations: Int,
    ): MultiPocketResult {
        validateScenarios(scenarios, minimumConfidence)
        require(candidates.map { it.id }.distinct().size == candidates.size) { "Candidate IDs must be unique" }
        require(candidates.size.toLong() * (scenarios.size + 1) <= MAX_SEARCH_SIMULATIONS) { "Search too large" }
        validateSearchBudget(candidates.size * (scenarios.size + 1), maxSimulations)
        val evaluations =
            candidates.map {
                RobustnessEvaluator().evaluate(it, config, physics, scenarios, minimumConfidence, scenarios.size + 1)
            }
        val ranking = Comparator<RobustShotEvaluation> { a, b -> ShotEvaluation.RANKING.compare(a.nominal, b.nominal) }
        return MultiPocketResult(evaluations.sortedWith(ranking))
    }
}

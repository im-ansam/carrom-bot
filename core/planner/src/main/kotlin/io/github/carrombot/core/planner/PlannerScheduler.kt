package io.github.carrombot.core.planner

import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.ShotSimulator
import io.github.carrombot.core.physics.SimulationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

private const val NANOS_PER_MILLISECOND: Long = 1_000_000
private const val MAX_DURATION_MILLIS: Long = Long.MAX_VALUE / NANOS_PER_MILLISECOND

/** Count of fresh simulations and monotonic elapsed milliseconds, including worker dispatch delay. */
public data class SearchBudget(
    public val maxSimulations: Int,
    public val maxDurationMillis: Long,
) {
    init {
        require(maxSimulations in 1..MAX_SEARCH_SIMULATIONS) { "Invalid simulation budget" }
        require(maxDurationMillis in 0..MAX_DURATION_MILLIS) { "Invalid duration budget" }
    }
}

public enum class SearchStopReason {
    COMPLETE,
    SIMULATION_LIMIT,
    DEADLINE,
}

/** Timings are measured monotonic nanoseconds, not deterministic simulation time. */
public data class SearchMetrics(
    public val elapsedNanos: Long,
    public val simulationNanos: Long,
    public val startedSimulations: Int,
    public val completedSimulations: Int,
    public val cacheHits: Int,
)

/** Only fully evaluated evidence is ranked; [unexaminedIds] includes an interrupted simulation. */
public class ScheduledSearchResult internal constructor(
    public val planning: PlanningResult,
    public val stopReason: SearchStopReason,
    pruned: List<PrunedCandidate>,
    unexaminedIds: List<String>,
    public val metrics: SearchMetrics,
) {
    public val pruned: List<PrunedCandidate> = ownedCopy(pruned)
    public val unexaminedIds: List<String> = ownedCopy(unexaminedIds)
}

/**
 * Sequential best-so-far search on Dispatchers.Default; suspends the caller, owns no scope or job.
 * Inputs must remain stable until copied on the worker. At most 4096 proposals, with unique IDs.
 * A request-local exact ShotInput cache reuses physics only, rescoring every proposal independently.
 * Deadline aborts are cooperative at simulator checkpoints; scheduling, allocation, scoring and a
 * single bounded contact calculation can overshoot. This is not a hard real-time guarantee.
 * External CancellationException and all engine failures propagate, never become partial success.
 */
public class PlannerScheduler internal constructor(
    private val nanoTime: () -> Long,
) {
    public constructor() : this(System::nanoTime)

    public suspend fun search(
        candidates: List<CandidateShot>,
        config: PlannerConfig,
        physics: PhysicsConfig,
        budget: SearchBudget,
    ): ScheduledSearchResult {
        val start = nanoTime()
        return withContext(Dispatchers.Default) {
            require(candidates.size <= MAX_SEARCH_SIMULATIONS) { "Too many candidates" }
            val snapshot = candidates.toList()
            require(snapshot.map { it.id }.distinct().size == snapshot.size) { "Candidate IDs must be unique" }
            SearchRun(snapshot, config, physics, budget, nanoTime, start, coroutineContext).execute()
        }
    }
}

@Suppress("LongParameterList")
private class SearchRun(
    private val candidates: List<CandidateShot>,
    private val config: PlannerConfig,
    private val physics: PhysicsConfig,
    private val budget: SearchBudget,
    private val nanoTime: () -> Long,
    private val start: Long,
    private val context: CoroutineContext,
) {
    private val cache = hashMapOf<ShotInput, SimulationResult>()
    private val evaluations = arrayListOf<ShotEvaluation>()
    private val pruned = arrayListOf<PrunedCandidate>()
    private var index = 0
    private var started = 0
    private var completed = 0
    private var hits = 0
    private var simulationNanos = 0L

    fun execute(): ScheduledSearchResult {
        var reason = SearchStopReason.COMPLETE
        try {
            while (index < candidates.size) {
                checkpoint()
                if (!evaluate(candidates[index])) {
                    reason = SearchStopReason.SIMULATION_LIMIT
                    break
                }
                index++
            }
        } catch (_: SearchDeadline) {
            context.ensureActive()
            reason = SearchStopReason.DEADLINE
        }
        context.ensureActive()
        return ScheduledSearchResult(
            PlanningResult(evaluations.sortedWith(ShotEvaluation.RANKING)),
            reason,
            pruned,
            candidates.drop(index).map { it.id },
            SearchMetrics(nanoTime() - start, simulationNanos, started, completed, hits),
        )
    }

    private fun evaluate(candidate: CandidateShot): Boolean {
        val pruning = CandidatePruner().reason(candidate, config)
        if (pruning != null) {
            pruned.add(PrunedCandidate(candidate, pruning))
            return true
        }
        return evaluateUnpruned(candidate)
    }

    private fun evaluateUnpruned(candidate: CandidateShot): Boolean {
        val cached = cache[candidate.shot]
        val simulation =
            if (cached != null) {
                hits++
                cached
            } else {
                if (started >= budget.maxSimulations) return false
                simulate(candidate).also { cache[candidate.shot] = it }
            }
        val evaluation = ShotEvaluation.fromSimulation(candidate, simulation, config)
        checkpoint()
        evaluations.add(evaluation)
        return true
    }

    private fun simulate(candidate: CandidateShot): SimulationResult {
        started++
        val before = nanoTime()
        try {
            return ShotSimulator()
                .simulate(candidate.shot.boardState, candidate.shot, physics, ::checkpoint)
                .also { completed++ }
        } finally {
            simulationNanos += nanoTime() - before
        }
    }

    private fun checkpoint() {
        context.ensureActive()
        if (nanoTime() - start >= budget.maxDurationMillis * NANOS_PER_MILLISECOND) throw SearchDeadline()
    }
}

private class SearchDeadline : RuntimeException()

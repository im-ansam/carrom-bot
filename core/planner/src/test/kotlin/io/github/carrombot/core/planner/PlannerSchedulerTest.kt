package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PlannerSchedulerTest {
    private val board =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))),
            listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(60.0, 50.0), 5.0))),
            Striker("s", Circle(Vec2(30.0, 50.0), 5.0)),
        )
    private val policy = PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(2.0, 1.0, 1.0))
    private val physics = PhysicsConfig(0.1, 200, deceleration = 10.0)

    @Test
    fun `count budget returns completed best and untested reasons`() =
        runBlocking {
            val result =
                PlannerScheduler().search(
                    listOf(candidate("first"), candidate("second", 90.0)),
                    policy,
                    physics,
                    SearchBudget(1, 1000),
                )
            assertEquals(SearchStopReason.SIMULATION_LIMIT, result.stopReason)
            assertEquals(
                "first",
                result.planning.best
                    ?.candidate
                    ?.id,
            )
            assertEquals(listOf("second"), result.unexaminedIds)
            assertEquals(1, result.metrics.completedSimulations)
        }

    @Test
    fun `repeated exact candidates use request cache and retain identities`() =
        runBlocking {
            val result =
                PlannerScheduler().search(
                    listOf(candidate("b"), candidate("a")),
                    policy,
                    physics,
                    SearchBudget(1, 1000),
                )
            assertEquals(SearchStopReason.COMPLETE, result.stopReason)
            assertEquals(listOf("a", "b"), result.planning.plans.map { it.candidate.id })
            assertEquals(1, result.metrics.cacheHits)
            assertEquals(1, result.metrics.completedSimulations)
            assertTrue(result.unexaminedIds.isEmpty())
        }

    @Test
    fun `deadline interrupts a running simulation without partial evidence`() =
        runBlocking {
            val ticks = AtomicInteger()
            val scheduler = PlannerScheduler { ticks.getAndIncrement().toLong() * 100_000 }
            val result =
                scheduler.search(
                    listOf(candidate("slow")),
                    policy,
                    physics,
                    SearchBudget(1, 1),
                )
            assertEquals(SearchStopReason.DEADLINE, result.stopReason)
            assertEquals(1, result.metrics.startedSimulations)
            assertEquals(0, result.metrics.completedSimulations)
            assertEquals(null, result.planning.best)
            assertEquals(listOf("slow"), result.unexaminedIds)
            assertTrue(result.metrics.simulationNanos > 0)
        }

    @Test
    fun `zero deadline starts no work and empty search completes`() =
        runBlocking {
            val scheduler = PlannerScheduler { 0L }
            val expired = scheduler.search(listOf(candidate("x")), policy, physics, SearchBudget(1, 0))
            assertEquals(SearchStopReason.DEADLINE, expired.stopReason)
            assertEquals(0, expired.metrics.startedSimulations)
            val empty = scheduler.search(emptyList(), policy, physics, SearchBudget(1, 0))
            assertEquals(SearchStopReason.COMPLETE, empty.stopReason)
        }

    @Test
    fun `cache rescoring respects constraints and is discarded between requests`() =
        runBlocking {
            val original = candidate("a")
            val constrained =
                CandidateShot(
                    "b",
                    original.type,
                    original.shot,
                    original.power,
                    original.targets,
                    TrajectoryConstraints(maxBodyContacts = 0),
                )
            val scheduler = PlannerScheduler()
            repeat(2) {
                val result = scheduler.search(listOf(original, constrained), policy, physics, SearchBudget(1, 1000))
                assertEquals(listOf("a"), result.planning.plans.map { it.candidate.id })
                assertEquals(
                    listOf(RejectionReason.TRAJECTORY_CONSTRAINT),
                    result.planning.rejected
                        .single()
                        .rejections,
                )
                assertEquals(1, result.metrics.cacheHits)
                assertEquals(1, result.metrics.startedSimulations)
            }
        }

    @Test
    fun `power pruning avoids simulation without manufacturing rejection evidence`() =
        runBlocking {
            val restricted = policy.copy(powerBounds = PowerBounds(0.7, 1.0))
            val result = PlannerScheduler().search(listOf(candidate("x")), restricted, physics, SearchBudget(1, 1000))
            assertEquals(SearchStopReason.COMPLETE, result.stopReason)
            assertEquals(PruningReason.POWER_OUT_OF_BOUNDS, result.pruned.single().reason)
            assertEquals(0, result.metrics.startedSimulations)
            assertTrue(result.planning.rejected.isEmpty())
            assertThrows(UnsupportedOperationException::class.java) { (result.pruned as MutableList).clear() }
            Unit
        }

    @Test(timeout = 10000)
    fun `external cancellation stops worker and propagates without result`() =
        runBlocking {
            val enteredSimulation = CountDownLatch(1)
            val reads = AtomicInteger()
            val caller = Thread.currentThread()
            val scheduler =
                PlannerScheduler {
                    if (reads.incrementAndGet() >= 10) {
                        assertTrue(Thread.currentThread() != caller)
                        enteredSimulation.countDown()
                    }
                    0L
                }
            var returned = false
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.search(
                        listOf(candidate("slow", 1.0)),
                        policy,
                        PhysicsConfig(0.00001, 100000),
                        SearchBudget(1, 1000),
                    )
                    returned = true
                }
            assertTrue(enteredSimulation.await(5, TimeUnit.SECONDS))
            withTimeout(5000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertEquals(false, returned)
        }

    @Test
    fun `repeated count bounded searches retain identical simulated outcomes`() =
        runBlocking {
            val candidates = listOf(candidate("z", 90.0), candidate("a"), candidate("miss", 20.0))
            val scheduler = PlannerScheduler { 0L }
            val first = scheduler.search(candidates, policy, physics, SearchBudget(3, 1000))
            val second = scheduler.search(candidates, policy, physics, SearchBudget(3, 1000))
            assertEquals(first.planning.plans.map { it.candidate }, second.planning.plans.map { it.candidate })
            assertEquals(first.planning.rejected.map { it.rejections }, second.planning.rejected.map { it.rejections })
            assertEquals(
                first.planning.plans.map { it.simulation?.prediction?.frames },
                second.planning.plans.map { it.simulation?.prediction?.frames },
            )
        }

    @Test
    fun `invalid requests and engine failures propagate`() {
        assertThrows(IllegalArgumentException::class.java) { SearchBudget(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { SearchBudget(1, -1) }
        assertThrows(IllegalArgumentException::class.java) { SearchBudget(1, Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                PlannerScheduler().search(
                    candidates = listOf(candidate("x"), candidate("x")),
                    config = policy,
                    physics = physics,
                    budget = SearchBudget(2, 1000),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                PlannerScheduler().search(
                    listOf(candidate("x")),
                    policy,
                    PhysicsConfig(1.0, 2, maxCollisionIterations = 1),
                    SearchBudget(1, 1000),
                )
            }
        }
    }

    private fun candidate(
        id: String,
        speed: Double = 100.0,
    ): CandidateShot =
        CandidateShot(
            id,
            ShotType.DIRECT,
            ShotInput(board, Vec2(speed, 0.0)),
            0.5,
            listOf(PocketTarget("d", "p")),
        )
}

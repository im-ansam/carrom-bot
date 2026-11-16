package io.github.carrombot

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.CoordinateTransform
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.overlay.AimAction
import io.github.carrombot.core.overlay.AimControlsState
import io.github.carrombot.core.overlay.AimPhase
import io.github.carrombot.core.overlay.BoardMapping
import io.github.carrombot.core.overlay.OverlayScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class AimingCoordinatorTest {
    @Test
    fun `unconfigured live mode explains missing calibration and never copies frames`() =
        harness { dispatcher, scope ->
            val states = LinkedBlockingQueue<AimControlsState>()
            val coordinator = AimingCoordinator(scope, { 100 }, null, { state, _ -> states.add(state) })
            withContext(dispatcher) { coordinator.dispatch(AimAction.Start) }
            val state = checkNotNull(states.poll(2, TimeUnit.SECONDS))
            assertTrue(checkNotNull(state.message).contains("calibrated game profile required"))
            coordinator.offer { error("Unconfigured capture must not be copied") }
            withContext(dispatcher) { coordinator.close() }
        }

    @Test
    fun `bounded mailbox replaces queued frames and old timestamps do not reach vision`() =
        harness { dispatcher, scope ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val observed = LinkedBlockingQueue<Long>()
            val clock = AtomicLong(100)
            val coordinator =
                AimingCoordinator(scope, clock::get, {
                    { frame, _ ->
                        observed.add(frame.timestampNanos)
                        if (frame.timestampNanos == 1L) {
                            entered.countDown()
                            check(release.await(2, TimeUnit.SECONDS))
                        }
                        AimingObservation()
                    }
                }, { _, _ -> })
            withContext(dispatcher) { coordinator.dispatch(AimAction.Start) }
            coordinator.offer { frame(1) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            try {
                coordinator.offer { frame(2) }
                coordinator.offer { frame(3) }
            } finally {
                release.countDown()
            }
            assertEquals(1L, observed.poll(2, TimeUnit.SECONDS))
            assertEquals(3L, observed.poll(2, TimeUnit.SECONDS))
            clock.set(AimingCoordinator.MAX_AGE_NANOS + 100)
            coordinator.offer { frame(4) }
            withContext(dispatcher) { coordinator.close() }
            assertNull(observed.poll())
        }

    @Test
    fun `moving board and pause cancel search and each retry gets a new observer`() =
        harness { dispatcher, scope ->
            val started = CountDownLatch(1)
            val cancelled = CountDownLatch(1)
            val states = LinkedBlockingQueue<AimControlsState>()
            var observers = 0
            val coordinator =
                AimingCoordinator(scope, { 100 }, {
                    observers++
                    { frame, _ -> if (frame.timestampNanos == 1L) ready() else AimingObservation(reason = "moving") }
                }, { state, _ -> states.add(state) }, planner = {
                    started.countDown()
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.countDown()
                    }
                })
            withContext(dispatcher) { coordinator.dispatch(AimAction.Start) }
            coordinator.offer { frame(1) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            coordinator.offer { frame(2) }
            awaitState(states) { it.message == "moving" }
            assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            withContext(dispatcher) {
                coordinator.dispatch(AimAction.Pause)
                coordinator.dispatch(AimAction.Resume)
            }
            coordinator.offer { frame(2) }
            awaitState(states) { it.message == "moving" }
            assertEquals(2, observers)
            withContext(dispatcher) { coordinator.close() }
            assertNull(awaitState(states) { it.phase == AimPhase.STOPPED }.scene)
        }

    @Test
    fun `worker errors clear evidence and require explicit retry`() =
        harness { dispatcher, scope ->
            val states = LinkedBlockingQueue<AimControlsState>()
            val coordinator =
                AimingCoordinator(
                    scope,
                    { 100 },
                    { { _, _ -> error("private native failure") } },
                    { state, _ -> states.add(state) },
                )
            withContext(dispatcher) { coordinator.dispatch(AimAction.Start) }
            coordinator.offer { frame(1) }
            val failure = awaitState(states) { it.phase == AimPhase.ERROR }
            assertNull(failure.scene)
            assertNull(failure.message)
            withContext(dispatcher) { coordinator.dispatch(AimAction.Start) }
            assertEquals(AimPhase.RUNNING, awaitState(states) { it.phase == AimPhase.RUNNING }.phase)
            withContext(dispatcher) { coordinator.close() }
        }

    @Test
    fun `synthetic fixture uses real simulation evidence and preserves contact vertices`() =
        runBlocking {
            val board = ready().board!!
            val scene = SyntheticProfile.plan(board)
            assertFalse("Known synthetic fixture must produce an evaluated plan", scene.candidates.isEmpty())
            assertTrue(scene.candidates.all { it.confidence == null })
            val candidate = scene.candidates.first()
            assertTrue(candidate.annotations.any { it.kind == io.github.carrombot.core.overlay.AnnotationKind.CONTACT })
            assertTrue(candidate.annotations.any { it.kind == io.github.carrombot.core.overlay.AnnotationKind.POCKET })
            assertTrue(candidate.trajectories.size >= 2)
        }

    private fun ready(): AimingObservation {
        val bounds = SyntheticProfile.bounds
        val board =
            BoardState(
                bounds,
                listOf(Pocket("p", Circle(Vec2(340.0, 60.0), 32.0))),
                listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(220.0, 60.0), 20.0))),
                Striker("s", Circle(Vec2(100.0, 60.0), 24.0)),
            )
        return AimingObservation(board, BoardMapping(bounds, CoordinateTransform(bounds.min, bounds.min, 1.0)))
    }

    @Test
    fun `late noncooperative planner cannot publish after stop and stale evidence expires`() =
        harness { dispatcher, scope ->
            val states = LinkedBlockingQueue<AimControlsState>()
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val completed = CountDownLatch(1)
            val coordinator =
                AimingCoordinator(
                    scope,
                    { 100 },
                    { { _, _ -> ready() } },
                    { state, _ -> states.add(state) },
                    planner = { board ->
                        withContext(NonCancellable) {
                            started.countDown()
                            check(release.await(2, TimeUnit.SECONDS))
                            completed.countDown()
                            OverlayScene(board.bounds, emptyList(), null)
                        }
                    },
                )
            withContext(dispatcher) { coordinator.dispatch(AimAction.Start) }
            coordinator.offer { frame(1) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            withContext(dispatcher) { coordinator.dispatch(AimAction.Stop) }
            release.countDown()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            withContext(dispatcher) { coordinator.close() }
            val snapshots = states.toList()
            val stopped = snapshots.indexOfFirst { it.phase == AimPhase.STOPPED }
            assertTrue(stopped >= 0)
            assertTrue(snapshots.drop(stopped).all { it.scene == null && it.phase == AimPhase.STOPPED })
        }

    @Test
    fun `freshness timer removes recommendations when capture stops producing frames`() =
        harness { dispatcher, scope ->
            val states = LinkedBlockingQueue<AimControlsState>()
            val coordinator =
                AimingCoordinator(
                    scope,
                    { 100 },
                    { { _, _ -> ready() } },
                    { state, _ -> states.add(state) },
                    planner = { OverlayScene(it.bounds, emptyList(), null) },
                )
            withContext(dispatcher) { coordinator.dispatch(AimAction.Start) }
            coordinator.offer { frame(1) }
            awaitState(states) { it.scene != null }
            val expired = awaitState(states) { it.message?.contains("expired") == true }
            assertNull(expired.scene)
            withContext(dispatcher) { coordinator.close() }
        }

    @Test
    fun `dense board v1 records bounded synthetic planning cost without accuracy claims`() =
        runBlocking {
            val initial = checkNotNull(ready().board)
            val discs =
                initial.discs +
                    List(18) { index ->
                        Disc(
                            "dense-$index",
                            DiscKind.BLACK,
                            Circle(Vec2(40.0 + (index % 6) * 55, 160.0 + (index / 6) * 70), 8.0),
                        )
                    }
            val board = BoardState(initial.bounds, initial.pockets, discs, initial.striker)
            val started = System.nanoTime()
            val scene = SyntheticProfile.plan(board)
            val elapsed = System.nanoTime() - started
            println("CB35 dense-board-v1 discs=${discs.size} elapsedNanos=$elapsed displayed=${scene.candidates.size}")
            assertTrue(scene.candidates.size <= 3)
        }

    private fun frame(time: Long): RgbaFrame = RgbaFrame.copy(1, 1, time, ByteBuffer.allocate(4), 4, 4)

    private fun awaitState(
        states: LinkedBlockingQueue<AimControlsState>,
        predicate: (AimControlsState) -> Boolean,
    ): AimControlsState {
        repeat(20) {
            val state = checkNotNull(states.poll(2, TimeUnit.SECONDS)) { "Missing coordinator transition" }
            if (predicate(state)) return state
        }
        error("Expected transition not observed")
    }

    private fun harness(block: suspend (kotlinx.coroutines.CoroutineDispatcher, CoroutineScope) -> Unit) =
        runBlocking {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                try {
                    block(dispatcher, scope)
                } finally {
                    scope.cancel()
                }
            }
        }
}

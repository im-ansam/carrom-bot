package io.github.carrombot

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.overlay.AimAction
import io.github.carrombot.core.overlay.AimControlsState
import io.github.carrombot.core.overlay.AimPhase
import io.github.carrombot.core.overlay.BoardMapping
import io.github.carrombot.core.overlay.OverlayScene
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/** Main-dispatcher owner. Capture submits owned frames to a one-slot mailbox; no Image crosses threads. */
internal class AimingCoordinator(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val newObserver: (() -> ((RgbaFrame, Long) -> AimingObservation))?,
    private val present: (AimControlsState, BoardMapping?) -> Unit,
    private val planner: suspend (BoardState) -> OverlayScene = SyntheticProfile::plan,
    private val visionDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    private val model = AimViewModel()
    private val mailbox = AtomicReference<Channel<RgbaFrame>?>(null)
    private var run: Job? = null
    private var search: Job? = null
    private var expiry: Job? = null
    private var board: BoardState? = null
    private var mapping: BoardMapping? = null
    private var evidence: OverlayScene? = null
    private var observedAt = 0L
    private var request = 0L

    /** Caller is the capture looper; copy executes only while an aiming run accepts input. */
    fun offer(copy: () -> RgbaFrame) {
        val target = mailbox.get() ?: return
        target.trySend(copy())
    }

    fun dispatch(action: AimAction) {
        val generation = model.generation
        model.dispatch(action)
        if (generation != model.generation) {
            stopRun()
            if (model.state.phase == AimPhase.RUNNING) {
                if (newObserver == null) {
                    model.message(model.generation, "Live aiming unavailable: calibrated game profile required")
                } else {
                    startRun()
                }
            }
        }
        publish()
    }

    fun close() {
        model.dispatch(AimAction.Stop)
        stopRun()
        publish()
    }

    fun presentationFailed() = fail(model.generation)

    private fun startRun() {
        val frames = Channel<RgbaFrame>(1, BufferOverflow.DROP_OLDEST)
        mailbox.set(frames)
        val generation = model.generation
        run =
            scope.launch {
                try {
                    val observer = withContext(visionDispatcher) { checkNotNull(newObserver).invoke() }
                    for (frame in frames) {
                        if (now() - frame.timestampNanos !in 0..MAX_AGE_NANOS) continue
                        val observation = withContext(visionDispatcher) { observer(frame, now()) }
                        ensureActive()
                        if (now() - frame.timestampNanos !in 0..MAX_AGE_NANOS) {
                            invalidate("Synthetic frame expired")
                        } else {
                            observedAt = frame.timestampNanos
                            accept(observation, generation)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: RuntimeException) {
                    fail(generation)
                }
            }
    }

    private fun accept(
        observation: AimingObservation,
        generation: Long,
    ) {
        expiry?.cancel()
        expiry =
            scope.launch {
                val remaining = (MAX_AGE_NANOS - (now() - observedAt)).coerceAtLeast(0)
                delay(remaining / NANOS_PER_MILLISECOND)
                invalidate("Synthetic frame expired; waiting for fresh capture")
            }
        if (observation.board == null) {
            invalidate(observation.reason)
            return
        }
        if (board != observation.board || mapping != observation.mapping) {
            invalidate("Synthetic fixture only: evaluating stable board")
            board = observation.board
            mapping = checkNotNull(observation.mapping)
            val currentRequest = request
            search =
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.Default) { planner(observation.board) }
                        ensureActive()
                        if (currentRequest == request && now() - observedAt in 0..MAX_AGE_NANOS) {
                            evidence = result
                            model.submit(generation, result)
                            model.message(generation, "SYNTHETIC FIXTURE ONLY — no real-game calibration")
                            publish()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: RuntimeException) {
                        if (currentRequest == request) fail(generation)
                    }
                }
        } else if (evidence != null) {
            // Current observations corroborate exactly the same immutable board, not merely the run token.
            if (model.state.scene == null) model.submit(generation, checkNotNull(evidence))
        }
        publish()
    }

    private fun invalidate(reason: String) {
        request++
        search?.cancel()
        search = null
        board = null
        mapping = null
        evidence = null
        model.message(model.generation, reason, clearScene = true)
        publish()
    }

    private fun fail(generation: Long) {
        if (model.fail(generation)) {
            stopRun()
            publish()
        }
    }

    private fun stopRun() {
        mailbox.getAndSet(null)?.cancel()
        run?.cancel()
        run = null
        search?.cancel()
        search = null
        expiry?.cancel()
        expiry = null
        request++
        board = null
        mapping = null
        evidence = null
    }

    private fun publish() = present(model.state, mapping)

    internal companion object {
        const val MAX_AGE_NANOS = 1_500_000_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

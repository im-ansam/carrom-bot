package io.github.carrombot

import io.github.carrombot.core.overlay.AimAction
import io.github.carrombot.core.overlay.AimControlsState
import io.github.carrombot.core.overlay.AimPhase
import io.github.carrombot.core.overlay.OverlayScene

/**
 * Service-owned, main-thread state holder, independent of Activity recreation and Android resources.
 * Generation invalidates in-flight evidence on every mode change. CB-35 must also cancel its work
 * and invalidate requests when board observations change; tokens alone do not establish freshness.
 * No planner is invoked here and an absent result never becomes a successful prediction.
 */
internal class AimViewModel {
    var state: AimControlsState = AimControlsState()
        private set
    var generation: Long = 0
        private set

    fun dispatch(action: AimAction) {
        when (action) {
            AimAction.Start -> {
                if (state.phase == AimPhase.STOPPED || state.phase == AimPhase.ERROR) change(AimPhase.RUNNING)
            }

            AimAction.Stop -> {
                if (state.phase != AimPhase.STOPPED) change(AimPhase.STOPPED)
            }

            AimAction.Pause -> {
                if (state.phase == AimPhase.RUNNING) change(AimPhase.PAUSED)
            }

            AimAction.Resume -> {
                if (state.phase == AimPhase.PAUSED) change(AimPhase.RUNNING)
            }

            AimAction.Next -> {
                next()
            }

            is AimAction.Select -> {
                select(action.id)
            }
        }
    }

    /** Caller supplies an already validated scene in ranked order; stale/inactive results are ignored. */
    fun submit(
        request: Long,
        scene: OverlayScene,
    ): Boolean {
        if (request != generation || state.phase != AimPhase.RUNNING) return false
        state = state.copy(scene = scene)
        return true
    }

    /** Sanitized failure only; exception messages and captured content never enter the UI state. */
    fun fail(request: Long): Boolean {
        if (request != generation || state.phase != AimPhase.RUNNING) return false
        change(AimPhase.ERROR)
        return true
    }

    private fun change(phase: AimPhase) {
        generation++
        state = AimControlsState(phase)
    }

    private fun next() {
        val scene = state.scene ?: return
        if (scene.candidates.isEmpty()) return
        val index = scene.candidates.indexOfFirst { it.id == scene.selectedId }
        select(scene.candidates[(index + 1) % scene.candidates.size].id)
    }

    private fun select(id: String) {
        val scene = state.scene ?: return
        if (scene.candidates.none { it.id == id }) return
        state = state.copy(scene = OverlayScene(scene.bounds, scene.candidates, id))
    }
}

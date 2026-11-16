package io.github.carrombot

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.overlay.AimAction
import io.github.carrombot.core.overlay.AimPhase
import io.github.carrombot.core.overlay.BodyTrajectory
import io.github.carrombot.core.overlay.OverlayScene
import io.github.carrombot.core.overlay.RenderedCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AimViewModelTest {
    @Test
    fun startPauseResumeAndStopClearEvidenceAndRejectLateResults() {
        val model = AimViewModel()
        assertEquals(AimPhase.STOPPED, model.state.phase)
        model.dispatch(AimAction.Start)
        val request = model.generation
        assertTrue(model.submit(request, scene()))
        model.dispatch(AimAction.Pause)
        assertEquals(AimPhase.PAUSED, model.state.phase)
        assertNull(model.state.scene)
        assertFalse(model.submit(request, scene()))
        model.dispatch(AimAction.Resume)
        assertEquals(AimPhase.RUNNING, model.state.phase)
        assertFalse(model.submit(request, scene()))
        assertTrue(model.submit(model.generation, scene()))
        model.dispatch(AimAction.Stop)
        assertEquals(AimPhase.STOPPED, model.state.phase)
        assertNull(model.state.scene)
    }

    @Test
    fun selectionFollowsSuppliedRankWrapsAndUpdatesSceneAndPowerTogether() {
        val model = AimViewModel()
        model.dispatch(AimAction.Start)
        model.submit(model.generation, scene())
        model.dispatch(AimAction.Next)
        assertEquals("second", model.state.scene?.selectedId)
        assertEquals(0.7, model.state.selected?.power)
        model.dispatch(AimAction.Next)
        assertEquals("first", model.state.scene?.selectedId)
        model.dispatch(AimAction.Select("second"))
        model.dispatch(AimAction.Select("missing"))
        assertEquals("second", model.state.scene?.selectedId)
        assertTrue(model.submit(model.generation, OverlayScene(scene().bounds, emptyList(), null)))
        assertNull(model.state.selected)
        model.dispatch(AimAction.Next)
        assertNull(model.state.scene?.selectedId)
    }

    @Test
    fun failureClearsEvidenceAndRetryRequiresExplicitStart() {
        val model = AimViewModel()
        model.dispatch(AimAction.Start)
        val request = model.generation
        model.submit(request, scene())
        assertTrue(model.fail(request))
        assertEquals(AimPhase.ERROR, model.state.phase)
        assertNull(model.state.selected)
        model.dispatch(AimAction.Resume)
        assertEquals(AimPhase.ERROR, model.state.phase)
        model.dispatch(AimAction.Start)
        assertEquals(AimPhase.RUNNING, model.state.phase)
        assertFalse(model.submit(request, scene()))
        assertFalse(model.fail(request))
    }

    @Test
    fun duplicateAndInvalidActionsDoNotInvalidateActiveWork() {
        val model = AimViewModel()
        model.dispatch(AimAction.Pause)
        assertEquals(AimPhase.STOPPED, model.state.phase)
        model.dispatch(AimAction.Start)
        val request = model.generation
        model.dispatch(AimAction.Start)
        model.dispatch(AimAction.Resume)
        assertEquals(request, model.generation)
        assertTrue(model.submit(request, scene()))
    }

    private fun scene(): OverlayScene =
        OverlayScene(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(candidate("first", 0.3), candidate("second", 0.7)),
            "first",
        )

    private fun candidate(
        id: String,
        power: Double,
    ): RenderedCandidate =
        RenderedCandidate(
            id,
            listOf(BodyTrajectory("striker", true, listOf(Vec2(10.0, 10.0), Vec2(20.0, 20.0)))),
            emptyList(),
            power,
            null,
        )
}

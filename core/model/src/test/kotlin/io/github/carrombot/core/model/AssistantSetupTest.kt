package io.github.carrombot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AssistantSetupTest {
    @Test
    fun `opening or resuming never requests permission`() {
        val state = AssistantSetupState()
        assertEquals(SetupStage.IDLE, state.stage)
        assertEquals(SetupStage.IDLE, state.observeOverlay(true).stage)
        assertEquals(SetupStage.IDLE, state.observeOverlay(false).stage)
    }

    @Test
    fun `start requires overlay before capture and returning never chains dialogs`() {
        val requested = AssistantSetupState().start()
        assertEquals(SetupStage.OVERLAY_REQUESTED, requested.stage)
        assertEquals(requested, requested.start())
        val denied = requested.observeOverlay(false)
        assertEquals(SetupStage.OVERLAY_DENIED, denied.stage)
        assertEquals(SetupStage.IDLE, denied.observeOverlay(true).stage)
        val allowed = denied.start().observeOverlay(true)
        assertEquals(SetupStage.IDLE, allowed.stage)
        assertEquals(SetupStage.CAPTURE_REQUESTED, allowed.start().stage)
    }

    @Test
    fun `capture denial can retry and consent never means a running assistant`() {
        val requested = AssistantSetupState().observeOverlay(true).start()
        val denied = requested.captureResult(false)
        assertEquals(SetupStage.CAPTURE_DENIED, denied.stage)
        val confirmed = denied.start().captureResult(true)
        assertEquals(SetupStage.CONSENT_CONFIRMED, confirmed.stage)
        assertEquals(SetupStage.CAPTURE_REQUESTED, confirmed.start().stage)
        assertEquals(SetupStage.IDLE, confirmed.stop().stage)
    }

    @Test
    fun `stop revocation and recreation discard pending consent`() {
        val requested = AssistantSetupState().observeOverlay(true).start()
        val stopped = requested.stop()
        assertEquals(stopped, stopped.captureResult(true))
        val revoked = requested.observeOverlay(false)
        assertEquals(SetupStage.OVERLAY_DENIED, revoked.stage)
        assertFalse(revoked.overlayAllowed)
        assertEquals(revoked, revoked.captureResult(true))
        assertEquals(AssistantSetupState(), AssistantSetupState().captureResult(true))
    }

    @Test
    fun `missing system permission UI reports explicit failure and can retry`() {
        val failed = AssistantSetupState().start().launchFailed()
        assertEquals(SetupStage.UNAVAILABLE, failed.stage)
        assertEquals(SetupStage.OVERLAY_REQUESTED, failed.start().stage)
    }
}

package io.github.carrombot

import io.github.carrombot.core.model.AssistantSetupState
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticLogTest {
    @Test
    fun `diagnostics contain only closed event and permission vocabularies`() {
        val requested = AssistantSetupState().observeOverlay(true).start()
        assertEquals(
            "event=START_PRESSED stage=CAPTURE_REQUESTED overlay=ALLOWED",
            DiagnosticEvent.START_PRESSED.format(requested),
        )
        assertEquals(
            "event=PERMISSION_STATE stage=CAPTURE_DENIED overlay=ALLOWED",
            DiagnosticEvent.PERMISSION_STATE.format(requested.captureResult(false)),
        )
    }
}

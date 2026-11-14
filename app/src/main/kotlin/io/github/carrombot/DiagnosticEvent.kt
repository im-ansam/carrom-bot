package io.github.carrombot

import android.util.Log
import io.github.carrombot.core.model.AssistantSetupState

/** Closed vocabulary prevents arbitrary text, consent intents, exceptions or frame data entering diagnostics. */
internal enum class DiagnosticEvent {
    APP_OPENED,
    START_PRESSED,
    STOP_PRESSED,
    PERMISSION_STATE,
    PERMISSION_UI_UNAVAILABLE,
    SESSION_RESET,
    SETTINGS_OPENED,
}

internal fun DiagnosticEvent.format(state: AssistantSetupState): String =
    "event=$name stage=${state.stage.name} overlay=${if (state.overlayAllowed) "ALLOWED" else "NOT_ALLOWED"}"

internal fun logDiagnostic(
    event: DiagnosticEvent,
    state: AssistantSetupState,
) {
    Log.i("CarromBot", event.format(state))
}

package io.github.carrombot.core.model

/** Permission setup progress only; none of these stages indicate active capture or an active assistant. */
public enum class SetupStage {
    IDLE,
    OVERLAY_REQUESTED,
    OVERLAY_DENIED,
    CAPTURE_REQUESTED,
    CAPTURE_DENIED,
    CONSENT_CONFIRMED,
    UNAVAILABLE,
}

/**
 * Immutable permission setup shared by UI and future adapters. Only [start] requests a
 * permission prompt. Observations never launch UI, consent is session-local and [stop]
 * invalidates late results. Recreate this state after Activity/process destruction;
 * never persist a capture grant or infer one from overlay access.
 */
@ConsistentCopyVisibility
public data class AssistantSetupState private constructor(
    public val overlayAllowed: Boolean,
    public val stage: SetupStage,
) {
    public constructor() : this(false, SetupStage.IDLE)

    init {
        require(overlayAllowed || stage !in CAPTURE_STAGES) { "Capture setup requires overlay access" }
    }

    /** Recheck current OS permission; returning from overlay settings requires another explicit Start. */
    public fun observeOverlay(allowed: Boolean): AssistantSetupState {
        val nextStage =
            when {
                stage == SetupStage.OVERLAY_REQUESTED -> if (allowed) SetupStage.IDLE else SetupStage.OVERLAY_DENIED
                allowed && stage == SetupStage.OVERLAY_DENIED -> SetupStage.IDLE
                !allowed && stage in CAPTURE_STAGES -> SetupStage.OVERLAY_DENIED
                else -> stage
            }
        return AssistantSetupState(allowed, nextStage)
    }

    /** A user gesture requests at most one system dialog; repeated taps while pending have no effect. */
    public fun start(): AssistantSetupState =
        if (stage == SetupStage.OVERLAY_REQUESTED || stage == SetupStage.CAPTURE_REQUESTED) {
            this
        } else {
            AssistantSetupState(
                overlayAllowed,
                if (overlayAllowed) SetupStage.CAPTURE_REQUESTED else SetupStage.OVERLAY_REQUESTED,
            )
        }

    /** Accept a result only for the current pending request. Does not retain or activate an OS consent token. */
    public fun captureResult(granted: Boolean): AssistantSetupState =
        if (stage == SetupStage.CAPTURE_REQUESTED) {
            AssistantSetupState(
                overlayAllowed,
                if (granted) SetupStage.CONSENT_CONFIRMED else SetupStage.CAPTURE_DENIED,
            )
        } else {
            this
        }

    public fun stop(): AssistantSetupState = AssistantSetupState(overlayAllowed, SetupStage.IDLE)

    public fun launchFailed(): AssistantSetupState = AssistantSetupState(overlayAllowed, SetupStage.UNAVAILABLE)

    private companion object {
        val CAPTURE_STAGES: Set<SetupStage> =
            setOf(SetupStage.CAPTURE_REQUESTED, SetupStage.CAPTURE_DENIED, SetupStage.CONSENT_CONFIRMED)
    }
}

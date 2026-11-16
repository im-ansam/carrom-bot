package io.github.carrombot.core.overlay

/** Aiming is independent of screen sharing; stopping or pausing aiming clears its evidence. */
public enum class AimPhase { STOPPED, RUNNING, PAUSED, ERROR }

/** User intent only. No action executes a shot or requests capture permission. */
public sealed interface AimAction {
    public data object Start : AimAction

    public data object Stop : AimAction

    public data object Pause : AimAction

    public data object Resume : AimAction

    public data object Next : AimAction

    public data class Select(
        public val id: String,
    ) : AimAction
}

/** Immutable presentation snapshot. Scene order is the producer's ranking, never inferred utility. */
public data class AimControlsState(
    public val phase: AimPhase = AimPhase.STOPPED,
    public val scene: OverlayScene? = null,
    public val message: String? = null,
) {
    init {
        require(scene == null || phase == AimPhase.RUNNING) { "Inactive aiming cannot retain evidence" }
    }

    public val selected: RenderedCandidate? get() = scene?.candidates?.firstOrNull { it.id == scene.selectedId }
}

package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardState

/** Immutable snapshot and explicit simulation clock in seconds; never a wall-clock timestamp. */
public data class SimulationState(
    public val boardState: BoardState,
    public val elapsedSeconds: Double = 0.0,
) {
    init {
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0) { "Clock must be finite and nonnegative" }
    }
}

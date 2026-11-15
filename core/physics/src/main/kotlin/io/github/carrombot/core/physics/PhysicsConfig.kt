package io.github.carrombot.core.physics

/**
 * Fixed step in seconds and a hard iteration budget. The time cap is exactly
 * [timeStepSeconds] * [maxSteps]; no shortened final step or wall clock is used.
 * At most 100,000 steps may be retained in a trajectory. Invalid settings throw
 * [IllegalArgumentException], including an unrepresentable time cap.
 * [deceleration] is isotropic drag in mm/s²; zero preserves free motion, positive
 * values enable the configurable constant-deceleration approximation for every body.
 */
public data class PhysicsConfig(
    public val timeStepSeconds: Double,
    public val maxSteps: Int,
    public val deceleration: Double = 0.0,
) {
    init {
        require(timeStepSeconds.isFinite() && timeStepSeconds > 0.0) { "Step must be finite and positive" }
        require(maxSteps in 1..MAX_STEPS) { "Step budget must be in 1..$MAX_STEPS" }
        require(timeLimitSeconds.isFinite()) { "Time cap must be finite" }
        require(deceleration.isFinite() && deceleration >= 0.0) { "Deceleration must be finite and nonnegative" }
    }

    public val timeLimitSeconds: Double get() = timeStepSeconds * maxSteps

    private companion object {
        const val MAX_STEPS: Int = 100_000
    }
}

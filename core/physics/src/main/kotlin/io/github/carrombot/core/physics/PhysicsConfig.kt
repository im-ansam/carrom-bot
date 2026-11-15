package io.github.carrombot.core.physics

/**
 * Fixed step in seconds and a hard iteration budget. The time cap is exactly
 * [timeStepSeconds] * [maxSteps]; no shortened final step or wall clock is used.
 * At most 100,000 steps may be retained in a trajectory. Invalid settings throw
 * [IllegalArgumentException], including an unrepresentable time cap.
 * [deceleration] is isotropic drag in mm/s²; zero preserves free motion, positive
 * values enable the configurable constant-deceleration approximation for every body.
 * [discMass] and [strikerMass] are positive masses in a common unit (only their ratio matters).
 * [restitution] is normal restitution in [0, 1]; no tangential friction or spin is modeled.
 * [contactTolerance] is the contact detection distance in mm, not a rest-speed threshold.
 * [maxCollisionIterations] bounds pair scans/advancements per fixed step, including separation.
 * Defaults are uncalibrated equal masses and elastic contacts. Budget exhaustion, arithmetic
 * that cannot be represented, or a clock unable to advance throws rather than truncates.
 */
public data class PhysicsConfig(
    public val timeStepSeconds: Double,
    public val maxSteps: Int,
    public val deceleration: Double = 0.0,
    public val discMass: Double = 1.0,
    public val strikerMass: Double = 1.0,
    public val restitution: Double = 1.0,
    public val contactTolerance: Double = 1e-8,
    public val maxCollisionIterations: Int = 4096,
) {
    init {
        require(timeStepSeconds.isFinite() && timeStepSeconds > 0.0) { "Step must be finite and positive" }
        require(maxSteps in 1..MAX_STEPS) { "Step budget must be in 1..$MAX_STEPS" }
        require(timeLimitSeconds.isFinite()) { "Time cap must be finite" }
        require(deceleration.isFinite() && deceleration >= 0.0) { "Deceleration must be finite and nonnegative" }
        require(discMass.isFinite() && discMass > 0.0 && (1.0 / discMass).isFinite()) { "Invalid disc mass" }
        require(strikerMass.isFinite() && strikerMass > 0.0 && (1.0 / strikerMass).isFinite()) {
            "Invalid striker mass"
        }
        require(restitution.isFinite() && restitution in 0.0..1.0) { "Restitution must be in [0, 1]" }
        require(contactTolerance.isFinite() && contactTolerance > 0.0) { "Invalid contact tolerance" }
        require(maxCollisionIterations in 1..MAX_STEPS) { "Invalid collision iteration budget" }
    }

    public val timeLimitSeconds: Double get() = timeStepSeconds * maxSteps

    private companion object {
        const val MAX_STEPS: Int = 100_000
    }
}

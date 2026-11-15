package io.github.carrombot.core.physics

/**
 * Calibratable linear striker mapping: speed = normalized power * [maximumSpeed], in mm/s.
 * This is an approximation, not a third-party game's measured response. Maximum speed
 * must be finite and positive. Invalid power/speed throws [IllegalArgumentException];
 * values are never silently clamped. Zero power maps to zero speed (no ShotInput launch).
 */
public class PowerModel(
    public val maximumSpeed: Double,
) {
    init {
        require(maximumSpeed.isFinite() && maximumSpeed > 0.0) { "Maximum speed must be finite and positive" }
    }

    public fun speedForPower(power: Double): Double {
        require(power.isFinite() && power in 0.0..1.0) { "Power must be in [0, 1]" }
        return power * maximumSpeed
    }

    /** Inverse lookup for a finite speed in [0, maximumSpeed], returning power in [0, 1]. */
    public fun powerForSpeed(speed: Double): Double {
        require(speed.isFinite() && speed in 0.0..maximumSpeed) { "Speed must be in [0, maximumSpeed]" }
        return speed / maximumSpeed
    }
}

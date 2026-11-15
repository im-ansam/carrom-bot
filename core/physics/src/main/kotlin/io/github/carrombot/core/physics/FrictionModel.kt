package io.github.carrombot.core.physics

import io.github.carrombot.core.model.Vec2
import kotlin.math.hypot
import kotlin.math.min

/** Integrated displacement in mm and final velocity in mm/s over one caller-supplied interval. */
public data class FrictionStep(
    public val displacement: Vec2,
    public val velocity: Vec2,
)

/**
 * Isotropic constant-deceleration approximation to rolling/sliding drag, in mm/s².
 * Direction stays fixed until exact rest; there is no reversal, spin or phase transition.
 * [deceleration] must be finite and positive. Invalid inputs or nonrepresentable
 * results throw [IllegalArgumentException]. Parameters require empirical calibration.
 */
public class FrictionModel(
    public val deceleration: Double,
) {
    init {
        require(deceleration.isFinite() && deceleration > 0.0) { "Deceleration must be finite and positive" }
    }

    /** Analytic time to rest in seconds for a finite nonnegative speed in mm/s. */
    public fun stoppingTime(speed: Double): Double {
        requireSpeed(speed)
        return finiteResult(speed / deceleration)
    }

    /** Analytic distance to rest in mm: speed² / (2 * deceleration). */
    public fun stoppingDistance(speed: Double): Double = finiteResult((speed / 2.0) * stoppingTime(speed))

    /**
     * Exact constant-deceleration integration for finite nonnegative [seconds].
     * If rest occurs inside the interval, integrate only to rest, then hold position.
     * No rest threshold is used. IEEE 754 rounding/underflow still applies.
     */
    public fun step(
        velocity: Vec2,
        seconds: Double,
    ): FrictionStep {
        require(seconds.isFinite() && seconds >= 0.0) { "Interval must be finite and nonnegative" }
        val speed = hypot(velocity.x, velocity.y)
        requireSpeed(speed)
        if (seconds == 0.0 || speed == 0.0) {
            return FrictionStep(Vec2(0.0, 0.0), if (seconds == 0.0) velocity else Vec2(0.0, 0.0))
        }
        val stopTime = stoppingTime(speed)
        val activeTime = min(seconds, stopTime)
        val finalSpeed = if (seconds >= stopTime) 0.0 else (speed - deceleration * seconds).coerceAtLeast(0.0)
        val distance = finiteResult((speed / 2.0 + finalSpeed / 2.0) * activeTime)
        val direction = Vec2(velocity.x / speed, velocity.y / speed)
        return FrictionStep(
            Vec2(direction.x * distance, direction.y * distance),
            if (finalSpeed == 0.0) Vec2(0.0, 0.0) else Vec2(direction.x * finalSpeed, direction.y * finalSpeed),
        )
    }

    private fun requireSpeed(speed: Double) {
        require(speed.isFinite() && speed >= 0.0) { "Speed must be finite and nonnegative" }
    }

    private fun finiteResult(value: Double): Double {
        require(value.isFinite()) { "Friction result is not representable" }
        return value
    }
}

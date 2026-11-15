package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import kotlin.math.abs
import kotlin.math.sign

/** Radius-aware swept walls with analytic drag between impacts; no pocket openings yet. */
internal class ReboundEngine(
    private val config: PhysicsConfig,
) {
    private val friction = config.deceleration.takeIf { it > 0.0 }?.let(::FrictionModel)

    fun time(
        body: ContactBody,
        bounds: BoardBounds,
        remaining: Double,
    ): Double {
        var earliest = remaining
        for (wall in WallCollision.entries) {
            val gap = wall.gap(body, bounds)
            val approaching = wall.outward(body.velocity) > 0.0 && gap > config.contactTolerance
            if (approaching && travel(body, wall, earliest) >= gap) {
                var low = 0.0
                var high = earliest
                repeat(BISECTIONS) {
                    val middle = low + (high - low) / 2.0
                    if (travel(body, wall, middle) < gap) low = middle else high = middle
                }
                earliest = high
            }
        }
        return earliest
    }

    fun resolve(
        body: ContactBody,
        bounds: BoardBounds,
    ): ContactBody {
        var result = body
        for (wall in WallCollision.entries) {
            val gap = wall.gap(result, bounds)
            require(gap >= -config.contactTolerance) { "Body correction exceeds wall tolerance" }
            if (gap <= config.contactTolerance) {
                if (gap < 0.0) result = correctPenetration(result, wall, bounds, gap)
                val normalSpeed = wall.outward(result.velocity)
                if (normalSpeed > 0.0) result = result.copy(velocity = reflect(result.velocity, wall, normalSpeed))
            }
        }
        require(bounds.contains(result.circle)) { "Wall contact cannot be represented inside bounds" }
        return result
    }

    private fun correctPenetration(
        body: ContactBody,
        wall: WallCollision,
        bounds: BoardBounds,
        gap: Double,
    ): ContactBody {
        val center = offset(body.circle.center, wall.normal, gap)
        val corrected = body.copy(circle = body.circle.copy(center = center))
        return if (wall.gap(corrected, bounds) < 0.0) {
            corrected.copy(circle = corrected.circle.copy(center = wall.roundInward(center)))
        } else {
            corrected
        }
    }

    private fun reflect(
        velocity: Vec2,
        wall: WallCollision,
        normalSpeed: Double,
    ): Vec2 {
        val tangent = Vec2(-wall.normal.y, wall.normal.x)
        val tangentSpeed = velocity.x * tangent.x + velocity.y * tangent.y
        val loss = config.wallFriction * normalSpeed * (1.0 + config.wallRestitution)
        require(loss.isFinite()) { "Unrepresentable wall friction" }
        val retained = (abs(tangentSpeed) * config.wallTangentialRestitution - loss).coerceAtLeast(0.0)
        val normal = -normalSpeed * config.wallRestitution
        return Vec2(
            wall.normal.x * normal + tangent.x * sign(tangentSpeed) * retained,
            wall.normal.y * normal + tangent.y * sign(tangentSpeed) * retained,
        )
    }

    private fun travel(
        body: ContactBody,
        wall: WallCollision,
        seconds: Double,
    ): Double {
        val displacement =
            friction?.step(body.velocity, seconds)?.displacement
                ?: Vec2(body.velocity.x * seconds, body.velocity.y * seconds)
        return wall.outward(displacement)
    }

    private fun offset(
        value: Vec2,
        direction: Vec2,
        amount: Double,
    ): Vec2 = Vec2(value.x + direction.x * amount, value.y + direction.y * amount)

    private companion object {
        const val BISECTIONS = 64
    }
}

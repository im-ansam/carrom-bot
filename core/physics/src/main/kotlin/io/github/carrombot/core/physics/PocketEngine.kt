package io.github.carrombot.core.physics

import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.Vec2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Full-body aperture capture along analytic straight drag paths, before any wall crossing. */
internal class PocketEngine(
    private val config: PhysicsConfig,
) {
    private val friction = config.deceleration.takeIf { it > 0.0 }?.let(::FrictionModel)

    fun contains(
        body: ContactBody,
        pocket: Pocket,
    ): Boolean {
        val radius = pocket.circle.radius - body.circle.radius
        val delta = Vec2(body.circle.center.x - pocket.circle.center.x, body.circle.center.y - pocket.circle.center.y)
        val distance = hypot(delta.x, delta.y)
        require(distance.isFinite()) { "Unrepresentable pocket geometry" }
        return radius >= 0.0 && distance <= radius + config.contactTolerance
    }

    fun time(
        body: ContactBody,
        pocket: Pocket,
        horizon: Double,
    ): Double {
        val radius = pocket.circle.radius - body.circle.radius
        val speed = hypot(body.velocity.x, body.velocity.y)
        require(speed.isFinite()) { "Unrepresentable pocket speed" }
        return if (radius < 0.0 || speed == 0.0) horizon else entryTime(body, pocket, horizon, radius, speed)
    }

    private fun entryTime(
        body: ContactBody,
        pocket: Pocket,
        horizon: Double,
        radius: Double,
        speed: Double,
    ): Double {
        val dx = pocket.circle.center.x - body.circle.center.x
        val dy = pocket.circle.center.y - body.circle.center.y
        val ux = body.velocity.x / speed
        val uy = body.velocity.y / speed
        val along = dx * ux + dy * uy
        val across = kotlin.math.abs(dx * uy - dy * ux)
        require(along.isFinite() && across.isFinite()) { "Unrepresentable pocket sweep" }
        val entry =
            if (along > 0.0 && across <= radius) {
                val depth = sqrt(radius - across) * sqrt(radius + across)
                require(depth.isFinite()) { "Unrepresentable aperture intersection" }
                along - depth
            } else {
                Double.POSITIVE_INFINITY
            }
        return if (entry > 0.0 && entry <= travel(speed, horizon)) bisect(speed, entry, horizon) else horizon
    }

    private fun bisect(
        speed: Double,
        entry: Double,
        horizon: Double,
    ): Double {
        var low = 0.0
        var high = horizon
        repeat(BISECTIONS) {
            val middle = low + (high - low) / 2.0
            if (travel(speed, middle) < entry) low = middle else high = middle
        }
        return high
    }

    private fun travel(
        speed: Double,
        seconds: Double,
    ): Double =
        friction?.step(Vec2(speed, 0.0), seconds)?.displacement?.x
            ?: (speed * seconds).also { require(it.isFinite()) { "Unrepresentable pocket travel" } }

    private companion object {
        const val BISECTIONS = 64
    }
}

package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Vec2
import kotlin.math.hypot

/**
 * Swept circles along piecewise quadratic drag trajectories.
 * Contact distance is approximate within config tolerance (mm).
 * Stable ID pair order, then stable body ID/wall order, defines sequential
 * simultaneous impacts. Repeated scans settle chains before advancing.
 * Exhausting the shared deterministic body/wall budget fails explicitly.
 */
internal class CollisionEngine(
    private val config: PhysicsConfig,
) {
    private val friction = config.deceleration.takeIf { it > 0.0 }?.let(::FrictionModel)
    private val resolver = CollisionResolver(config)
    private val sweep = SweptContact(config)
    private val rebounds = ReboundEngine(config)

    fun needsSeparation(board: BoardState): Boolean {
        val bodies = bodies(board)
        return bodies.indices.any { i -> (i + 1 until bodies.size).any { j -> gap(bodies[i], bodies[j]) < 0.0 } }
    }

    fun advance(board: BoardState): BoardState {
        val bodies = bodies(board).toMutableList()
        var remaining = config.timeStepSeconds
        var iterations = 0
        while (true) {
            require(iterations++ < config.maxCollisionIterations) { "Collision iteration budget exhausted" }
            var changed = resolveContacts(bodies)
            for (i in bodies.indices) {
                val after = rebounds.resolve(bodies[i], board.bounds)
                changed = changed || after != bodies[i]
                bodies[i] = after
            }
            if (!changed) {
                if (remaining == 0.0) break
                val wallInterval = bodies.minOf { rebounds.time(it, board.bounds, remaining) }
                val interval = safeInterval(bodies, wallInterval)
                require(interval > 0.0 && remaining - interval < remaining) { "Collision clock cannot advance" }
                for (i in bodies.indices) bodies[i] = move(bodies[i], interval)
                remaining = (remaining - interval).coerceAtLeast(0.0)
            }
        }
        val byId = bodies.associateBy { it.id }
        val discs =
            board.discs.map {
                val body = byId.getValue(it.id)
                it.copy(circle = body.circle, velocity = body.velocity)
            }
        val striker = byId.getValue(board.striker.id)
        return BoardState(
            board.bounds,
            board.pockets,
            discs,
            board.striker.copy(circle = striker.circle, velocity = striker.velocity),
        )
    }

    private fun resolveContacts(bodies: MutableList<ContactBody>): Boolean {
        var changed = false
        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                if (gap(bodies[i], bodies[j]) <= config.contactTolerance) {
                    val (a, b) = resolver.resolve(bodies[i], bodies[j])
                    changed = changed || a != bodies[i] || b != bodies[j]
                    bodies[i] = a
                    bodies[j] = b
                }
            }
        }
        return changed
    }

    private fun safeInterval(
        bodies: List<ContactBody>,
        remaining: Double,
    ): Double {
        var interval = remaining
        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                interval = minOf(interval, sweep.time(bodies[i], bodies[j], remaining))
            }
        }
        return interval
    }

    private fun move(
        body: ContactBody,
        seconds: Double,
    ): ContactBody {
        val motion =
            friction?.step(body.velocity, seconds) ?: FrictionStep(
                Vec2(body.velocity.x * seconds, body.velocity.y * seconds),
                body.velocity,
            )
        return body.copy(
            circle =
                body.circle.copy(
                    center =
                        Vec2(
                            body.circle.center.x + motion.displacement.x,
                            body.circle.center.y + motion.displacement.y,
                        ),
                ),
            velocity = motion.velocity,
        )
    }

    private fun gap(
        a: ContactBody,
        b: ContactBody,
    ): Double {
        val distance = hypot(b.circle.center.x - a.circle.center.x, b.circle.center.y - a.circle.center.y)
        val value = distance - a.circle.radius - b.circle.radius
        require(value.isFinite()) { "Unrepresentable pair geometry" }
        return value
    }

    private fun bodies(board: BoardState): List<ContactBody> =
        (
            board.discs.map { ContactBody(it.id, it.circle, it.velocity, config.discMass) } +
                ContactBody(board.striker.id, board.striker.circle, board.striker.velocity, config.strikerMass)
        ).sortedBy { it.id }
}

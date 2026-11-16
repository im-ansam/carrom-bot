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
    private val timeline: ShotTimeline? = null,
    private val checkpoint: () -> Unit = {},
) {
    private val friction = config.deceleration.takeIf { it > 0.0 }?.let(::FrictionModel)
    private val resolver = CollisionResolver(config)
    private val sweep = SweptContact(config)
    private val rebounds = ReboundEngine(config)
    private val pockets = PocketEngine(config)
    private var eventTime = 0.0

    fun needsSeparation(board: BoardState): Boolean {
        val bodies = bodies(board)
        return bodies.indices.any { i -> (i + 1 until bodies.size).any { j -> gap(bodies[i], bodies[j]) < 0.0 } }
    }

    fun advance(
        board: BoardState,
        startSeconds: Double = 0.0,
    ): BoardState {
        val bodies = bodies(board).toMutableList()
        var remaining = config.timeStepSeconds
        var iterations = 0
        while (true) {
            checkpoint()
            eventTime = startSeconds + (config.timeStepSeconds - remaining)
            require(iterations++ < config.maxCollisionIterations) { "Collision iteration budget exhausted" }
            capture(bodies, board)
            var changed = resolveContacts(bodies)
            for (i in bodies.indices) {
                val after =
                    rebounds.resolve(bodies[i], board.bounds) { wall ->
                        record(ShotEventType.WALL_CONTACT, bodies[i].id, wall.name, bodies[i].circle.center)
                    }
                changed = changed || after != bodies[i]
                bodies[i] = after
            }
            if (!changed) {
                if (remaining == 0.0) break
                val wallInterval = bodies.minOfOrNull { rebounds.time(it, board.bounds, remaining) } ?: remaining
                val pocketInterval = pocketInterval(bodies, board, wallInterval)
                val interval = safeInterval(bodies, pocketInterval)
                require(interval > 0.0 && remaining - interval < remaining) { "Collision clock cannot advance" }
                for (i in bodies.indices) bodies[i] = move(bodies[i], interval)
                remaining = (remaining - interval).coerceAtLeast(0.0)
            }
        }
        val byId = bodies.associateBy { it.id }
        val discs =
            board.discs.filter { it.id in byId }.map {
                val body = byId.getValue(it.id)
                it.copy(circle = body.circle, velocity = body.velocity)
            }
        val striker = requireNotNull(byId[board.striker.id] ?: timeline?.capturedStriker) { "Missing striker state" }
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
                checkpoint()
                if (gap(bodies[i], bodies[j]) <= config.contactTolerance) {
                    val (a, b) = resolver.resolve(bodies[i], bodies[j])
                    recordBodyImpact(bodies[i], bodies[j], a, b)
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
                checkpoint()
                interval = minOf(interval, sweep.time(bodies[i], bodies[j], remaining))
            }
        }
        return interval
    }

    private fun recordBodyImpact(
        beforeA: ContactBody,
        beforeB: ContactBody,
        a: ContactBody,
        b: ContactBody,
    ) {
        if (a.velocity != beforeA.velocity || b.velocity != beforeB.velocity) {
            record(ShotEventType.BODY_CONTACT, a.id, b.id, a.circle.center, b.circle.center)
        }
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

    private fun bodies(board: BoardState): List<ContactBody> =
        (
            board.discs.map { ContactBody(it.id, it.circle, it.velocity, config.discMass) } +
                ContactBody(board.striker.id, board.striker.circle, board.striker.velocity, config.strikerMass)
        ).filter { timeline?.removed?.contains(it.id) != true }.sortedBy { it.id }

    private fun record(
        type: ShotEventType,
        bodyId: String,
        target: String,
        center: Vec2,
        targetCenter: Vec2? = null,
    ) {
        val event = ShotEvent(eventTime, type, bodyId, target)
        timeline?.events?.add(event)
        timeline?.resolvedEvents?.add(ResolvedShotEvent(event, center, targetCenter))
    }

    private fun pocketInterval(
        bodies: List<ContactBody>,
        board: BoardState,
        horizon: Double,
    ): Double {
        var interval = horizon
        if (timeline != null) {
            for (body in bodies) {
                for (pocket in board.pockets) {
                    checkpoint()
                    interval = minOf(interval, pockets.time(body, pocket, horizon))
                }
            }
        }
        return interval
    }

    private fun capture(
        bodies: MutableList<ContactBody>,
        board: BoardState,
    ) {
        val sink = timeline ?: return
        val sortedPockets = board.pockets.sortedBy { it.id }
        val iterator = bodies.listIterator()
        while (iterator.hasNext()) {
            checkpoint()
            val body = iterator.next()
            val pocket = sortedPockets.firstOrNull { pockets.contains(body, it) }
            if (pocket != null) {
                val frozen = rebounds.resolve(body.copy(velocity = Vec2(0.0, 0.0)), board.bounds)
                sink.removed.add(body.id)
                if (body.id == board.striker.id) sink.capturedStriker = frozen
                val kind = board.discs.find { it.id == body.id }?.kind
                sink.captures.add(PocketCapture(eventTime, body.id, pocket.id, kind))
                record(ShotEventType.CAPTURE, body.id, pocket.id, body.circle.center)
                iterator.remove()
            }
        }
    }
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

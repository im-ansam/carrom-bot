package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

internal class ContactShotGeometry(
    private val request: ContactShotRequest,
    private val config: ContactShotConfig,
) {
    private val board = request.board
    private val disc = board.discs.single { it.id == request.target.discId }
    private val pocket = board.pockets.single { it.id == request.target.pocketId }
    private val strikerRadius = board.striker.circle.radius

    // Sequential geometric guards keep rejected proposals from constructing invalid model values.
    @Suppress("ReturnCount")
    fun generate(
        legal: List<Vec2>,
        family: ShotType,
    ): ContactShot? {
        validateScene()
        if (request.strikerPosition !in legal) return null
        val start = Circle(request.strikerPosition, strikerRadius)
        if (!board.bounds.contains(start)) return null
        val direction = subtract(request.pocketAim, disc.circle.center)
        val length = magnitude(direction)
        if (length == 0.0) return null
        val normal = scale(direction, 1.0 / length)
        val ghost = subtract(disc.circle.center, scale(normal, strikerRadius + disc.circle.radius))
        if (!board.bounds.contains(Circle(ghost, strikerRadius))) return null
        val approach = subtract(ghost, start.center)
        val travel = magnitude(approach)
        if (travel == 0.0 || dot(approach, normal) <= 0.0) return null
        val direct = abs(approach.x * normal.y - approach.y * normal.x) <= config.alignmentTolerance
        if (direct != (family == ShotType.DIRECT)) return null
        val entry = pocketEntry(normal) ?: return null
        if (!clearPaths(start, ghost, entry)) return null
        val speed = config.powerModel.speedForPower(request.power)
        val velocity = scale(approach, speed / travel)
        val positioned = BoardState(board.bounds, board.pockets, board.discs, board.striker.copy(circle = start))
        val candidate =
            CandidateShot(
                request.id,
                family,
                ShotInput(positioned, velocity),
                request.power,
                listOf(request.target),
                TrajectoryConstraints(maxWallContacts = 0, maxBodyContacts = 1, minBodyContacts = 1),
            )
        return ContactShot(
            candidate,
            ContactGeometry(
                ghost,
                subtract(disc.circle.center, scale(normal, disc.circle.radius)),
                request.pocketAim,
                entry,
            ),
        )
    }

    private fun validateScene() {
        require(
            board.discs.all { it.velocity.x == 0.0 && it.velocity.y == 0.0 } &&
                board.striker.velocity.x == 0.0 && board.striker.velocity.y == 0.0,
        ) { "Contact generation requires a stationary scene" }
        for ((index, first) in board.discs.withIndex()) {
            require(
                board.discs.drop(index + 1).all {
                    distance(first.circle.center, it.circle.center) > first.circle.radius + it.circle.radius
                },
            ) { "Overlapping or touching scene discs" }
        }
        require(
            board.discs.none { body ->
                board.pockets.any {
                    distance(body.circle.center, it.circle.center) <= it.circle.radius - body.circle.radius
                }
            },
        ) { "Active disc is already captured" }
    }

    // Each guard excludes an impossible aperture before square roots or model construction.
    @Suppress("ReturnCount")
    private fun pocketEntry(normal: Vec2): Vec2? {
        val aperture = pocket.circle.radius - disc.circle.radius - config.clearance
        if (aperture <= 0.0 || distance(request.pocketAim, pocket.circle.center) > aperture) return null
        val offset = subtract(pocket.circle.center, disc.circle.center)
        val along = dot(offset, normal)
        val across = abs(offset.x * normal.y - offset.y * normal.x)
        if (across > aperture) return null
        val entryDistance = along - sqrt(aperture - across) * sqrt(aperture + across)
        if (entryDistance <= 0.0) return null
        val entry = add(disc.circle.center, scale(normal, entryDistance))
        return entry.takeIf { board.bounds.contains(Circle(it, disc.circle.radius)) }
    }

    private fun clearPaths(
        start: Circle,
        ghost: Vec2,
        entry: Vec2,
    ): Boolean {
        val overlappingStart =
            board.discs.any {
                distance(start.center, it.circle.center) <= strikerRadius + it.circle.radius
            }
        val blockedCorridor =
            board.discs.filter { it.id != disc.id }.any {
                blocked(start.center, ghost, it.circle, strikerRadius) ||
                    blocked(disc.circle.center, entry, it.circle, disc.circle.radius)
            }
        val strikerCapture =
            board.pockets.any {
                val aperture = it.circle.radius - strikerRadius
                aperture >= 0.0 && segmentDistance(start.center, ghost, it.circle.center) <= aperture + config.clearance
            }
        val wrongPocket =
            board.pockets.filter { it.id != pocket.id }.any {
                val aperture = it.circle.radius - disc.circle.radius
                aperture >= 0.0 &&
                    segmentDistance(disc.circle.center, entry, it.circle.center) <= aperture + config.clearance
            }
        return !overlappingStart && !blockedCorridor && !strikerCapture && !wrongPocket
    }

    private fun blocked(
        start: Vec2,
        end: Vec2,
        obstacle: Circle,
        radius: Double,
    ): Boolean = segmentDistance(start, end, obstacle.center) <= radius + obstacle.radius + config.clearance
}

private fun segmentDistance(
    start: Vec2,
    end: Vec2,
    point: Vec2,
): Double {
    val delta = subtract(end, start)
    val length = magnitude(delta)
    if (length == 0.0) return distance(start, point)
    val unit = scale(delta, 1.0 / length)
    val projection = dot(subtract(point, start), unit).coerceIn(0.0, length)
    return distance(point, add(start, scale(unit, projection)))
}

private fun subtract(
    a: Vec2,
    b: Vec2,
): Vec2 = Vec2(a.x - b.x, a.y - b.y)

private fun add(
    a: Vec2,
    b: Vec2,
): Vec2 = Vec2(a.x + b.x, a.y + b.y)

private fun scale(
    a: Vec2,
    factor: Double,
): Vec2 = Vec2(a.x * factor, a.y * factor)

private fun dot(
    a: Vec2,
    b: Vec2,
): Double =
    (a.x * b.x + a.y * b.y).also {
        require(it.isFinite()) { "Unrepresentable contact geometry" }
    }

private fun magnitude(a: Vec2): Double =
    hypot(a.x, a.y).also {
        require(it.isFinite()) { "Unrepresentable contact distance" }
    }

private fun distance(
    a: Vec2,
    b: Vec2,
): Double = magnitude(subtract(a, b))

package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.PredictionTermination
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.ShotEventType
import io.github.carrombot.core.physics.ShotSimulator
import io.github.carrombot.core.physics.SimulationResult
import kotlin.math.hypot

/** A physically verified proposal. Route identities are immutable and ordered after the striker. */
public class ContactSequenceShot internal constructor(
    public val candidate: CandidateShot,
    discIds: List<String>,
    public val simulation: SimulationResult,
) {
    public val discIds: List<String> = ownedCopy(discIds)
}

/** Internal geometric intent only; impulses and capture are always verified by ShotSimulator. */
internal class SequenceGeometry(
    private val request: ContactShotRequest,
    private val config: ContactShotConfig,
) {
    private val board = request.board

    fun validate(legal: List<Vec2>): Boolean {
        ContactShotGeometry(request, config).validateScene()
        val start = Circle(request.strikerPosition, board.striker.circle.radius)
        return request.strikerPosition in legal && board.bounds.contains(start) &&
            board.discs.none { length(delta(start.center, it.circle.center)) <= start.radius + it.circle.radius }
    }

    fun aperture(): Boolean {
        val target = board.discs.single { it.id == request.target.discId }
        val pocket = board.pockets.single { it.id == request.target.pocketId }
        val radius = pocket.circle.radius - target.circle.radius - config.clearance
        return radius > 0.0 && length(delta(request.pocketAim, pocket.circle.center)) <= radius
    }

    fun ghost(
        center: Vec2,
        outgoing: Vec2,
        radii: Double,
    ): Vec2? {
        val direction = delta(outgoing, center)
        val distance = length(direction)
        return if (distance == 0.0) null else delta(center, sequenceScale(direction, radii / distance))
    }

    fun clear(
        start: Vec2,
        end: Vec2,
        radius: Double,
        excluded: Set<String>,
        final: Boolean,
    ): Boolean {
        val blocked =
            board.discs.filter { it.id !in excluded }.any {
                segmentDistance(start, end, it.circle.center) <= radius + it.circle.radius + config.clearance
            }
        val captured =
            board.pockets.filter { !final || it.id != request.target.pocketId }.any {
                val aperture = it.circle.radius - radius
                aperture >= 0.0 && segmentDistance(start, end, it.circle.center) <= aperture + config.clearance
            }
        return board.bounds.contains(Circle(start, radius)) && board.bounds.contains(Circle(end, radius)) &&
            !blocked && !captured
    }

    fun launch(
        ghost: Vec2,
        normal: Vec2,
        family: ShotType,
        bodies: Int,
        walls: Int,
    ): CandidateShot? {
        val approach = delta(ghost, request.strikerPosition)
        val distance = length(approach)
        val projection = approach.x * normal.x + approach.y * normal.y
        require(projection.isFinite()) { "Unrepresentable approach" }
        if (distance == 0.0 || projection <= 0.0) return null
        val positioned =
            BoardState(
                board.bounds,
                board.pockets,
                board.discs,
                board.striker.copy(circle = Circle(request.strikerPosition, board.striker.circle.radius)),
            )
        return CandidateShot(
            request.id,
            family,
            ShotInput(positioned, sequenceScale(approach, config.powerModel.speedForPower(request.power) / distance)),
            request.power,
            listOf(request.target),
            TrajectoryConstraints(walls, bodies, walls, bodies),
        )
    }
}

internal data class ExpectedImpulse(
    val body: String,
    val target: String,
    val wall: Boolean = false,
)

internal fun verifiedSequence(
    candidate: CandidateShot,
    ids: List<String>,
    expected: List<ExpectedImpulse>,
    physics: PhysicsConfig,
): ContactSequenceShot? {
    val result = ShotSimulator().simulate(candidate.shot.boardState, candidate.shot, physics)
    val impulses = result.events.filter { it.type != ShotEventType.CAPTURE }
    val matches =
        impulses.size == expected.size &&
            impulses.zip(expected).all { (actual, intent) ->
                if (intent.wall) {
                    actual.type == ShotEventType.WALL_CONTACT &&
                        actual.bodyId == intent.body && actual.target == intent.target
                } else {
                    actual.type == ShotEventType.BODY_CONTACT &&
                        setOf(actual.bodyId, actual.target) == setOf(intent.body, intent.target)
                }
            }
    val target = candidate.targets.single()
    val captured =
        result.captures.any {
            it.bodyId == target.discId && it.pocketId == target.pocketId && it.kind != null
        }
    val completed = !result.strikerCaptured && result.prediction.termination == PredictionTermination.REST
    return if (matches && captured && completed) {
        ContactSequenceShot(candidate, ids, result)
    } else {
        null
    }
}

internal fun delta(
    a: Vec2,
    b: Vec2,
): Vec2 = Vec2(a.x - b.x, a.y - b.y)

internal fun sequenceScale(
    a: Vec2,
    factor: Double,
): Vec2 = Vec2(a.x * factor, a.y * factor)

internal fun length(a: Vec2): Double {
    val result = hypot(a.x, a.y)
    require(result.isFinite()) { "Unrepresentable route distance" }
    return result
}

private fun segmentDistance(
    start: Vec2,
    end: Vec2,
    point: Vec2,
): Double {
    val direction = delta(end, start)
    val distance = length(direction)
    if (distance == 0.0) return length(delta(point, start))
    val unit = sequenceScale(direction, 1.0 / distance)
    val offset = delta(point, start)
    val projection = offset.x * unit.x + offset.y * unit.y
    require(projection.isFinite()) { "Unrepresentable route projection" }
    val along = projection.coerceIn(0.0, distance)
    return length(delta(point, Vec2(start.x + unit.x * along, start.y + unit.y * along)))
}

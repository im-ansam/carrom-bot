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

/** A completed, capture-validated bank with its intended path and actual ordered collision events. */
public data class StrikerBankShot(
    public val candidate: CandidateShot,
    public val path: ReflectionPath,
    public val geometry: ContactGeometry,
    public val simulation: SimulationResult,
)

/**
 * One caller-ID bank request with explicit legal placements, wall order, calibration and physics.
 * Mirrors propose only; the real simulator must complete without foul, capture the requested target
 * and produce exactly the requested striker walls followed by one target impact. Additional body/
 * wall impulses, including post-impact banks, are conservatively rejected. Null means infeasible;
 * invalid scenes, nonrepresentable arithmetic and simulation failures propagate without catching.
 * No placement/power search, rules, resources, clocks or retained caller lists are introduced.
 */
public class StrikerBankGenerator {
    @Suppress("ReturnCount") // Feasibility guards avoid constructing invalid model values.
    public fun generate(
        request: ContactShotRequest,
        legalStrikerPositions: List<Vec2>,
        walls: List<BankWall>,
        config: ContactShotConfig,
        physics: PhysicsConfig,
    ): StrikerBankShot? {
        require(walls.size in 1..2) { "One or two walls required" }
        ContactShotGeometry(request, config).validateScene()
        if (request.strikerPosition !in legalStrikerPositions) return null
        val board = request.board
        val disc = board.discs.single { it.id == request.target.discId }
        val direction = bankSubtract(request.pocketAim, disc.circle.center)
        val length = bankLength(direction)
        if (length == 0.0) return null
        val ghost =
            bankSubtract(
                disc.circle.center,
                bankScale(direction, (disc.circle.radius + board.striker.circle.radius) / length),
            )
        val path =
            ReflectionPathGenerator().generate(
                board.bounds,
                board.striker.circle.radius,
                request.strikerPosition,
                ghost,
                walls,
            ) ?: return null
        val lastBounce = path.points[path.points.lastIndex - 1]
        val finalRequest = request.copy(strikerPosition = lastBounce)
        val contact =
            DirectShotGenerator().generate(finalRequest, listOf(lastBounce), config)
                ?: CutShotGenerator().generate(finalRequest, listOf(lastBounce), config) ?: return null
        if (!clearBankPath(path, board, config.clearance)) return null
        val start = Circle(request.strikerPosition, board.striker.circle.radius)
        val positioned = BoardState(board.bounds, board.pockets, board.discs, board.striker.copy(circle = start))
        val launch = bankSubtract(path.points[1], path.points[0])
        val velocity = bankScale(launch, config.powerModel.speedForPower(request.power) / bankLength(launch))
        val candidate =
            CandidateShot(
                request.id,
                if (walls.size == 1) ShotType.STRIKER_BANK else ShotType.DOUBLE_BANK,
                ShotInput(positioned, velocity),
                request.power,
                listOf(request.target),
                TrajectoryConstraints(walls.size, 1, walls.size, 1),
            )
        val simulation = ShotSimulator().simulate(positioned, candidate.shot, physics)
        if (!matchesBank(candidate, path, simulation)) return null
        return StrikerBankShot(candidate, path, contact.geometry, simulation)
    }
}

private fun clearBankPath(
    path: ReflectionPath,
    board: BoardState,
    margin: Double,
): Boolean =
    path.points.zipWithNext().withIndex().all { (index, segment) ->
        val (start, end) = segment
        val obstacles = if (index == path.points.size - 2) emptyList() else board.discs
        val blocked =
            obstacles.any {
                val exclusion = board.striker.circle.radius + it.circle.radius + margin
                bankSegmentDistance(start, end, it.circle.center) <= exclusion
            }
        val capture =
            board.pockets.any {
                val aperture = it.circle.radius - board.striker.circle.radius
                aperture >= 0.0 && bankSegmentDistance(start, end, it.circle.center) <= aperture + margin
            }
        !blocked && !capture
    }

private fun matchesBank(
    candidate: CandidateShot,
    path: ReflectionPath,
    result: SimulationResult,
): Boolean {
    val board = candidate.shot.boardState
    val target = candidate.targets.single()
    val impulses = result.events.filter { it.type != ShotEventType.CAPTURE }
    val wallsMatch =
        path.walls.withIndex().all { (index, wall) ->
            val event = impulses.getOrNull(index)
            event?.type == ShotEventType.WALL_CONTACT && event.bodyId == board.striker.id && event.target == wall.name
        }
    val impact = impulses.getOrNull(path.walls.size)
    val impactMatch =
        impact?.type == ShotEventType.BODY_CONTACT &&
            setOf(impact.bodyId, impact.target) == setOf(board.striker.id, target.discId)
    val captured =
        result.captures.any {
            it.bodyId == target.discId && it.pocketId == target.pocketId && it.kind != null
        }
    return result.prediction.termination == PredictionTermination.REST && !result.strikerCaptured && captured &&
        impulses.size == path.walls.size + 1 && wallsMatch && impactMatch
}

internal fun bankSubtract(
    a: Vec2,
    b: Vec2,
): Vec2 = Vec2(a.x - b.x, a.y - b.y)

private fun bankScale(
    a: Vec2,
    factor: Double,
): Vec2 = Vec2(a.x * factor, a.y * factor)

private fun bankLength(a: Vec2): Double {
    val length = hypot(a.x, a.y)
    require(length.isFinite()) { "Unrepresentable bank distance" }
    return length
}

private fun bankSegmentDistance(
    start: Vec2,
    end: Vec2,
    point: Vec2,
): Double {
    val delta = bankSubtract(end, start)
    val length = bankLength(delta)
    val unit = bankScale(delta, 1.0 / length)
    val offset = bankSubtract(point, start)
    val projection =
        (offset.x * unit.x + offset.y * unit.y)
            .also {
                require(it.isFinite()) { "Unrepresentable bank projection" }
            }.coerceIn(0.0, length)
    return bankLength(bankSubtract(point, Vec2(start.x + unit.x * projection, start.y + unit.y * projection)))
}

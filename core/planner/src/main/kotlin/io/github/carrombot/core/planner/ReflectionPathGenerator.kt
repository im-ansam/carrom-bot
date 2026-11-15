package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2

/** Physical rectangular rail names, matching ShotEvent wall targets. */
public enum class BankWall { LEFT, RIGHT, TOP, BOTTOM }

/** Owned board-mm centre path: start, ordered rail hits, then ghost centre. */
public class ReflectionPath internal constructor(
    walls: List<BankWall>,
    points: List<Vec2>,
) {
    public val walls: List<BankWall> = ownedCopy(walls)
    public val points: List<Vec2> = ownedCopy(points)
}

/**
 * Specular mirror proposals on radius-inset rails. No physical loss or capture is inferred.
 * One or two caller-ordered rails are required. Corners, rail endpoints, repeated adjacent
 * rails and paths meeting another rail first are conservatively infeasible (null).
 * Invalid radius/count or nonrepresentable arithmetic throws; lists are borrowed synchronously.
 */
public class ReflectionPathGenerator {
    @Suppress("ReturnCount") // Ordered geometric guards precede division and path construction.
    public fun generate(
        bounds: BoardBounds,
        radius: Double,
        start: Vec2,
        end: Vec2,
        walls: List<BankWall>,
    ): ReflectionPath? {
        require(radius.isFinite() && radius > 0.0) { "Invalid moving radius" }
        require(walls.size in 1..2) { "One or two walls required" }
        if (bounds.width <= radius * 2.0 || bounds.height <= radius * 2.0) return null
        val inset =
            BoardBounds(
                Vec2(bounds.min.x + radius, bounds.min.y + radius),
                Vec2(bounds.max.x - radius, bounds.max.y - radius),
            )
        val repeatedRail = walls.zipWithNext().any { it.first == it.second }
        if (!interior(inset, start) || !interior(inset, end) || repeatedRail) return null
        var image = end
        for (wall in walls.asReversed()) image = reflect(image, wall, inset)
        val points = mutableListOf(start)
        for (wall in walls) {
            val hit = intersection(points.last(), image, wall, inset) ?: return null
            points.add(hit)
            image = reflect(image, wall, inset)
        }
        points.add(end)
        return ReflectionPath(walls, points)
    }
}

private fun interior(
    bounds: BoardBounds,
    point: Vec2,
): Boolean = point.x > bounds.min.x && point.x < bounds.max.x && point.y > bounds.min.y && point.y < bounds.max.y

private fun rail(
    wall: BankWall,
    bounds: BoardBounds,
): Double =
    when (wall) {
        BankWall.LEFT -> bounds.min.x
        BankWall.RIGHT -> bounds.max.x
        BankWall.TOP -> bounds.min.y
        BankWall.BOTTOM -> bounds.max.y
    }

private fun vertical(wall: BankWall): Boolean = wall == BankWall.LEFT || wall == BankWall.RIGHT

private fun reflect(
    point: Vec2,
    wall: BankWall,
    bounds: BoardBounds,
): Vec2 {
    val edge = rail(wall, bounds)
    return if (vertical(wall)) Vec2(edge + (edge - point.x), point.y) else Vec2(point.x, edge + (edge - point.y))
}

private fun intersection(
    start: Vec2,
    image: Vec2,
    wall: BankWall,
    bounds: BoardBounds,
): Vec2? {
    val edge = rail(wall, bounds)
    val delta = bankSubtract(image, start)
    val denominator = if (vertical(wall)) delta.x else delta.y
    val numerator = edge - if (vertical(wall)) start.x else start.y
    val fraction = numerator / denominator
    if (!fraction.isFinite() || fraction <= 0.0 || fraction >= 1.0) return null
    val point =
        if (vertical(wall)) {
            Vec2(edge, start.y + delta.y * fraction)
        } else {
            Vec2(start.x + delta.x * fraction, edge)
        }
    val onRail =
        if (vertical(wall)) {
            point.y > bounds.min.y && point.y < bounds.max.y
        } else {
            point.x > bounds.min.x && point.x < bounds.max.x
        }
    return point.takeIf { onRail }
}

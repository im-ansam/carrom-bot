package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2

/** Stable corner order: left, right, top, bottom (X before Y). Normals point outward. */
internal enum class WallCollision(
    val normal: Vec2,
) {
    LEFT(Vec2(-1.0, 0.0)),
    RIGHT(Vec2(1.0, 0.0)),
    TOP(Vec2(0.0, -1.0)),
    BOTTOM(Vec2(0.0, 1.0)),
    ;

    fun gap(
        body: ContactBody,
        bounds: BoardBounds,
    ): Double {
        val center = body.circle.center
        val distance =
            when (this) {
                LEFT -> center.x - bounds.min.x
                RIGHT -> bounds.max.x - center.x
                TOP -> center.y - bounds.min.y
                BOTTOM -> bounds.max.y - center.y
            }
        return (distance - body.circle.radius).also { require(it.isFinite()) { "Unrepresentable wall geometry" } }
    }

    fun outward(vector: Vec2): Double = vector.x * normal.x + vector.y * normal.y
}

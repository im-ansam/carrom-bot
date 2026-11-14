package io.github.carrombot.core.model

/**
 * Finite double-precision vector. Board positions use millimetres, velocities mm/s;
 * screen positions use pixels only at [CoordinateTransform] boundaries.
 * Board origin is the top-left inner playing surface, +x right and +y down.
 * Equality is exact (including signed zero); numerical comparisons need caller-chosen tolerances.
 */
public data class Vec2(
    public val x: Double,
    public val y: Double,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Vector components must be finite" }
    }
}

/** Circle in board millimetres; radius must be finite and strictly positive. */
public data class Circle(
    public val center: Vec2,
    public val radius: Double,
) {
    init {
        require(radius.isFinite() && radius > 0.0) { "Radius must be finite and positive" }
    }
}

/** Inner playing rectangle in millimetres. Edges are inclusive; dimensions must be finite and positive. */
public data class BoardBounds(
    public val min: Vec2,
    public val max: Vec2,
) {
    init {
        require(width.isFinite() && width > 0.0 && height.isFinite() && height > 0.0) {
            "Bounds must have finite positive dimensions"
        }
    }

    public val width: Double get() = max.x - min.x
    public val height: Double get() = max.y - min.y

    /** True on edges, without an implicit tolerance. */
    public fun contains(point: Vec2): Boolean = point.x in min.x..max.x && point.y in min.y..max.y

    /** True only if the entire circle fits, including exact tangency to an edge. */
    public fun contains(circle: Circle): Boolean =
        circle.center.x - min.x >= circle.radius && max.x - circle.center.x >= circle.radius &&
            circle.center.y - min.y >= circle.radius && max.y - circle.center.y >= circle.radius
}

package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import kotlin.math.abs

/**
 * Owned, immutable projective mapping between original frame pixels and board millimetres.
 * Both axes point right/down. No clipping or rounding; points on the projective horizon
 * fail with [IllegalArgumentException]. Native objects are never retained.
 * Matrix indices and four-corner projective algebra use literal subscripts for readability.
 */
@Suppress("MagicNumber")
public class BoardTransform private constructor(
    private val forward: DoubleArray,
    private val inverse: DoubleArray,
    public val bounds: BoardBounds,
) {
    /** Maps an original frame pixel to board mm, including extrapolation outside the board. */
    public fun toBoard(screenPoint: Vec2): Vec2 {
        val normalized = map(inverse, screenPoint)
        return Vec2(bounds.min.x + normalized.x * bounds.width, bounds.min.y + normalized.y * bounds.height)
    }

    /** Maps board mm to original frame pixels, including extrapolation outside the board. */
    public fun toScreen(boardPoint: Vec2): Vec2 =
        map(forward, Vec2((boardPoint.x - bounds.min.x) / bounds.width, (boardPoint.y - bounds.min.y) / bounds.height))

    public companion object {
        private const val EPSILON = 1e-12

        /**
         * Four convex clockwise corners in screen coordinates, starting at board top-left.
         * Caller supplies physical bounds; images alone cannot establish millimetres or game orientation.
         * Rejects nonconvex, reversed, duplicate, collinear or numerically singular corners.
         */
        public fun fromCorners(
            corners: List<Vec2>,
            bounds: BoardBounds,
        ): BoardTransform {
            require(corners.size == 4) { "Exactly four corners required" }
            corners.indices.forEach { index ->
                val a = corners[index]
                val b = corners[(index + 1) % 4]
                val c = corners[(index + 2) % 4]
                require((b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > EPSILON) {
                    "Corners must be strictly convex and clockwise in screen coordinates"
                }
            }
            val a = corners[0]
            val b = corners[1]
            val c = corners[2]
            val d = corners[3]
            val dx = a.x - b.x + c.x - d.x
            val dy = a.y - b.y + c.y - d.y
            val denominator = (b.x - c.x) * (d.y - c.y) - (d.x - c.x) * (b.y - c.y)
            require(abs(denominator) > EPSILON) { "Singular quadrilateral" }
            val g = (dx * (d.y - c.y) - (d.x - c.x) * dy) / denominator
            val h = ((b.x - c.x) * dy - dx * (b.y - c.y)) / denominator
            val matrix =
                doubleArrayOf(
                    b.x - a.x + g * b.x,
                    d.x - a.x + h * d.x,
                    a.x,
                    b.y - a.y + g * b.y,
                    d.y - a.y + h * d.y,
                    a.y,
                    g,
                    h,
                    1.0,
                )
            require(matrix.all(Double::isFinite)) { "Nonfinite transform" }
            return BoardTransform(matrix, invert(matrix), bounds)
        }

        private fun invert(m: DoubleArray): DoubleArray {
            val result =
                doubleArrayOf(
                    m[4] * m[8] - m[5] * m[7],
                    m[2] * m[7] - m[1] * m[8],
                    m[1] * m[5] - m[2] * m[4],
                    m[5] * m[6] - m[3] * m[8],
                    m[0] * m[8] - m[2] * m[6],
                    m[2] * m[3] - m[0] * m[5],
                    m[3] * m[7] - m[4] * m[6],
                    m[1] * m[6] - m[0] * m[7],
                    m[0] * m[4] - m[1] * m[3],
                )
            val determinant = m[0] * result[0] + m[1] * result[3] + m[2] * result[6]
            require(determinant.isFinite() && abs(determinant) > EPSILON) { "Singular transform" }
            return DoubleArray(9) { result[it] / determinant }.also {
                require(it.all(Double::isFinite)) { "Nonfinite inverse" }
            }
        }

        private fun map(
            m: DoubleArray,
            point: Vec2,
        ): Vec2 {
            val w = m[6] * point.x + m[7] * point.y + m[8]
            require(w.isFinite() && abs(w) > EPSILON) { "Point lies on projective horizon" }
            return Vec2((m[0] * point.x + m[1] * point.y + m[2]) / w, (m[3] * point.x + m[4] * point.y + m[5]) / w)
        }
    }
}

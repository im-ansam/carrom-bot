package io.github.carrombot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    @Test
    fun `transform maps cropped board coordinates to pixels and back`() {
        val transform = CoordinateTransform(Vec2(10.0, 20.0), Vec2(100.0, 200.0), 2.5)
        assertEquals(Vec2(150.0, 275.0), transform.toScreen(Vec2(30.0, 50.0)))
        assertEquals(Vec2(10.0, 20.0), transform.toBoard(Vec2(100.0, 200.0)))
        for (point in listOf(Vec2(-1.25, 0.0), Vec2(735.123, 500.5), Vec2(0.0, 0.0))) {
            val restored = transform.toBoard(transform.toScreen(point))
            assertEquals(point.x, restored.x, 1e-10)
            assertEquals(point.y, restored.y, 1e-10)
        }
    }

    @Test
    fun `bounds include edges and tangent circles but exclude crossing circles`() {
        val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 80.0))
        assertTrue(bounds.contains(Vec2(100.0, 80.0)))
        assertTrue(bounds.contains(Circle(Vec2(10.0, 10.0), 10.0)))
        assertFalse(bounds.contains(Circle(Vec2(9.999, 10.0), 10.0)))
        assertFalse(bounds.contains(Vec2(-0.001, 0.0)))
        assertFalse(bounds.contains(Circle(Vec2(50.0, 40.0), 41.0)))
    }

    @Test
    fun `geometry has exact value equality and copy validates invariants`() {
        val vector = Vec2(1.0, 2.0)
        assertEquals(vector, Vec2(1.0, 2.0))
        assertEquals(vector.hashCode(), Vec2(1.0, 2.0).hashCode())
        assertEquals(Circle(vector, 3.0), Circle(Vec2(1.0, 2.0), 3.0))
        assertThrows(IllegalArgumentException::class.java) { vector.copy(x = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Circle(vector, 3.0).copy(radius = 0.0) }
    }

    @Test
    fun `nonfinite degenerate and overflowing geometry is rejected`() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Vec2(invalid, 0.0) }
            assertThrows(IllegalArgumentException::class.java) { Vec2(0.0, invalid) }
            assertThrows(IllegalArgumentException::class.java) { Circle(Vec2(0.0, 0.0), invalid) }
        }
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Circle(Vec2(0.0, 0.0), invalid) }
            assertThrows(IllegalArgumentException::class.java) {
                CoordinateTransform(Vec2(0.0, 0.0), Vec2(0.0, 0.0), invalid)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoardBounds(Vec2(0.0, 0.0), Vec2(0.0, 1.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoardBounds(Vec2(-Double.MAX_VALUE, 0.0), Vec2(Double.MAX_VALUE, 1.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoordinateTransform(Vec2(0.0, 0.0), Vec2(0.0, 0.0), 2.0).toScreen(Vec2(Double.MAX_VALUE, 0.0))
        }
    }
}

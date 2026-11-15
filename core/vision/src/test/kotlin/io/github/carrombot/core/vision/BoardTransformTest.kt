package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoardTransformTest {
    private val bounds = BoardBounds(Vec2(10.0, 20.0), Vec2(750.0, 760.0))
    private val corners = listOf(Vec2(120.0, 80.0), Vec2(700.0, 140.0), Vec2(650.0, 740.0), Vec2(70.0, 650.0))

    @Test
    fun `perspective maps all four corners to physical board bounds`() {
        val transform = BoardTransform.fromCorners(corners, bounds)
        val expected =
            listOf(bounds.min, Vec2(bounds.max.x, bounds.min.y), bounds.max, Vec2(bounds.min.x, bounds.max.y))
        corners.zip(expected).forEach { (screen, board) ->
            assertPoint(board, transform.toBoard(screen))
            assertPoint(screen, transform.toScreen(board))
        }
    }

    @Test
    fun `interior and exterior points round trip without clipping`() {
        val transform = BoardTransform.fromCorners(corners, bounds)
        listOf(Vec2(360.0, 370.0), Vec2(-20.0, 0.0), Vec2(800.0, 900.0)).forEach {
            assertPoint(it, transform.toBoard(transform.toScreen(it)))
        }
    }

    @Test
    fun `invalid or wrongly ordered quadrilaterals fail explicitly`() {
        listOf(
            corners.take(3),
            listOf(corners[0], corners[2], corners[1], corners[3]),
            listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(2.0, 0.0), Vec2(3.0, 0.0)),
            corners.reversed(),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { BoardTransform.fromCorners(invalid, bounds) }
        }
    }

    @Test
    fun `projective horizon fails rather than returning plausible coordinates`() {
        val trapezoid = listOf(Vec2(0.0, 0.0), Vec2(2.0, 0.0), Vec2(3.0, 2.0), Vec2(-1.0, 2.0))
        val transform = BoardTransform.fromCorners(trapezoid, bounds)
        assertThrows(IllegalArgumentException::class.java) { transform.toBoard(Vec2(0.0, -2.0)) }
    }

    @Test
    fun `transform owns its coefficients after caller mutates corners`() {
        val mutable = corners.toMutableList()
        val transform = BoardTransform.fromCorners(mutable, bounds)
        mutable.clear()
        assertPoint(bounds.min, transform.toBoard(corners.first()))
    }

    private fun assertPoint(
        expected: Vec2,
        actual: Vec2,
    ) {
        assertEquals(expected.x, actual.x, 1e-7)
        assertEquals(expected.y, actual.y, 1e-7)
    }
}

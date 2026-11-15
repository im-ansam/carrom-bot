package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReflectionPathGeneratorTest {
    private val bounds = BoardBounds(Vec2(10.0, 20.0), Vec2(110.0, 120.0))
    private val generator = ReflectionPathGenerator()

    @Test
    fun `translated radius inset reflections preserve requested order`() {
        val walls = mutableListOf(BankWall.LEFT, BankWall.TOP)
        val result = requireNotNull(generator.generate(bounds, 5.0, Vec2(40.0, 90.0), Vec2(50.0, 60.0), walls))
        assertEquals(Vec2(15.0, 48.33333333333333), result.points[1])
        assertEquals(29.0, result.points[2].x, 1e-10)
        assertEquals(25.0, result.points[2].y, 0.0)
        walls.clear()
        assertEquals(listOf(BankWall.LEFT, BankWall.TOP), result.walls)
        assertThrows(UnsupportedOperationException::class.java) { (result.walls as MutableList).clear() }
    }

    @Test
    fun `corners repeated walls out of bounds and invalid wall counts reject explicitly`() {
        val corner = listOf(BankWall.LEFT, BankWall.TOP)
        val repeated = listOf(BankWall.LEFT, BankWall.LEFT)
        assertNull(generator.generate(bounds, 5.0, Vec2(40.0, 50.0), Vec2(60.0, 70.0), corner))
        assertNull(generator.generate(bounds, 5.0, Vec2(40.0, 90.0), Vec2(50.0, 60.0), repeated))
        assertNull(generator.generate(bounds, 5.0, Vec2(11.0, 90.0), Vec2(50.0, 60.0), listOf(BankWall.LEFT)))
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(bounds, 5.0, Vec2(40.0, 90.0), Vec2(50.0, 60.0), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(bounds, Double.NaN, Vec2(40.0, 90.0), Vec2(50.0, 60.0), listOf(BankWall.LEFT))
        }
    }

    @Test
    fun `opposite rails are sequential and wrong first rail is infeasible`() {
        val result =
            requireNotNull(
                generator.generate(
                    bounds,
                    5.0,
                    Vec2(40.0, 70.0),
                    Vec2(60.0, 70.0),
                    listOf(BankWall.LEFT, BankWall.RIGHT),
                ),
            )
        assertEquals(listOf(Vec2(40.0, 70.0), Vec2(15.0, 70.0), Vec2(105.0, 70.0), Vec2(60.0, 70.0)), result.points)
        assertNull(
            generator.generate(
                bounds,
                5.0,
                Vec2(100.0, 30.0),
                Vec2(20.0, 110.0),
                listOf(BankWall.LEFT, BankWall.TOP),
            ),
        )
    }
}

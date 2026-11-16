package io.github.carrombot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLayoutTest {
    @Test
    fun `360 dp portrait source overlapping 240 dp controls is refused at every density`() {
        listOf(1, 2, 3).forEach { density ->
            assertFalse(
                DemoLayout.hasHorizontalClearance(
                    0,
                    164 * density,
                    360 * density,
                    240 * density,
                    16 * density,
                ),
            )
        }
    }

    @Test
    fun `480 dp disjoint source preserves required gap and includes measured source offset`() {
        assertTrue(DemoLayout.hasHorizontalClearance(0, 224, 480, 240, 16))
        assertFalse(DemoLayout.hasHorizontalClearance(1, 224, 480, 240, 16))
        assertFalse(DemoLayout.hasHorizontalClearance(Int.MAX_VALUE, Int.MAX_VALUE, 480, 240, 16))
    }
}

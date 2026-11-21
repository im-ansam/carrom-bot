package io.github.carrombot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLayoutTest {
    @Test
    fun `shrunk phone source refuses overlap with larger actual preview`() {
        assertFalse(DemoLayout.hasVerticalClearance(263, 412, 1600, 1084, 2.625f))
        assertFalse(DemoLayout.hasVerticalClearance(263, 412, 1605, 1084, 2.625f))
        assertTrue(DemoLayout.hasVerticalClearance(263, 412, 1606, 1084, 2.625f))
        assertFalse(DemoLayout.hasVerticalClearance(Int.MAX_VALUE, Int.MAX_VALUE, 1606, 1084, 2.625f))
        assertFalse(DemoLayout.hasVerticalClearance(263, 412, 1606, 80, 3f))
    }

    @Test
    fun `phone source shrinks to reserve measured controls and gap`() {
        val size = DemoLayout.fitSourceSize(500, 1084, 630, 42)
        assertEquals(412, size)
        assertTrue(DemoLayout.hasHorizontalClearance(0, size, 1084, 630, 42))
    }

    @Test
    fun `wide display retains preferred source size`() {
        assertEquals(500, DemoLayout.fitSourceSize(500, 1440, 630, 42))
    }

    @Test
    fun `insufficient space remains a positive layout dimension and fails clearance`() {
        val size = DemoLayout.fitSourceSize(500, 640, 630, 42)
        assertEquals(1, size)
        assertFalse(DemoLayout.hasHorizontalClearance(0, size, 640, 630, 42))
        assertEquals(1, DemoLayout.fitSourceSize(500, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
    }

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

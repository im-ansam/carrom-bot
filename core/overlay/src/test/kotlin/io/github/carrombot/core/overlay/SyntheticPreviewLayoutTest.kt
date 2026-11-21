package io.github.carrombot.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyntheticPreviewLayoutTest {
    @Test
    fun `phone preview keeps its actual width when source shrinks`() {
        val size = checkNotNull(SyntheticPreviewLayout.dimensions(1084, 2.625f))
        assertEquals(500, size.width)
        assertEquals(888, size.height)
        assertEquals(262, size.legendHeight)
        assertEquals(126, size.titleHeight)
        assertEquals(42, size.gap)
    }

    @Test
    fun `preview height sums separately rounded title and legend`() {
        val size = checkNotNull(SyntheticPreviewLayout.dimensions(1084, 1.3333f))
        assertEquals(521, size.width)
        assertEquals(717, size.height)
        assertEquals(size.width + size.legendHeight + size.titleHeight, size.height)
    }

    @Test
    fun `unusable or overflowing display metrics refuse preview`() {
        assertNull(SyntheticPreviewLayout.dimensions(80, 3f))
        assertNull(SyntheticPreviewLayout.dimensions(0, 1f))
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE).forEach {
            assertNull(SyntheticPreviewLayout.dimensions(1084, it))
        }
    }
}

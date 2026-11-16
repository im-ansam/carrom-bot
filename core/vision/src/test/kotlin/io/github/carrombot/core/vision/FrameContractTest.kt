package io.github.carrombot.core.vision

import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameContractTest {
    @Test
    fun `viewport rejects clipping and integer overflow without native loading`() {
        assertThrows(IllegalArgumentException::class.java) { FrameViewport(10, 0, 100, 100, 100) }
        assertThrows(IllegalArgumentException::class.java) { FrameViewport(Int.MAX_VALUE, 0, 100, 100, 100) }
        assertThrows(IllegalArgumentException::class.java) { FrameViewport(0, 0, 0, 100, 100) }
    }

    @Test
    fun `marker coordinates must be sampleable and RGB policy must be bounded`() {
        assertThrows(IllegalArgumentException::class.java) { FrameMarker(Vec2(1.0, 0.0), 0, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { FrameMarker(Vec2(0.0, 0.0), 256, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { FrameMarker(Vec2(0.0, 0.0), 0, 0, 0, -1) }
    }
}

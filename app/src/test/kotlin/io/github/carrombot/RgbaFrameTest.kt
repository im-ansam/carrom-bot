package io.github.carrombot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class RgbaFrameTest {
    @Test
    fun `copy excludes row padding and respects buffer position and pixel stride`() {
        val bytes = ByteArray(28) { it.toByte() }
        val buffer = ByteBuffer.wrap(bytes).apply { position(2) }
        val frame = RgbaFrame.copy(2, 2, 10, buffer, 12, 5)
        bytes.fill(0)
        assertArrayEquals(byteArrayOf(2, 3, 4, 5, 7, 8, 9, 10, 14, 15, 16, 17, 19, 20, 21, 22), frame.rgba)
        assertEquals(2, buffer.position())
        assertEquals(10L, frame.timestampNanos)
    }

    @Test
    fun `short buffer and excessive frame dimensions fail before allocation`() {
        assertThrows(IllegalArgumentException::class.java) {
            RgbaFrame.copy(2, 2, 0, ByteBuffer.allocate(15), 8, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RgbaFrame.copy(Int.MAX_VALUE, 2, 0, ByteBuffer.allocate(0), Int.MAX_VALUE, 4)
        }
    }

    @Test
    fun `overlapping rows and invalid timestamps are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RgbaFrame.copy(2, 2, 0, ByteBuffer.allocate(16), 4, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RgbaFrame.copy(1, 1, -1, ByteBuffer.allocate(4), 4, 4)
        }
    }
}

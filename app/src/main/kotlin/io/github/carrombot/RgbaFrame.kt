package io.github.carrombot

import java.nio.ByteBuffer

/** Owned packed RGBA bytes. Single-consumer transient storage, never persisted or logged. */
internal class RgbaFrame private constructor(
    val width: Int,
    val height: Int,
    val timestampNanos: Long,
    val rgba: ByteArray,
) {
    companion object {
        // Bounds copying/native allocations even if a platform supplies unreasonable dimensions.
        private const val MAX_PIXELS = 4_194_304L
        private const val CHANNELS = 4

        /** Copies a borrowed plane before its Image is closed; leaves position/limit unchanged. */
        @Suppress("LongParameterList")
        fun copy(
            width: Int,
            height: Int,
            timestampNanos: Long,
            source: ByteBuffer,
            rowStride: Int,
            pixelStride: Int,
        ): RgbaFrame {
            require(width > 0 && height > 0 && timestampNanos >= 0)
            val pixels = width.toLong() * height
            require(pixels <= MAX_PIXELS) { "Frame exceeds processing capacity" }
            require(pixelStride >= CHANNELS)
            val rowBytes = (width - 1).toLong() * pixelStride + CHANNELS
            require(rowStride >= rowBytes)
            val required = (height - 1).toLong() * rowStride + rowBytes
            require(required <= source.remaining()) { "Incomplete RGBA plane" }
            val input = source.duplicate()
            val start = input.position()
            val bytes = ByteArray((pixels * CHANNELS).toInt())
            var output = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    input.position(start + y * rowStride + x * pixelStride)
                    input.get(bytes, output, CHANNELS)
                    output += CHANNELS
                }
            }
            return RgbaFrame(width, height, timestampNanos, bytes)
        }
    }
}

package io.github.carrombot

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.overlay.BoardMapping
import io.github.carrombot.core.vision.BoardEstimate

/** Full-display pixels; rejects resized/app-only capture instead of guessing its screen origin. */
internal data class DemoViewport(
    val x: Int,
    val y: Int,
    val size: Int,
    val displayWidth: Int,
    val displayHeight: Int,
) {
    init {
        require(x >= 0 && y >= 0 && size >= MIN_SIZE)
        require(x.toLong() + size <= displayWidth && y.toLong() + size <= displayHeight)
    }

    companion object {
        private const val MIN_SIZE = 100
        private const val FIELD_COUNT = 5

        @Suppress("MagicNumber") // Explicit five-field internal Intent tuple.
        fun from(values: IntArray?): DemoViewport? {
            if (values == null || values.size != FIELD_COUNT) return null
            return try {
                DemoViewport(values[0], values[1], values[2], values[3], values[4])
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

internal data class AimingObservation(
    val board: BoardState? = null,
    val mapping: BoardMapping? = null,
    val reason: String = "Synthetic board not visible or not stationary",
)

/** App composition only; native allocation/processing/release is owned by core:vision. */
@Suppress("MagicNumber") // Explicit original synthetic marker and confidence calibration.
internal class SyntheticVision(
    viewport: DemoViewport,
) {
    private val vision =
        io.github.carrombot.core.vision.RgbaBoardEstimator(
            SyntheticProfile.bounds,
            io.github.carrombot.core.vision.FrameViewport(
                viewport.x,
                viewport.y,
                viewport.size,
                viewport.displayWidth,
                viewport.displayHeight,
            ),
            SyntheticProfile.profiles,
            SyntheticProfile.tracking,
            SyntheticProfile.inventory,
            0.75,
            listOf(
                io.github.carrombot.core.vision
                    .FrameMarker(Vec2(0.03, 0.03), 255, 0, 255, 10),
                io.github.carrombot.core.vision
                    .FrameMarker(Vec2(0.97, 0.03), 0, 255, 255, 10),
            ),
        )

    fun observe(
        frame: RgbaFrame,
        nowNanos: Long,
    ): AimingObservation {
        val result = vision.observe(frame.width, frame.height, frame.rgba, frame.timestampNanos, nowNanos)
        return when (val estimate = result.estimate) {
            is BoardEstimate.Ready -> {
                AimingObservation(
                    estimate.board,
                    BoardMapping(estimate.board.bounds, checkNotNull(result.transform)),
                )
            }

            is BoardEstimate.Unavailable -> {
                AimingObservation(reason = "Synthetic board: ${estimate.reason}")
            }
        }
    }
}

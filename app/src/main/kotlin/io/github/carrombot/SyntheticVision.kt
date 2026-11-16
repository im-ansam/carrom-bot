package io.github.carrombot

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.CoordinateTransform
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.overlay.BoardMapping
import io.github.carrombot.core.vision.BoardDetection
import io.github.carrombot.core.vision.BoardDetector
import io.github.carrombot.core.vision.BoardEstimate
import io.github.carrombot.core.vision.BoardStateEstimator
import io.github.carrombot.core.vision.ObjectDetector
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import kotlin.math.abs

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

/** One vision-worker owner per aiming run. All Mats are call-local and released before returning. */
@Suppress("MagicNumber", "ReturnCount")
internal class SyntheticVision(
    private val viewport: DemoViewport,
) {
    private val detector = BoardDetector.create(SyntheticProfile.bounds)
    private val objects = ObjectDetector.create(SyntheticProfile.profiles)
    private var estimator: BoardStateEstimator? = null
    private var previousMapping: BoardMapping? = null

    @Suppress("NestedBlockDepth") // Nested finally blocks express native Mat ownership at each boundary.
    fun observe(
        frame: RgbaFrame,
        nowNanos: Long,
    ): AimingObservation {
        if (frame.width != viewport.displayWidth || frame.height != viewport.displayHeight) return unavailable()
        val full = Mat(frame.height, frame.width, CvType.CV_8UC4)
        try {
            full.put(0, 0, frame.rgba)
            val crop = full.submat(Rect(viewport.x, viewport.y, viewport.size, viewport.size))
            try {
                // Marker lives outside the playing surface. Never apply this profile to arbitrary content.
                if (!fixtureMarker(crop)) return unavailable()
                val geometry = detector.detect(crop) as? BoardDetection.Found ?: return unavailable()
                val corners = geometry.corners
                val width = corners[1].x - corners[0].x
                val height = corners[3].y - corners[0].y
                if (width <= 0 || abs(width - height) > 2 || !axisAligned(corners)) {
                    return unavailable()
                }
                val mapping =
                    BoardMapping(
                        SyntheticProfile.bounds,
                        CoordinateTransform(
                            SyntheticProfile.bounds.min,
                            Vec2(viewport.x + corners[0].x, viewport.y + corners[0].y),
                            width / 400.0,
                        ),
                    )
                if (mapping != previousMapping) {
                    estimator =
                        BoardStateEstimator(
                            SyntheticProfile.bounds,
                            SyntheticProfile.tracking,
                            SyntheticProfile.inventory,
                            0.75,
                        )
                    previousMapping = mapping
                }
                val normalized = detector.normalize(crop, geometry, 401)
                try {
                    val observation = objects.detect(normalized, SyntheticProfile.bounds)
                    val estimate = checkNotNull(estimator).update(observation, frame.timestampNanos, nowNanos)
                    return when (estimate) {
                        is BoardEstimate.Ready -> {
                            AimingObservation(estimate.board, mapping)
                        }

                        is BoardEstimate.Unavailable -> {
                            AimingObservation(reason = "Synthetic board: ${estimate.reason}")
                        }
                    }
                } finally {
                    normalized.release()
                }
            } finally {
                crop.release()
            }
        } finally {
            full.release()
        }
    }

    private fun axisAligned(corners: List<Vec2>): Boolean =
        listOf(0 to 1, 2 to 3).all { (a, b) -> abs(corners[a].y - corners[b].y) <= 2 } &&
            listOf(0 to 3, 1 to 2).all { (a, b) -> abs(corners[a].x - corners[b].x) <= 2 }

    private fun unavailable(): AimingObservation {
        estimator = null
        previousMapping = null
        return AimingObservation()
    }

    private fun fixtureMarker(crop: Mat): Boolean {
        val markers = listOf(0.03 to doubleArrayOf(255.0, 0.0, 255.0), 0.97 to doubleArrayOf(0.0, 255.0, 255.0))
        return markers.all { (fraction, color) ->
            val pixel = crop.get((crop.rows() * 0.03).toInt(), (crop.cols() * fraction).toInt())
            pixel != null && color.indices.all { abs(pixel[it] - color[it]) <= 10.0 }
        }
    }
}

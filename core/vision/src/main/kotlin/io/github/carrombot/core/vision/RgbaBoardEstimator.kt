package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.CoordinateTransform
import io.github.carrombot.core.model.Vec2
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import kotlin.math.abs

/** Caller-confirmed square viewport in full-display pixels. App-only/resized capture is rejected. */
public data class FrameViewport(
    public val x: Int,
    public val y: Int,
    public val size: Int,
    public val displayWidth: Int,
    public val displayHeight: Int,
) {
    init {
        require(x >= 0 && y >= 0 && size > 0)
        require(x.toLong() + size <= displayWidth && y.toLong() + size <= displayHeight)
    }
}

/** Caller-defined RGB source marker at a normalized viewport position, with inclusive channel tolerance. */
public data class FrameMarker(
    public val position: Vec2,
    public val red: Int,
    public val green: Int,
    public val blue: Int,
    public val tolerance: Int,
) {
    init {
        require(position.x >= 0.0 && position.x < 1.0 && position.y >= 0.0 && position.y < 1.0)
        require(listOf(red, green, blue, tolerance).all { it in 0..MAX_CHANNEL })
    }

    private companion object {
        const val MAX_CHANNEL = 255
    }
}

/** Owned estimates and full-display geometry; native storage and pixel buffers never escape vision. */
public class RgbaBoardObservation internal constructor(
    public val estimate: BoardEstimate,
    public val transform: CoordinateTransform? = null,
)

/**
 * Single-worker, synchronous RGBA-to-board adapter. Configuration is caller-calibrated, without defaults.
 * Packed owned RGBA bytes are borrowed unchanged only during observe; do not mutate them concurrently.
 * All native allocation, crops, rectification and deterministic release remain in core:vision.
 * A changed/missing geometry or source marker resets tracking. Supports axis-aligned square boards only.
 * Invalid timestamps throw; invalid pixels/geometry withhold readiness. No screenshots are persisted.
 */
@Suppress("MagicNumber", "ReturnCount") // RGBA channels, square geometry indices and bounded image dimensions.
public class RgbaBoardEstimator(
    private val bounds: BoardBounds,
    private val viewport: FrameViewport,
    profiles: List<ObjectProfile>,
    private val tracking: TrackingConfig,
    expectedInventory: Map<ObjectKind, Int>,
    private val minConfidence: Double,
    markers: List<FrameMarker>,
) {
    private val detector = BoardDetector.create(bounds)
    private val objects = ObjectDetector.create(profiles)
    private val inventory = expectedInventory.toMap()
    private val markers = markers.toList()
    private var estimator: BoardStateEstimator? = null
    private var previousTransform: CoordinateTransform? = null

    init {
        require(bounds.width == bounds.height) { "Rectification requires square physical bounds" }
        // Validate policy immediately, even when no valid source is visible yet.
        BoardStateEstimator(bounds, tracking, inventory, minConfidence)
    }

    @Suppress("NestedBlockDepth") // Each native allocation has its own finally boundary.
    public fun observe(
        width: Int,
        height: Int,
        rgba: ByteArray,
        timestampNanos: Long,
        nowNanos: Long,
    ): RgbaBoardObservation {
        require(timestampNanos >= 0 && nowNanos >= timestampNanos)
        val pixels = width.toLong() * height
        if (width != viewport.displayWidth || height != viewport.displayHeight) return unavailable()
        if (pixels !in 1..MAX_PIXELS || rgba.size.toLong() != pixels * 4) return unavailable()
        val full = Mat(height, width, CvType.CV_8UC4)
        try {
            full.put(0, 0, rgba)
            val crop = full.submat(Rect(viewport.x, viewport.y, viewport.size, viewport.size))
            try {
                if (!markersMatch(crop)) return unavailable()
                val geometry = detector.detect(crop) as? BoardDetection.Found ?: return unavailable()
                val corners = geometry.corners
                val boardWidth = corners[1].x - corners[0].x
                val boardHeight = corners[3].y - corners[0].y
                if (boardWidth <= 0 || abs(boardWidth - boardHeight) > 2 || !axisAligned(corners)) {
                    return unavailable()
                }
                val transform =
                    CoordinateTransform(
                        bounds.min,
                        Vec2(viewport.x + corners[0].x, viewport.y + corners[0].y),
                        boardWidth / bounds.width,
                    )
                if (transform != previousTransform) {
                    estimator = BoardStateEstimator(bounds, tracking, inventory, minConfidence)
                    previousTransform = transform
                }
                val normalized = detector.normalize(crop, geometry, NORMALIZED_SIZE)
                try {
                    val observation = objects.detect(normalized, bounds)
                    val estimate = checkNotNull(estimator).update(observation, timestampNanos, nowNanos)
                    return RgbaBoardObservation(estimate, transform.takeIf { estimate is BoardEstimate.Ready })
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

    private fun unavailable(): RgbaBoardObservation {
        estimator = null
        previousTransform = null
        return RgbaBoardObservation(BoardEstimate.Unavailable(BoardUnavailableReason.INVALID_FRAME))
    }

    private fun axisAligned(corners: List<Vec2>): Boolean =
        listOf(0 to 1, 2 to 3).all { (a, b) -> abs(corners[a].y - corners[b].y) <= 2 } &&
            listOf(0 to 3, 1 to 2).all { (a, b) -> abs(corners[a].x - corners[b].x) <= 2 }

    private fun markersMatch(crop: Mat): Boolean =
        markers.all { marker ->
            val pixel = crop.get((crop.rows() * marker.position.y).toInt(), (crop.cols() * marker.position.x).toInt())
            val channels = listOf(marker.red, marker.green, marker.blue)
            pixel != null && channels.indices.all { abs(pixel[it] - channels[it]) <= marker.tolerance }
        }

    private companion object {
        const val MAX_PIXELS = 4_194_304L
        const val NORMALIZED_SIZE = 401
    }
}

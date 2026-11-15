package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Collections
import kotlin.math.abs
import kotlin.math.hypot

/** Explicit detection outcome; no guessed geometry is returned on failure. */
public sealed interface BoardDetection {
    /** Owned geometry with no reference to input pixels or native buffers. */
    public class Found internal constructor(
        corners: List<Vec2>,
        public val transform: BoardTransform,
        /** Geometric quality in [0,1], NOT a calibrated probability that this is a carrom board. */
        public val confidence: Double,
    ) : BoardDetection {
        /** Clockwise original pixels; near-minimum x+y corners break ties by y then x. */
        public val corners: List<Vec2> = Collections.unmodifiableList(corners.toList())
    }

    public data class Failed(
        public val reason: BoardDetectionFailure,
    ) : BoardDetection
}

public enum class BoardDetectionFailure {
    INVALID_FRAME,
    BOARD_NOT_FOUND,
    AMBIGUOUS_BOARD,
    OPENCV_ERROR,
}

/**
 * Detects a complete high-contrast, approximately square playing boundary. Screen axes define
 * orientation; symmetric boards cannot reveal player orientation. Supply calibrated physical
 * [bounds]. Clipped/occluded or strongly foreshortened boards are deliberately unsupported.
 * This geometric detector does not classify arbitrary square UI panels as non-boards.
 *
 * Call [create] once after consent setup. Calls borrow CV_8UC4 RGBA Mats synchronously without
 * mutation/retention; caller owns input and must not mutate it during a call. No frame queue,
 * pixel persistence or native state is retained. Safe for independent frames on separate threads.
 */
public class BoardDetector private constructor(
    private val bounds: BoardBounds,
) {
    /** Native/OpenCV runtime errors become explicit failures; cancellation is never intercepted. */
    public fun detect(rgba: Mat): BoardDetection {
        if (!validFrame(rgba)) return BoardDetection.Failed(BoardDetectionFailure.INVALID_FRAME)
        return try {
            findBoard(rgba)
        } catch (_: org.opencv.core.CvException) {
            BoardDetection.Failed(BoardDetectionFailure.OPENCV_ERROR)
        }
    }

    /**
     * Returns a new RGBA square of [size] pixels (2..2048), with board bounds at pixel 0..size-1.
     * Caller MUST release the result. Input remains borrowed and unchanged. [geometry] must refer
     * to this frame. Native errors propagate; partially allocated output is released on failure.
     */
    public fun normalize(
        rgba: Mat,
        geometry: BoardDetection.Found,
        size: Int,
    ): Mat {
        require(validFrame(rgba)) { "Expected nonempty CV_8UC4 RGBA frame" }
        require(size in 2..MAX_NORMALIZED_SIZE) { "Unsupported normalized size" }
        val source = MatOfPoint2f()
        val edge = (size - 1).toDouble()
        val destination = MatOfPoint2f(Point(0.0, 0.0), Point(edge, 0.0), Point(edge, edge), Point(0.0, edge))
        val output = Mat()
        var succeeded = false
        try {
            source.fromList(geometry.corners.map { Point(it.x, it.y) })
            val homography = Imgproc.getPerspectiveTransform(source, destination)
            try {
                Imgproc.warpPerspective(rgba, output, homography, Size(size.toDouble(), size.toDouble()))
            } finally {
                homography.release()
            }
            succeeded = true
            return output
        } finally {
            if (!succeeded) output.release()
            source.release()
            destination.release()
        }
    }

    @Suppress("ReturnCount") // Explicit failure exits keep native cleanup in one finally block.
    private fun findBoard(rgba: Mat): BoardDetection {
        val edges = FramePreprocessor.edges(rgba)
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val candidates =
                contours.mapNotNull { candidate(it, rgba.cols(), rgba.rows()) }.sortedByDescending { it.area }
            val best = candidates.firstOrNull() ?: return BoardDetection.Failed(BoardDetectionFailure.BOARD_NOT_FOUND)
            // Canny creates two contours per edge; nested rail edges count as the same candidate.
            if (candidates.any { other -> !sameBoundary(best, other) }) {
                return BoardDetection.Failed(BoardDetectionFailure.AMBIGUOUS_BOARD)
            }
            return BoardDetection.Found(best.corners, BoardTransform.fromCorners(best.corners, bounds), best.quality)
        } finally {
            edges.release()
            hierarchy.release()
            contours.forEach(Mat::release)
        }
    }

    @Suppress("ReturnCount", "MagicNumber") // Four-corner geometry, with early rejection of invalid contours.
    private fun candidate(
        contour: MatOfPoint,
        width: Int,
        height: Int,
    ): Candidate? {
        val curve = MatOfPoint2f()
        val polygon = MatOfPoint2f()
        try {
            curve.fromList(contour.toList())
            Imgproc.approxPolyDP(curve, polygon, Imgproc.arcLength(curve, true) * APPROXIMATION, true)
            val points = polygon.toArray().map { Vec2(it.x, it.y) }
            if (points.size != 4 || points.any { nearBorder(it, width, height) }) {
                return null
            }
            val center = Vec2(points.sumOf { it.x } / 4, points.sumOf { it.y } / 4)
            val ordered = points.sortedBy { kotlin.math.atan2(it.y - center.y, it.x - center.x) }
            val start = firstCorner(ordered)
            val corners = List(4) { ordered[(start + it) % 4] }
            if (!convex(corners)) return null
            val area = abs(corners.indices.sumOf { i -> cross(corners[i], corners[(i + 1) % 4]) }) / 2
            if (area / (width.toDouble() * height) !in MIN_AREA..MAX_AREA) return null
            val lengths = corners.indices.map { distance(corners[it], corners[(it + 1) % 4]) }
            val ratio = lengths.min() / lengths.max()
            if (ratio < MIN_SIDE_RATIO) return null
            val maxCosine =
                corners.indices.maxOf { i -> cornerCosine(corners[(i + 3) % 4], corners[i], corners[(i + 1) % 4]) }
            if (maxCosine > MAX_COSINE) return null
            return Candidate(corners, area, (ratio * (1 - maxCosine)).coerceIn(0.0, 1.0))
        } finally {
            curve.release()
            polygon.release()
        }
    }

    private fun sameBoundary(
        a: Candidate,
        b: Candidate,
    ): Boolean {
        val tolerance = kotlin.math.sqrt(a.area) * DUPLICATE_DISTANCE
        return b.corners.indices.any { shift ->
            a.corners.indices.all { index ->
                distance(a.corners[index], b.corners[(index + shift) % b.corners.size]) <= tolerance
            }
        }
    }

    private fun firstCorner(ordered: List<Vec2>): Int {
        val score = ordered.minOf { it.x + it.y }
        val shortestSide = ordered.indices.minOf { distance(ordered[it], ordered[(it + 1) % ordered.size]) }
        // Avoid a 90-degree change from subpixel/contour jitter near 45-degree boards.
        val tolerance = shortestSide * CORNER_TIE_TOLERANCE
        return ordered.indices
            .filter { ordered[it].x + ordered[it].y <= score + tolerance }
            .minWith(compareBy({ ordered[it].y }, { ordered[it].x }))
    }

    private data class Candidate(
        val corners: List<Vec2>,
        val area: Double,
        val quality: Double,
    )

    public companion object {
        private const val APPROXIMATION = 0.02
        private const val BORDER = 2.0
        private const val MIN_AREA = 0.10
        private const val MAX_AREA = 0.90
        private const val MIN_SIDE_RATIO = 0.65
        private const val MAX_COSINE = 0.35
        private const val DUPLICATE_DISTANCE = 0.08
        private const val CORNER_TIE_TOLERANCE = 0.02
        private const val MAX_NORMALIZED_SIZE = 2048

        /** Loads the official packaged native runtime; throws if unavailable, never fakes detection. */
        public fun create(bounds: BoardBounds): BoardDetector {
            check(OpenCVLoader.initLocal()) { "OpenCV native initialization failed" }
            return BoardDetector(bounds)
        }

        private fun validFrame(frame: Mat): Boolean {
            val isRgba = frame.type() == CvType.CV_8UC4
            return !frame.empty() && frame.dims() == 2 && isRgba
        }

        private fun nearBorder(
            point: Vec2,
            width: Int,
            height: Int,
        ): Boolean = point.x <= BORDER || point.y <= BORDER || point.x >= width - BORDER || point.y >= height - BORDER

        @Suppress("MagicNumber") // Four clockwise vertices.
        private fun convex(corners: List<Vec2>): Boolean =
            corners.indices.all { i ->
                val a = corners[i]
                val b = corners[(i + 1) % 4]
                val c = corners[(i + 2) % 4]
                (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 0.0
            }

        private fun cross(
            a: Vec2,
            b: Vec2,
        ): Double = a.x * b.y - a.y * b.x

        private fun distance(
            a: Vec2,
            b: Vec2,
        ): Double = hypot(a.x - b.x, a.y - b.y)

        private fun cornerCosine(
            a: Vec2,
            b: Vec2,
            c: Vec2,
        ): Double = abs((a.x - b.x) * (c.x - b.x) + (a.y - b.y) * (c.y - b.y)) / (distance(a, b) * distance(c, b))
    }
}

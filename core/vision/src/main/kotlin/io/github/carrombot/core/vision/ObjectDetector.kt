package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Vec2
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.hypot

/**
 * Synchronous color/contour recognition on an already rectified CV_8UC4 RGBA playing surface.
 * Pixel (0,0)..(cols-1,rows-1) must map to [BoardBounds.min]..[BoardBounds.max], with uniform
 * physical scale (within 1%). Caller must supply matching geometry; unrectified input cannot
 * be identified automatically. Centers map each axis independently; radii use the smaller
 * pixels/mm scale for a conservative enclosing circle with slightly rounded dimensions.
 * Borrows input without mutation, retention or release. All scratch
 * native memory is released per call; owned results survive release of input. Calls may run
 * independently on separate threads. Alpha is ignored; supply opaque captured/normalized pixels.
 *
 * Rejects clipped, noncircular and conflicting candidates rather than splitting occlusions or
 * inventing missing objects. Small occlusions may pass shape tolerances; this is not proof of
 * full visibility. A matching circular decoration can be a false positive. Profiles require
 * empirical calibration. No state, tracking IDs, default striker or board snapshot is produced.
 */
public class ObjectDetector private constructor(
    profiles: List<ObjectProfile>,
) {
    private val profiles = profiles.toList()

    @Suppress("ReturnCount") // Explicit invalid input and geometry outcomes before native work.
    public fun detect(
        rgba: Mat,
        bounds: BoardBounds,
    ): ObjectDetection {
        if (!validFrame(rgba)) {
            return ObjectDetection.Failed(ObjectDetectionFailure.INVALID_FRAME)
        }
        val scale = (rgba.cols() - 1) / bounds.width
        val verticalScale = (rgba.rows() - 1) / bounds.height
        if (!compatibleScale(scale, verticalScale)) {
            return ObjectDetection.Failed(ObjectDetectionFailure.INCOMPATIBLE_GEOMETRY)
        }
        return try {
            observe(rgba, bounds, scale, verticalScale)
        } catch (_: org.opencv.core.CvException) {
            ObjectDetection.Failed(ObjectDetectionFailure.OPENCV_ERROR)
        }
    }

    private fun observe(
        rgba: Mat,
        bounds: BoardBounds,
        scale: Double,
        verticalScale: Double,
    ): ObjectDetection.Observed {
        val rgb = Mat()
        val hsv = Mat()
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val candidates = profiles.flatMap { candidates(hsv, it) }.distinct()
            // Reject BOTH candidates when color profiles overlap or different-color contours touch.
            // No priority order or guessed winner; even same-class near duplicates are ambiguous.
            val accepted = candidates.filter { a -> candidates.none { b -> a !== b && intersects(a, b) } }
            return ObjectDetection.Observed(
                accepted.sortedWith(compareBy({ it.center.y }, { it.center.x }, { it.kind.ordinal })).map {
                    Detection(
                        it.kind,
                        Circle(
                            Vec2(bounds.min.x + it.center.x / scale, bounds.min.y + it.center.y / verticalScale),
                            it.radius / minOf(scale, verticalScale),
                        ),
                        it.confidence,
                    )
                },
            )
        } finally {
            rgb.release()
            hsv.release()
        }
    }

    private fun candidates(
        hsv: Mat,
        profile: ObjectProfile,
    ): List<Candidate> {
        val mask = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            colorMask(hsv, profile.color, mask)
            // External contours avoid treating rings/holes as independent discs. Fill uses all
            // foreground pixels inside the contour, so a hollow decoration cannot pass as solid.
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            return contours.mapNotNull { candidate(it, mask, profile) }
        } finally {
            mask.release()
            hierarchy.release()
            contours.forEach(Mat::release)
        }
    }

    private fun colorMask(
        hsv: Mat,
        color: HsvRange,
        mask: Mat,
    ) {
        val wraps = color.minHue > color.maxHue
        val upperHue = if (wraps) MAX_HUE else color.maxHue
        Core.inRange(
            hsv,
            Scalar(color.minHue.toDouble(), color.minSaturation.toDouble(), color.minValue.toDouble()),
            Scalar(upperHue.toDouble(), color.maxSaturation.toDouble(), color.maxValue.toDouble()),
            mask,
        )
        if (wraps) {
            val low = Mat()
            try {
                Core.inRange(
                    hsv,
                    Scalar(0.0, color.minSaturation.toDouble(), color.minValue.toDouble()),
                    Scalar(color.maxHue.toDouble(), color.maxSaturation.toDouble(), color.maxValue.toDouble()),
                    low,
                )
                Core.bitwise_or(mask, low, mask)
            } finally {
                low.release()
            }
        }
    }

    @Suppress("ReturnCount", "MagicNumber") // Circle formula and 8-bit mask; one native cleanup block.
    private fun candidate(contour: MatOfPoint, mask: Mat, profile: ObjectProfile): Candidate? {
        val curve = MatOfPoint2f()
        val region = Mat()
        try {
            curve.fromList(contour.toList())
            val center = Point()
            val radii = FloatArray(1)
            Imgproc.minEnclosingCircle(curve, center, radii)
            val radius = radii[0].toDouble()
            val fraction = radius / (minOf(mask.rows(), mask.cols()) - 1)
            if (fraction !in profile.minRadiusFraction..profile.maxRadiusFraction) return null
            val box = Imgproc.boundingRect(contour)
            if (touchesBorder(box, mask)) return null
            if (profile.kind == ObjectKind.POCKET && !atCorner(center, mask, profile.cornerFraction)) return null
            val perimeter = Imgproc.arcLength(curve, true)
            if (perimeter <= 0.0) return null
            val circularity = (4 * PI * Imgproc.contourArea(contour) / (perimeter * perimeter)).coerceAtMost(1.0)
            if (circularity < profile.minCircularity) return null
            region.create(mask.rows(), mask.cols(), CvType.CV_8UC1)
            region.setTo(Scalar(0.0))
            Imgproc.drawContours(region, listOf(contour), 0, Scalar(255.0), -1)
            Core.bitwise_and(region, mask, region)
            val fill = (Core.countNonZero(region) / (PI * radius * radius)).coerceAtMost(1.0)
            if (fill < profile.minFill) return null
            return Candidate(profile.kind, Vec2(center.x, center.y), radius, circularity * fill)
        } finally {
            curve.release()
            region.release()
        }
    }

    private fun atCorner(
        center: Point,
        frame: Mat,
        fraction: Double,
    ): Boolean =
        minOf(center.x, frame.cols() - 1 - center.x) <= (frame.cols() - 1) * fraction &&
            minOf(center.y, frame.rows() - 1 - center.y) <= (frame.rows() - 1) * fraction

    private fun validFrame(frame: Mat): Boolean {
        val dimensions = frame.dims() == 2 && frame.cols() >= 2 && frame.rows() >= 2
        return !frame.empty() && dimensions && frame.type() == CvType.CV_8UC4
    }

    private fun compatibleScale(
        horizontal: Double,
        vertical: Double,
    ): Boolean =
        horizontal.isFinite() && vertical.isFinite() &&
            kotlin.math.abs(horizontal / vertical - 1.0) <= SCALE_TOLERANCE

    private fun touchesBorder(
        box: Rect,
        frame: Mat,
    ): Boolean {
        val startOnBorder = box.x <= 0 || box.y <= 0
        val endOnBorder = box.x + box.width >= frame.cols() || box.y + box.height >= frame.rows()
        return startOnBorder || endOnBorder
    }

    private fun intersects(
        a: Candidate,
        b: Candidate,
    ): Boolean = hypot(a.center.x - b.center.x, a.center.y - b.center.y) <= a.radius + b.radius

    private data class Candidate(
        val kind: ObjectKind,
        val center: Vec2,
        val radius: Double,
        val confidence: Double,
    )

    public companion object {
        private const val SCALE_TOLERANCE = 0.01
        private const val MAX_HUE = 179

        /** Explicit native initialization; throws if unavailable. Profiles are defensively copied. */
        public fun create(profiles: List<ObjectProfile>): ObjectDetector {
            require(profiles.isNotEmpty()) { "At least one calibrated profile is required" }
            check(OpenCVLoader.initLocal()) { "OpenCV native initialization failed" }
            return ObjectDetector(profiles)
        }
    }
}

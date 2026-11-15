package io.github.carrombot.core.vision

import io.github.carrombot.core.model.Circle
import java.util.Collections

/** Visual class only; no identity, ownership, velocity or game legality is inferred. */
public enum class ObjectKind {
    POCKET,
    BLACK_DISC,
    WHITE_DISC,
    QUEEN,
    STRIKER,
    OBSTACLE,
}

/** OpenCV HSV inclusive intervals. Hue is 0..179; minHue > maxHue wraps through 179/0. */
@Suppress("MagicNumber") // OpenCV's documented HSV channel limits.
public data class HsvRange(
    public val minHue: Int,
    public val maxHue: Int,
    public val minSaturation: Int,
    public val maxSaturation: Int,
    public val minValue: Int,
    public val maxValue: Int,
) {
    init {
        require(minHue in 0..179 && maxHue in 0..179)
        require(minSaturation in 0..255 && maxSaturation in minSaturation..255)
        require(minValue in 0..255 && maxValue in minValue..255)
    }
}

/**
 * Caller-calibrated appearance, with radius as a fraction of the shorter frame edge (size-1).
 * Defaults deliberately reject incomplete circles. Obstacles may use lower circularity/fill,
 * in which case their circle is a conservative enclosing bound, not their physical shape.
 * Pockets additionally require centers in a corner square of [cornerFraction] edge length.
 * No built-in colors claim to describe an actual game skin.
 */
@Suppress("MagicNumber") // Fractions and conservative shape gates in the public calibration contract.
public data class ObjectProfile(
    public val kind: ObjectKind,
    public val color: HsvRange,
    public val minRadiusFraction: Double,
    public val maxRadiusFraction: Double,
    public val minCircularity: Double = 0.80,
    public val minFill: Double = 0.90,
    public val cornerFraction: Double = 0.15,
) {
    init {
        require(minRadiusFraction.isFinite() && minRadiusFraction > 0.0)
        require(maxRadiusFraction.isFinite() && maxRadiusFraction >= minRadiusFraction && maxRadiusFraction < 0.5)
        require(minCircularity.isFinite() && minCircularity > 0.0 && minCircularity <= 1.0)
        require(minFill.isFinite() && minFill > 0.0 && minFill <= 1.0)
        require(cornerFraction.isFinite() && cornerFraction > 0.0 && cornerFraction < 0.5)
        require(kind == ObjectKind.OBSTACLE || (minFill >= 0.90 && minCircularity >= 0.80)) {
            "Only obstacles may relax complete-circle checks"
        }
    }
}

/** Owned geometry in board millimetres; confidence is shape/color-fit quality, not calibrated probability. */
@ConsistentCopyVisibility
public data class Detection internal constructor(
    public val kind: ObjectKind,
    public val circle: Circle,
    public val confidence: Double,
)

/** A successful observation may be empty and never asserts that all objects were visible. */
public sealed interface ObjectDetection {
    public class Observed internal constructor(
        objects: List<Detection>,
    ) : ObjectDetection {
        public val objects: List<Detection> = Collections.unmodifiableList(objects.toList())
    }

    public data class Failed(
        public val reason: ObjectDetectionFailure,
    ) : ObjectDetection
}

public enum class ObjectDetectionFailure {
    INVALID_FRAME,
    INCOMPATIBLE_GEOMETRY,
    OPENCV_ERROR,
}

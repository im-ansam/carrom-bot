package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Vec2
import java.util.Collections
import kotlin.math.hypot

/** Caller-calibrated gates in mm, mm/s and nanoseconds. No defaults assert real-game accuracy. */
public data class TrackingConfig(
    public val associationDistanceMm: Double,
    public val smoothingAlpha: Double,
    public val stationaryRadiusMm: Double,
    public val stationarySpeedMmPerSecond: Double,
    public val stationaryDurationNanos: Long,
    public val maxGapNanos: Long,
) {
    init {
        require(associationDistanceMm.isFinite() && associationDistanceMm > 0.0)
        require(smoothingAlpha.isFinite() && smoothingAlpha > 0.0 && smoothingAlpha <= 1.0)
        require(stationaryRadiusMm.isFinite() && stationaryRadiusMm >= 0.0)
        require(stationarySpeedMmPerSecond.isFinite() && stationarySpeedMmPerSecond >= 0.0)
        require(stationaryDurationNanos > 0L && maxGapNanos > 0L)
    }
}

/** Current observed geometry only. Velocity comes from raw observations, not smoothed displacement. */
@ConsistentCopyVisibility
public data class TrackedObject internal constructor(
    public val id: String,
    public val kind: ObjectKind,
    public val circle: Circle,
    public val velocity: Vec2,
    public val confidence: Double,
    public val stationary: Boolean,
)

public enum class TrackingFailure {
    OUT_OF_ORDER,
    STALE_FRAME,
    DETECTION_FAILED,
    INVALID_OBSERVATION,
}

/** Immutable current observations; retained missing identities never appear in [objects]. */
public class TrackingFrame internal constructor(
    public val timestampNanos: Long,
    objects: List<TrackedObject>,
    public val hasMissingObjects: Boolean,
    public val failure: TrackingFailure? = null,
) {
    public val objects: List<TrackedObject> = Collections.unmodifiableList(objects.toList())
}

/**
 * Synchronous, single-owner tracker for one fixed board coordinate system and capture session.
 * Supply nonnegative monotonic timestamps and a same-clock current time on every call; no wall clock
 * is read. Stale/out-of-order input never mutates history. Failed/invalid observations break dwell.
 * Reset by constructing a new tracker after geometry/session changes. IDs are session-local.
 * Matching requires a unique same-kind neighbour in both directions within a fixed distance gate.
 * Ambiguity withholds observations rather than guessing. Missing IDs expire after [TrackingConfig.maxGapNanos].
 * No extrapolated positions or inferred removals are published. Caller must serialize access.
 */
public class ObjectTracker(
    private val bounds: BoardBounds,
    private val config: TrackingConfig,
) {
    private val tracks = mutableListOf<Track>()
    private var lastTimestamp: Long? = null
    private var nextId = 0L

    public fun update(
        observation: ObjectDetection,
        timestampNanos: Long,
        nowNanos: Long = timestampNanos,
    ): TrackingFrame {
        require(timestampNanos >= 0L && nowNanos >= timestampNanos)
        val temporalFailure =
            when {
                lastTimestamp?.let { timestampNanos <= it } == true -> TrackingFailure.OUT_OF_ORDER
                nowNanos - timestampNanos > config.maxGapNanos -> TrackingFailure.STALE_FRAME
                else -> null
            }
        if (temporalFailure != null) return failed(timestampNanos, temporalFailure)
        lastTimestamp = timestampNanos
        tracks.removeAll { timestampNanos - it.lastSeen > config.maxGapNanos }
        return when (observation) {
            is ObjectDetection.Failed -> {
                tracks.forEach { it.breakDwell() }
                failed(timestampNanos, TrackingFailure.DETECTION_FAILED)
            }

            is ObjectDetection.Observed -> {
                processObservation(observation.objects, timestampNanos)
            }
        }
    }

    private fun processObservation(
        detections: List<Detection>,
        timestampNanos: Long,
    ): TrackingFrame {
        if (!valid(detections)) {
            tracks.forEach { it.breakDwell() }
            return failed(timestampNanos, TrackingFailure.INVALID_OBSERVATION)
        }
        return associate(
            detections.sortedWith(compareBy({ it.kind.ordinal }, { it.circle.center.x }, { it.circle.center.y })),
            timestampNanos,
        )
    }

    private fun associate(
        detections: List<Detection>,
        time: Long,
    ): TrackingFrame {
        val candidates =
            detections.map { detection ->
                tracks.filter {
                    it.raw.kind == detection.kind &&
                        distance(it.raw.circle.center, detection.circle.center) <= config.associationDistanceMm
                }
            }
        val matched = mutableSetOf<Track>()
        val observed = mutableListOf<TrackedObject>()
        detections.forEachIndexed { index, detection ->
            val options = candidates[index]
            val track =
                when {
                    options.isEmpty() -> Track("vision-${nextId++}", detection, time).also { tracks.add(it) }
                    options.size == 1 && candidates.count { options.single() in it } == 1 -> options.single()
                    else -> null
                }
            if (track != null) {
                if (track.lastSeen != time && !track.observe(detection, time, config)) {
                    tracks.forEach { it.breakDwell() }
                    return failed(time, TrackingFailure.INVALID_OBSERVATION)
                }
                matched.add(track)
                observed.add(track.snapshot(config, time))
            }
        }
        tracks.filter { it !in matched }.forEach { it.breakDwell() }
        val missing = tracks.any { it !in matched } || observed.size != detections.size
        return TrackingFrame(time, observed.sortedBy { it.id }, missing)
    }

    private fun valid(objects: List<Detection>): Boolean =
        objects.all { detection ->
            detection.confidence.isFinite() && detection.confidence in 0.0..1.0 &&
                if (detection.kind == ObjectKind.POCKET) {
                    bounds.contains(detection.circle.center)
                } else {
                    bounds.contains(detection.circle)
                }
        } &&
            objects.indices.all { index ->
                objects.drop(index + 1).none {
                    distance(objects[index].circle.center, it.circle.center) <
                        objects[index].circle.radius + it.circle.radius
                }
            }

    private fun failed(
        time: Long,
        reason: TrackingFailure,
    ): TrackingFrame = TrackingFrame(time, emptyList(), tracks.isNotEmpty(), reason)
}

private class Track(
    val id: String,
    var raw: Detection,
    var lastSeen: Long,
) {
    private var smoothed = raw.circle
    private var velocity = Vec2(0.0, 0.0)
    private var anchor = raw.circle
    private var stationarySince = lastSeen
    private var observations = 1

    fun breakDwell() {
        observations = 0
    }

    fun observe(
        detection: Detection,
        time: Long,
        config: TrackingConfig,
    ): Boolean {
        val seconds = (time - lastSeen).toDouble() / NANOS_PER_SECOND
        val speedX = (detection.circle.center.x - raw.circle.center.x) / seconds
        val speedY = (detection.circle.center.y - raw.circle.center.y) / seconds
        if (!speedX.isFinite() || !speedY.isFinite()) return false
        velocity = Vec2(speedX, speedY)
        val continuous = observations > 0
        val geometryChanged = !circlesNear(anchor, detection.circle, config.stationaryRadiusMm)
        if (!continuous || geometryChanged ||
            distance(Vec2(0.0, 0.0), velocity) > config.stationarySpeedMmPerSecond
        ) {
            anchor = detection.circle
            stationarySince = time
            observations = 0
        }
        val alpha = if (continuous) config.smoothingAlpha else 1.0
        smoothed =
            Circle(
                Vec2(
                    blend(smoothed.center.x, detection.circle.center.x, alpha),
                    blend(smoothed.center.y, detection.circle.center.y, alpha),
                ),
                blend(smoothed.radius, detection.circle.radius, alpha),
            )
        observations++
        raw = detection
        lastSeen = time
        return true
    }

    fun snapshot(
        config: TrackingConfig,
        time: Long,
    ): TrackedObject =
        TrackedObject(
            id,
            raw.kind,
            smoothed,
            velocity,
            raw.confidence,
            observations >= MIN_STATIONARY_OBSERVATIONS && time - stationarySince >= config.stationaryDurationNanos &&
                circlesNear(smoothed, raw.circle, config.stationaryRadiusMm),
        )
}

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MIN_STATIONARY_OBSERVATIONS = 3

private fun circlesNear(
    first: Circle,
    second: Circle,
    tolerance: Double,
): Boolean =
    distance(first.center, second.center) <= tolerance &&
        kotlin.math.abs(first.radius - second.radius) <= tolerance

private fun distance(
    first: Vec2,
    second: Vec2,
): Double = hypot(first.x - second.x, first.y - second.y)

private fun blend(
    previous: Double,
    current: Double,
    alpha: Double,
): Double = previous + (current - previous) * alpha

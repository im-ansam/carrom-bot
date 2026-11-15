package io.github.carrombot.core.vision

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2

/** Only [Ready] carries a board suitable for a stationary planner input. Never cache across frames. */
public sealed interface BoardEstimate {
    /** Confidence is minimum current detector fit quality, not a probability of completeness/accuracy. */
    public class Ready internal constructor(
        public val board: BoardState,
        public val confidence: Double,
        public val timestampNanos: Long,
    ) : BoardEstimate

    public data class Unavailable(
        public val reason: BoardUnavailableReason,
    ) : BoardEstimate
}

public enum class BoardUnavailableReason {
    INVALID_FRAME,
    INCOMPLETE_INVENTORY,
    LOW_CONFIDENCE,
    NOT_STATIONARY,
}

/**
 * Single-owner adapter from tracking to the shared model. [expectedInventory] must be independently
 * confirmed by the caller for the current game; repeated detections alone cannot establish completeness.
 * Counts are exact, absent kinds mean zero, and exactly one striker is required. Construct a new
 * estimator after confirmed removals, geometry changes or a new session. No game-rule removal is inferred.
 * Obstacles cannot be represented by BoardState, so a scene containing them is never ready.
 * [minConfidence] is caller calibrated. All expected objects must be currently observed, confidence
 * qualified and stationary for the configured dwell. No missing object's geometry is fabricated.
 * Ready velocities are zero under this calibrated stationary tolerance, not exact measured rest.
 * This exposes no cached ready state; downstream consumers must enforce timestamp freshness at use.
 */
public class BoardStateEstimator(
    private val bounds: BoardBounds,
    config: TrackingConfig,
    expectedInventory: Map<ObjectKind, Int>,
    private val minConfidence: Double,
) {
    private val inventory = expectedInventory.filterValues { it != 0 }.toMap()
    private val tracker = ObjectTracker(bounds, config)

    init {
        require(expectedInventory.values.all { it >= 0 })
        require(inventory[ObjectKind.STRIKER] == 1)
        require(ObjectKind.OBSTACLE !in inventory) { "BoardState cannot represent obstacles" }
        require(minConfidence.isFinite() && minConfidence in 0.0..1.0)
    }

    public fun update(
        observation: ObjectDetection,
        timestampNanos: Long,
        nowNanos: Long = timestampNanos,
    ): BoardEstimate {
        val lowConfidence =
            observation is ObjectDetection.Observed &&
                observation.objects.any { it.confidence < minConfidence }
        val qualified =
            if (observation is ObjectDetection.Observed) {
                val objects = observation.objects.filter { !it.confidence.isFinite() || it.confidence >= minConfidence }
                ObjectDetection.Observed(objects)
            } else {
                observation
            }
        val frame = tracker.update(qualified, timestampNanos, nowNanos)
        val objects = frame.objects
        val reason =
            when {
                frame.failure != null -> BoardUnavailableReason.INVALID_FRAME

                lowConfidence -> BoardUnavailableReason.LOW_CONFIDENCE

                frame.hasMissingObjects || objects
                    .groupingBy {
                        it.kind
                    }.eachCount() != inventory -> BoardUnavailableReason.INCOMPLETE_INVENTORY

                objects.any { !it.stationary } -> BoardUnavailableReason.NOT_STATIONARY

                else -> null
            }
        if (reason != null) return BoardEstimate.Unavailable(reason)
        return BoardEstimate.Ready(buildBoard(objects), objects.minOf { it.confidence }, timestampNanos)
    }

    private fun buildBoard(objects: List<TrackedObject>): BoardState {
        val striker = objects.single { it.kind == ObjectKind.STRIKER }
        return BoardState(
            bounds,
            objects.filter { it.kind == ObjectKind.POCKET }.map { Pocket(it.id, it.circle) },
            objects.mapNotNull { objectValue ->
                val kind =
                    when (objectValue.kind) {
                        ObjectKind.BLACK_DISC -> DiscKind.BLACK
                        ObjectKind.WHITE_DISC -> DiscKind.WHITE
                        ObjectKind.QUEEN -> DiscKind.QUEEN
                        else -> null
                    }
                kind?.let { Disc(objectValue.id, it, objectValue.circle, Vec2(0.0, 0.0)) }
            },
            Striker(striker.id, striker.circle, Vec2(0.0, 0.0)),
        )
    }
}

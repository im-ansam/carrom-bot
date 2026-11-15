package io.github.carrombot.core.overlay

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.CoordinateTransform
import io.github.carrombot.core.model.Vec2
import java.util.Collections

/** Explicit rectified board-mm to local Canvas-pixel mapping; never an implicit display offset. */
public data class BoardMapping(
    public val bounds: BoardBounds,
    public val transform: CoordinateTransform,
) {
    init {
        val minimum = transform.toScreen(bounds.min)
        val maximum = transform.toScreen(bounds.max)
        val components = listOf(minimum.x, minimum.y, maximum.x, maximum.y).map(Double::toFloat)
        require(components.all(Float::isFinite)) { "Mapped board exceeds Canvas precision" }
        require(maximum.x.toFloat() > minimum.x.toFloat() && maximum.y.toFloat() > minimum.y.toFloat()) {
            "Mapped board collapses at Canvas precision"
        }
    }

    public fun toScreen(point: Vec2): Vec2 = transform.toScreen(point)

    public fun toBoard(point: Vec2): Vec2 = transform.toBoard(point)

    /** Normalized [0,1] coordinates refer to the full board rectangle, including translated bounds. */
    public fun normalizedToScreen(point: Vec2): Vec2 {
        require(point.x in 0.0..1.0 && point.y in 0.0..1.0) { "Normalized point must be within the board" }
        return toScreen(Vec2(bounds.min.x + point.x * bounds.width, bounds.min.y + point.y * bounds.height))
    }
}

/**
 * Ordered centre-line vertices in board mm. Producers must include every rebound/contact vertex;
 * coarse fixed-time samples alone cannot reconstruct corners. Owns its list. No interpolation or physics is inferred.
 */
public class BodyTrajectory(
    public val bodyId: String,
    public val isStriker: Boolean,
    points: List<Vec2>,
) {
    public val points: List<Vec2> = owned(points)

    init {
        require(bodyId.isNotBlank() && this.points.size >= 2) { "Trajectory needs an identity and two vertices" }
    }
}

/** Marker shape carries meaning independently of candidate colour or line style. */
public enum class AnnotationKind { CONTACT, REBOUND, POCKET }

/** Producer-supplied physical contact/rebound/destination in board mm, never inferred from disc removal. */
public data class ShotAnnotation(
    public val kind: AnnotationKind,
    public val position: Vec2,
)

/**
 * Render-only proposal. Power is normalized (0,1]; nullable confidence is a supplied sensitivity
 * indicator [0,1], not a win probability. Null is displayed as unknown, never fabricated.
 * Lists are owned; IDs are short caller-provided display labels as well as stable identities.
 */
public class RenderedCandidate(
    public val id: String,
    trajectories: List<BodyTrajectory>,
    annotations: List<ShotAnnotation>,
    public val power: Double,
    public val confidence: Double?,
) {
    public val trajectories: List<BodyTrajectory> = owned(trajectories)
    public val annotations: List<ShotAnnotation> = owned(annotations)

    init {
        require(id.isNotBlank()) { "Candidate ID must not be blank" }
        require(power.isFinite() && power > 0.0 && power <= 1.0) { "Power must be in (0,1]" }
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0)) { "Invalid confidence" }
        require(this.trajectories.isNotEmpty()) { "Candidate needs trajectories" }
        require(
            this.trajectories
                .map { it.bodyId }
                .distinct()
                .size == this.trajectories.size,
        ) { "Duplicate body IDs" }
    }
}

/**
 * Immutable advisory scene. Empty candidates explicitly clear assistance. All geometry uses shared
 * board millimetres; selected candidate is drawn last, alternatives dashed. No capture or planner resources.
 * Invalid geometry/identity throws IllegalArgumentException before drawing. No clipping hides invalid input.
 */
public class OverlayScene(
    public val bounds: BoardBounds,
    candidates: List<RenderedCandidate>,
    public val selectedId: String?,
) {
    public val candidates: List<RenderedCandidate> = owned(candidates)

    init {
        require(
            this.candidates
                .map { it.id }
                .distinct()
                .size == this.candidates.size,
        ) { "Duplicate candidate IDs" }
        require(
            if (this.candidates.isEmpty()) selectedId == null else this.candidates.any { it.id == selectedId },
        ) { "Selection must identify a candidate" }
        require(
            this.candidates.all { candidate ->
                candidate.trajectories.all { path -> path.points.all(bounds::contains) } &&
                    candidate.annotations.all { bounds.contains(it.position) }
            },
        ) { "Scene geometry must lie within board bounds" }
    }
}

private fun <T> owned(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

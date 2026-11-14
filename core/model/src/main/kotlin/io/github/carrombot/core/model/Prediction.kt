package io.github.carrombot.core.model

/**
 * Proposed shot from an already positioned striker in [boardState]. [initialVelocity]
 * is the requested post-impulse velocity in mm/s, finite and nonzero. Device power,
 * aiming gestures, turn legality and impulse calibration belong to later adapters.
 */
public data class ShotInput(
    public val boardState: BoardState,
    public val initialVelocity: Vec2,
) {
    init {
        require(initialVelocity.x != 0.0 || initialVelocity.y != 0.0) { "Shot velocity must be nonzero" }
    }
}

/** Predicted snapshot at finite nonnegative elapsed seconds relative to the shot, never wall-clock time. */
public data class PredictionFrame(
    public val elapsedSeconds: Double,
    public val boardState: BoardState,
) {
    init {
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0) { "Elapsed seconds must be finite and nonnegative" }
    }
}

/** Producer-declared stopping reason; TIME_LIMIT explicitly indicates a truncated prediction. */
public enum class PredictionTermination {
    REST,
    TIME_LIMIT,
}

/**
 * Successful prediction data, not a simulator. Owns an immutable ordered frame list.
 * The first frame is the pre-impulse input snapshot at zero seconds; later times strictly
 * increase. Board/pocket geometry and surviving body identity, kind and radius stay fixed.
 * Discs may disappear but never reappear; removal does not itself assert pocketing.
 * The striker remains present. Producers report failures separately, never as empty success.
 * Invalid contracts throw [IllegalArgumentException]; no physical accuracy is guaranteed here.
 */
public class ShotPrediction(
    public val shot: ShotInput,
    frames: List<PredictionFrame>,
    public val termination: PredictionTermination,
) {
    public val frames: List<PredictionFrame> = immutableCopy(frames)

    init {
        require(this.frames.isNotEmpty()) { "Prediction needs an initial frame" }
        require(this.frames.first() == PredictionFrame(0.0, shot.boardState)) {
            "First frame must be the input snapshot at zero seconds"
        }
        this.frames.zipWithNext().forEach { (previous, next) ->
            require(next.elapsedSeconds > previous.elapsedSeconds) { "Frame times must strictly increase" }
            validateContinuation(previous.boardState, next.boardState)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ShotPrediction && shot == other.shot && frames == other.frames && termination == other.termination

    override fun hashCode(): Int = listOf(shot, frames, termination).hashCode()

    override fun toString(): String = "ShotPrediction(shot=$shot, frames=$frames, termination=$termination)"
}

private fun validateContinuation(
    previous: BoardState,
    next: BoardState,
) {
    require(next.bounds == previous.bounds && next.pockets == previous.pockets) { "Board geometry must stay fixed" }
    require(next.striker.id == previous.striker.id && next.striker.circle.radius == previous.striker.circle.radius) {
        "Striker identity and radius must stay fixed"
    }
    val previousDiscs = previous.discs.associateBy { it.id }
    require(
        next.discs.all { disc ->
            val before = previousDiscs[disc.id]
            before != null && before.kind == disc.kind && before.circle.radius == disc.circle.radius
        },
    ) { "Discs cannot appear or change identity, kind or radius" }
}

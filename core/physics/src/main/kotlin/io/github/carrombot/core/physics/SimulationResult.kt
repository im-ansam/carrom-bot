package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.ShotPrediction
import io.github.carrombot.core.model.Vec2
import java.util.Collections

/** Physical event type. Capture precedes body contacts, which precede walls at equal times. */
public enum class ShotEventType { CAPTURE, BODY_CONTACT, WALL_CONTACT }

/** Event clock in seconds; target is a pocket ID, body ID or wall name respectively. */
public data class ShotEvent(
    public val elapsedSeconds: Double,
    public val type: ShotEventType,
    public val bodyId: String,
    public val target: String,
) {
    init {
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0) { "Invalid event time" }
        require(bodyId.isNotBlank() && target.isNotBlank()) { "Event IDs must not be blank" }
    }
}

/** Event-resolved centres in board mm. Body contact supplies both centres; other events only one. */
public data class ResolvedShotEvent(
    public val event: ShotEvent,
    public val bodyCenter: Vec2,
    public val targetCenter: Vec2? = null,
)

/** Physical scoring outcome; null kind denotes a striker foul. No turn/queen-cover rules are inferred. */
public data class PocketCapture(
    public val elapsedSeconds: Double,
    public val bodyId: String,
    public val pocketId: String,
    public val kind: DiscKind?,
) {
    init {
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0) { "Invalid capture time" }
        require(bodyId.isNotBlank() && pocketId.isNotBlank()) { "Capture IDs must not be blank" }
    }
}

/** Owned immutable timeline and samples. Captured striker remains frozen in the board but is inactive. */
public class SimulationResult internal constructor(
    public val prediction: ShotPrediction,
    events: List<ShotEvent>,
    captures: List<PocketCapture>,
    resolvedEvents: List<ResolvedShotEvent> = emptyList(),
) {
    public val events: List<ShotEvent> = Collections.unmodifiableList(ArrayList(events))
    public val captures: List<PocketCapture> = Collections.unmodifiableList(ArrayList(captures))
    public val resolvedEvents: List<ResolvedShotEvent> = Collections.unmodifiableList(ArrayList(resolvedEvents))
    public val finalState: BoardState get() = prediction.frames.last().boardState
    public val strikerCaptured: Boolean get() = captures.any { it.kind == null }
}

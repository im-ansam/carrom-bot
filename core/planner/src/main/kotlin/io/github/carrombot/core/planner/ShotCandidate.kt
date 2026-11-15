package io.github.carrombot.core.planner

import io.github.carrombot.core.model.ShotInput
import java.util.Collections

/** Stable case-sensitive family key; custom keys allow new families without changing this schema. */
public data class ShotType(
    public val key: String,
) {
    init {
        require(key.isNotBlank()) { "Shot type key must not be blank" }
    }

    public companion object {
        public val DIRECT: ShotType = ShotType("direct")
        public val CUT: ShotType = ShotType("cut")
        public val STRIKER_BANK: ShotType = ShotType("striker-bank")
        public val DISC_BANK: ShotType = ShotType("disc-bank")
        public val DOUBLE_BANK: ShotType = ShotType("double-bank")
        public val COMBINATION: ShotType = ShotType("combination")
        public val MULTI_POCKET: ShotType = ShotType("multi-pocket")
    }
}

/** Desired disc capture at a specific pocket; identity is checked against the candidate snapshot. */
public data class PocketTarget(
    public val discId: String,
    public val pocketId: String,
) {
    init {
        require(discId.isNotBlank() && pocketId.isNotBlank()) { "Target IDs must not be blank" }
    }
}

/**
 * Inclusive counts of resolved impulses over the entire shot, not sampled path intersections.
 * Wall contacts count each wall separately at corners. Body contacts count each simulator event.
 * These coarse constraints do not assert an ordered route, obstacle clearance or shot-family geometry.
 */
public data class TrajectoryConstraints(
    public val maxWallContacts: Int = Int.MAX_VALUE,
    public val maxBodyContacts: Int = Int.MAX_VALUE,
    public val minWallContacts: Int = 0,
    public val minBodyContacts: Int = 0,
) {
    init {
        require(minWallContacts >= 0 && maxWallContacts >= minWallContacts) { "Invalid wall contact bounds" }
        require(minBodyContacts >= 0 && maxBodyContacts >= minBodyContacts) { "Invalid body contact bounds" }
    }
}

/**
 * Immutable proposal with stable caller ID, pre-positioned shared shot input and normalized power in (0,1].
 * Targets are ordered, nonempty and unique by disc; all IDs must exist in the snapshot.
 * The family describes generator intent, not proof of geometry. Power/velocity calibration is caller-owned;
 * no device calibration, legal placement or automatic execution is implied. Invalid inputs throw.
 */
public class CandidateShot(
    public val id: String,
    public val type: ShotType,
    public val shot: ShotInput,
    public val power: Double,
    targets: List<PocketTarget>,
    public val constraints: TrajectoryConstraints = TrajectoryConstraints(),
) {
    public val targets: List<PocketTarget> = ownedCopy(targets)

    init {
        require(id.isNotBlank()) { "Candidate ID must not be blank" }
        require(power.isFinite() && power > 0.0 && power <= 1.0) { "Launch power must be in (0,1]" }
        require(this.targets.isNotEmpty()) { "At least one target is required" }
        require(
            this.targets
                .map { it.discId }
                .distinct()
                .size == this.targets.size,
        ) { "Duplicate target disc" }
        require(
            this.targets.all { target ->
                shot.boardState.discs.any { it.id == target.discId } &&
                    shot.boardState.pockets.any { it.id == target.pocketId }
            },
        ) { "Targets must reference snapshot discs and pockets" }
    }

    override fun equals(other: Any?): Boolean =
        other is CandidateShot && id == other.id && type == other.type && shot == other.shot &&
            power == other.power && targets == other.targets && constraints == other.constraints

    override fun hashCode(): Int = listOf(id, type, shot, power, targets, constraints).hashCode()
}

internal fun <T> ownedCopy(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

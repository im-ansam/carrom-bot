package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PowerModel

/** One caller-identified proposal. Aim is a disc-centre point inside the full-fit pocket aperture. */
public data class ContactShotRequest(
    public val id: String,
    public val board: BoardState,
    public val target: PocketTarget,
    public val strikerPosition: Vec2,
    public val pocketAim: Vec2,
    public val power: Double,
) {
    init {
        require(id.isNotBlank()) { "Candidate ID must not be blank" }
        require(power.isFinite() && power > 0.0 && power <= 1.0) { "Power must be in (0,1]" }
        require(board.discs.any { it.id == target.discId }) { "Unknown target disc" }
        require(board.pockets.any { it.id == target.pocketId }) { "Unknown target pocket" }
    }
}

/**
 * Caller-calibrated launch mapping and geometric distances in mm. Clearance is a conservative
 * obstacle/aperture margin; alignmentTolerance partitions direct/cut by lateral approach offset.
 * No game-specific dimensions or power optimization are supplied.
 */
public data class ContactShotConfig(
    public val powerModel: PowerModel,
    public val clearance: Double,
    public val alignmentTolerance: Double,
) {
    init {
        require(clearance.isFinite() && clearance >= 0.0) { "Invalid clearance" }
        require(alignmentTolerance.isFinite() && alignmentTolerance >= 0.0) { "Invalid alignment tolerance" }
    }
}

/** Board mm: striker centre at impact, shared surface point, aim and first margin-aperture entry. */
public data class ContactGeometry(
    public val ghostCenter: Vec2,
    public val contactPoint: Vec2,
    public val pocketAim: Vec2,
    public val pocketEntry: Vec2,
)

/** Geometrically feasible intent; physics evaluation is still required to establish capture/foul/rest. */
public data class ContactShot(
    public val candidate: CandidateShot,
    public val geometry: ContactGeometry,
)

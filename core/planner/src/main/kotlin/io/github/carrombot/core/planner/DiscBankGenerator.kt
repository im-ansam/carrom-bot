package io.github.carrombot.core.planner

import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig

/**
 * One/two disc-rail proposals, with exact ordered impulses checked by the unified simulator.
 * Aim must fit strictly inside radius-inset bounds and inside the margin aperture. Corners,
 * rail starts, extra impulses and premature captures are conservatively rejected (null).
 * Caller supplies ID, legal placement, power, calibration and budgets; failures propagate.
 */
public class DiscBankGenerator {
    /** Stable enum order: single rails, then distinct ordered pairs; geometry prunes corners later. */
    public fun enumerateWallSequences(maxReflections: Int): List<List<BankWall>> {
        require(maxReflections in 1..2) { "One or two reflections required" }
        val singles = BankWall.entries.map { ownedCopy(listOf(it)) }
        val doubles =
            if (maxReflections == 2) {
                BankWall.entries.flatMap { first ->
                    BankWall.entries.filter { it != first }.map { ownedCopy(listOf(first, it)) }
                }
            } else {
                emptyList()
            }
        return ownedCopy(singles + doubles)
    }

    @Suppress("ReturnCount") // Ordered feasibility guards precede model construction.
    public fun generate(
        request: ContactShotRequest,
        legalStrikerPositions: List<Vec2>,
        walls: List<BankWall>,
        config: ContactShotConfig,
        physics: PhysicsConfig,
    ): ContactSequenceShot? {
        require(walls.size in 1..2) { "One or two disc walls required" }
        val geometry = SequenceGeometry(request, config)
        if (!geometry.validate(legalStrikerPositions) || !geometry.aperture()) return null
        val board = request.board
        val disc = board.discs.single { it.id == request.target.discId }
        val path =
            ReflectionPathGenerator().generate(
                board.bounds,
                disc.circle.radius,
                disc.circle.center,
                request.pocketAim,
                walls,
            ) ?: return null
        val ghost =
            geometry.ghost(disc.circle.center, path.points[1], disc.circle.radius + board.striker.circle.radius)
                ?: return null
        if (!board.bounds.contains(Circle(ghost, board.striker.circle.radius))) return null
        val strikerClear =
            geometry.clear(
                start = request.strikerPosition,
                end = ghost,
                radius = board.striker.circle.radius,
                excluded = setOf(disc.id),
                final = false,
            )
        if (!strikerClear) return null
        val clear =
            path.points.zipWithNext().withIndex().all { (index, leg) ->
                geometry.clear(leg.first, leg.second, disc.circle.radius, setOf(disc.id), index == path.points.size - 2)
            }
        if (!clear) return null
        val candidate =
            geometry.launch(ghost, delta(disc.circle.center, ghost), ShotType.DISC_BANK, 1, walls.size)
                ?: return null
        val wallImpulses = walls.map { ExpectedImpulse(disc.id, it.name, true) }
        val expected = listOf(ExpectedImpulse(board.striker.id, disc.id)) + wallImpulses
        return verifiedSequence(candidate, listOf(disc.id), expected, physics)
    }
}

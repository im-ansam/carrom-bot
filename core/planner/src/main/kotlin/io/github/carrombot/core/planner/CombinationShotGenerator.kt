package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig

/**
 * Bounded caller-selected striker -> disc -> ... -> target sequences (two to four discs).
 * Backward ghost construction proposes transfer normals; it does not simulate independent physics.
 * Cuts/caroms may retain residual motion, but only REST, no foul, exact requested impulse order
 * and requested final capture are accepted. Extra contacts reject conservatively. Lists are borrowed
 * synchronously, results own copies, malformed sequences/scenes and simulator failures throw.
 */
public class CombinationShotGenerator {
    @Suppress("ReturnCount") // Guard each corridor before constructing launch state.
    public fun generate(
        request: ContactShotRequest,
        legalStrikerPositions: List<Vec2>,
        discIds: List<String>,
        config: ContactShotConfig,
        physics: PhysicsConfig,
    ): ContactSequenceShot? {
        require(discIds.size in 2..MAX_CHAIN_DISCS && discIds.distinct().size == discIds.size) {
            "Invalid chain size or duplicates"
        }
        require(discIds.last() == request.target.discId) { "Chain must end at capture target" }
        val board = request.board
        require(discIds.all { id -> board.discs.any { it.id == id } }) { "Unknown chain disc" }
        val discs = discIds.map { id -> board.discs.single { it.id == id } }
        val geometry = SequenceGeometry(request, config)
        if (!geometry.validate(legalStrikerPositions) || !geometry.aperture()) return null
        var destination = request.pocketAim
        var firstNormal = Vec2(0.0, 0.0)
        for (index in discs.indices.reversed()) {
            val disc = discs[index]
            val final = index == discs.lastIndex
            val excluded = if (final) setOf(disc.id) else setOf(disc.id, discs[index + 1].id)
            if (!geometry.clear(disc.circle.center, destination, disc.circle.radius, excluded, final)) return null
            val incomingRadius = if (index == 0) board.striker.circle.radius else discs[index - 1].circle.radius
            val ghost =
                geometry.ghost(
                    center = disc.circle.center,
                    outgoing = destination,
                    radii = disc.circle.radius + incomingRadius,
                ) ?: return null
            if (!board.bounds.contains(Circle(ghost, incomingRadius))) return null
            firstNormal = delta(disc.circle.center, ghost)
            destination = ghost
        }
        val strikerClear =
            geometry.clear(
                start = request.strikerPosition,
                end = destination,
                radius = board.striker.circle.radius,
                excluded = setOf(discs.first().id),
                final = false,
            )
        if (!strikerClear) return null
        val candidate = geometry.launch(destination, firstNormal, ShotType.COMBINATION, discs.size, 0) ?: return null
        val identities = listOf(board.striker.id) + discIds
        val expected = identities.zipWithNext().map { ExpectedImpulse(it.first, it.second) }
        return verifiedSequence(candidate, discIds, expected, physics)
    }

    /**
     * Enumerates a deterministic prefix: shortest chains first, then board disc storage order.
     * At most maxSequences (1..256) routes of two..maxDiscs (2..4) distinct discs are returned.
     * Target is always last. This is intent enumeration, not feasibility or completeness proof;
     * callers allocate a unique request ID for each selected route before calling generate.
     */
    public fun enumerateSequences(
        board: BoardState,
        targetDiscId: String,
        maxDiscs: Int,
        maxSequences: Int,
    ): List<List<String>> {
        require(maxDiscs in 2..MAX_CHAIN_DISCS) { "Invalid chain bound" }
        require(maxSequences in 1..MAX_SEQUENCES) { "Invalid sequence budget" }
        require(board.discs.any { it.id == targetDiscId }) { "Unknown target disc" }
        val choices = board.discs.map { it.id }.filter { it != targetDiscId }
        val result = mutableListOf<List<String>>()
        val enumeration = SequenceEnumeration(choices, targetDiscId, maxSequences)
        for (count in 1 until maxDiscs) {
            enumeration.append(emptyList(), count, result)
            if (result.size == maxSequences) break
        }
        return ownedCopy(result)
    }

    private companion object {
        const val MAX_CHAIN_DISCS: Int = 4
        const val MAX_SEQUENCES: Int = 256
    }
}

private class SequenceEnumeration(
    private val choices: List<String>,
    private val target: String,
    private val limit: Int,
) {
    fun append(
        prefix: List<String>,
        count: Int,
        result: MutableList<List<String>>,
    ) {
        if (result.size == limit) return
        if (prefix.size == count) {
            result.add(ownedCopy(prefix + target))
        } else {
            for (id in choices) {
                if (result.size == limit) break
                if (id !in prefix) append(prefix + id, count, result)
            }
        }
    }
}

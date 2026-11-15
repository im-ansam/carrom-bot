package io.github.carrombot.core.planner

import io.github.carrombot.core.model.Vec2

/** Stateless direct contact generator; see [generate] for legality and failure semantics. */
public class DirectShotGenerator {
    /**
     * Generates one proposal from caller-owned discrete legal striker centres (exact membership).
     * Null means illegal/blocked/unreachable geometry or a different family. Invalid request,
     * moving/overlapping scene or nonrepresentable arithmetic throws IllegalArgumentException.
     * Inputs are borrowed synchronously, never retained or mutated. IDs remain caller-owned.
     */
    public fun generate(
        request: ContactShotRequest,
        legalStrikerPositions: List<Vec2>,
        config: ContactShotConfig,
    ): ContactShot? = ContactShotGeometry(request, config).generate(legalStrikerPositions, ShotType.DIRECT)
}

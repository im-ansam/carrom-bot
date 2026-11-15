package io.github.carrombot.core.planner

import io.github.carrombot.core.model.Vec2

/** Oblique contact generator with the same caller legality/failure contract as [DirectShotGenerator]. */
public class CutShotGenerator {
    /** Returns a positive normal-transfer cut proposal; excludes direct and grazing/back-facing approaches. */
    public fun generate(
        request: ContactShotRequest,
        legalStrikerPositions: List<Vec2>,
        config: ContactShotConfig,
    ): ContactShot? = ContactShotGeometry(request, config).generate(legalStrikerPositions, ShotType.CUT)
}

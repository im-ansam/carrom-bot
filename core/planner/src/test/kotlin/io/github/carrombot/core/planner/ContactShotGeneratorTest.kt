package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.PowerModel
import io.github.carrombot.core.physics.ShotSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactShotGeneratorTest {
    private val start = Vec2(30.0, 50.0)
    private val board =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))),
            listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(60.0, 50.0), 5.0))),
            Striker("s", Circle(start, 5.0)),
        )
    private val config = ContactShotConfig(PowerModel(200.0), 0.01, 1e-8)

    @Test
    fun `straight shot exposes ghost and surface contact with calibrated velocity`() {
        val result = requireNotNull(DirectShotGenerator().generate(request(), listOf(start), config))
        assertEquals(Vec2(50.0, 50.0), result.geometry.ghostCenter)
        assertEquals(Vec2(55.0, 50.0), result.geometry.contactPoint)
        assertEquals(Vec2(100.0, 0.0), result.candidate.shot.initialVelocity)
        assertEquals(ShotType.DIRECT, result.candidate.type)
        assertNull(CutShotGenerator().generate(request(), listOf(start), config))
    }

    @Test
    fun `angled approach is a cut and back facing approach is unreachable`() {
        val angled = Vec2(30.0, 30.0)
        val cut = requireNotNull(CutShotGenerator().generate(request(placement = angled), listOf(angled), config))
        assertEquals(ShotType.CUT, cut.candidate.type)
        assertEquals(Vec2(50.0, 50.0), cut.geometry.ghostCenter)
        val shot = cut.candidate.shot
        val simulation =
            ShotSimulator().simulate(
                shot.boardState,
                shot,
                PhysicsConfig(0.01, 1000, deceleration = 10.0),
            )
        assertTrue(simulation.captures.any { it.bodyId == "d" && it.pocketId == "p" })
        assertNull(DirectShotGenerator().generate(request(placement = angled), listOf(angled), config))
        val behind = Vec2(80.0, 30.0)
        assertNull(CutShotGenerator().generate(request(placement = behind), listOf(behind), config))
    }

    @Test
    fun `blocked and tangent corridors and illegal placements are rejected`() {
        for (center in listOf(Vec2(40.0, 50.0), Vec2(40.0, 60.0), Vec2(75.0, 50.0))) {
            val crowded =
                BoardState(
                    board.bounds,
                    board.pockets,
                    board.discs + Disc("block", DiscKind.WHITE, Circle(center, 5.0)),
                    board.striker,
                )
            assertNull(DirectShotGenerator().generate(request(crowded), listOf(start), config))
        }
        assertNull(DirectShotGenerator().generate(request(), emptyList(), config))
    }

    @Test
    fun `generated straight proposal captures target through existing simulator evaluation`() {
        val generated = requireNotNull(DirectShotGenerator().generate(request(), listOf(start), config))
        val candidate = generated.candidate
        val simulation =
            ShotSimulator().simulate(
                candidate.shot.boardState,
                candidate.shot,
                PhysicsConfig(0.01, 1000, deceleration = 1.0),
            )
        val evaluated =
            ShotEvaluation.fromSimulation(
                candidate,
                simulation,
                PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(1.0, 0.0, 0.0)),
            )
        assertTrue(evaluated.rejections.isEmpty())
        assertEquals(board, candidate.shot.boardState)
        assertEquals(candidate, DirectShotGenerator().generate(request(), listOf(start), config)?.candidate)
    }

    @Test
    fun `pocket aperture margin and wall before capture reject unreachable shots`() {
        val outside = request().copy(pocketAim = Vec2(100.0, 65.0))
        assertNull(CutShotGenerator().generate(outside, listOf(start), config))
        val narrow =
            BoardState(
                board.bounds,
                listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 7.0))),
                board.discs,
                board.striker,
            )
        assertNull(DirectShotGenerator().generate(request(narrow), listOf(start), config))
        val zero =
            request().copy(
                pocketAim =
                    board.discs
                        .single()
                        .circle.center,
            )
        assertNull(DirectShotGenerator().generate(zero, listOf(start), config))
    }

    @Test
    fun `grazing target and out of bounds legal centre are rejected`() {
        for (placement in listOf(Vec2(50.0, 20.0), Vec2(1.0, 50.0), Vec2(60.0, 50.0))) {
            assertNull(CutShotGenerator().generate(request(placement = placement), listOf(placement), config))
        }
    }

    @Test
    fun `clear corridor just beyond explicit margin is retained and repositioning preserves input`() {
        val blocker = Disc("block", DiscKind.WHITE, Circle(Vec2(40.0, 60.1), 5.0))
        val source = BoardState(board.bounds, board.pockets, board.discs + blocker, board.striker)
        assertNotNull(DirectShotGenerator().generate(request(source), listOf(start), config))
        val placement = Vec2(20.0, 50.0)
        val generated = DirectShotGenerator().generate(request(placement = placement), listOf(placement), config)
        val result = requireNotNull(generated)
        assertEquals(placement, result.candidate.shot.boardState.striker.circle.center)
        assertEquals(start, board.striker.circle.center)
    }

    @Test
    fun `moving scenes and invalid request or configuration fail explicitly`() {
        val moving =
            BoardState(
                board.bounds,
                board.pockets,
                listOf(board.discs.single().copy(velocity = Vec2(1.0, 0.0))),
                board.striker,
            )
        assertThrows(IllegalArgumentException::class.java) {
            DirectShotGenerator().generate(request(moving), listOf(start), config)
        }
        assertThrows(IllegalArgumentException::class.java) { request().copy(power = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { request().copy(target = PocketTarget("x", "p")) }
        assertThrows(IllegalArgumentException::class.java) { config.copy(clearance = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { config.copy(alignmentTolerance = -1.0) }
        val overlap =
            BoardState(
                board.bounds,
                board.pockets,
                board.discs + board.discs.single().copy(id = "overlap"),
                board.striker,
            )
        assertThrows(IllegalArgumentException::class.java) {
            DirectShotGenerator().generate(request(overlap), listOf(start), config)
        }
    }

    @Test
    fun `earlier striker capture and wrong pocket intercept are rejected`() {
        val foul =
            BoardState(
                board.bounds,
                board.pockets + Pocket("foul", Circle(Vec2(40.0, 50.0), 6.0)),
                board.discs,
                board.striker,
            )
        assertNull(DirectShotGenerator().generate(request(foul), listOf(start), config))
        val wrong =
            BoardState(
                board.bounds,
                board.pockets + Pocket("wrong", Circle(Vec2(75.0, 50.0), 6.0)),
                board.discs,
                board.striker,
            )
        assertNull(DirectShotGenerator().generate(request(wrong), listOf(start), config))
    }

    private fun request(
        source: BoardState = board,
        placement: Vec2 = start,
    ): ContactShotRequest =
        ContactShotRequest(
            id = "candidate",
            board = source,
            target = PocketTarget("d", "p"),
            strikerPosition = placement,
            pocketAim = Vec2(100.0, 50.0),
            power = 0.5,
        )
}

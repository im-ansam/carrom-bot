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
import io.github.carrombot.core.physics.ShotEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class StrikerBankGeneratorTest {
    private val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0))
    private val config = ContactShotConfig(PowerModel(200.0), 0.001, 1e-7)
    private val physics = PhysicsConfig(2.0, 10)
    private val start = Vec2(30.0, 50.0)
    private val board =
        BoardState(
            bounds,
            listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))),
            listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(60.0, 50.0), 5.0))),
            Striker("s", Circle(start, 5.0)),
        )

    @Test
    fun `all physical rails generate and requested placement replaces original striker`() {
        val transforms: List<(Vec2) -> Vec2> =
            listOf(
                { it },
                { Vec2(100.0 - it.x, it.y) },
                { Vec2(it.y, it.x) },
                { Vec2(it.y, 100.0 - it.x) },
            )
        val walls = listOf(BankWall.LEFT, BankWall.RIGHT, BankWall.TOP, BankWall.BOTTOM)
        transforms.zip(walls).forEach { (transform, wall) ->
            val placement = transform(start)
            val scene =
                BoardState(
                    bounds,
                    listOf(Pocket("p", Circle(transform(Vec2(100.0, 50.0)), 20.0))),
                    listOf(Disc("d", DiscKind.BLACK, Circle(transform(Vec2(60.0, 50.0)), 5.0))),
                    Striker("s", Circle(Vec2(20.0, 20.0), 5.0)),
                )
            val result = requireNotNull(generate(request(scene, placement), listOf(wall)))
            assertEquals(
                wall.name,
                result.simulation.events
                    .first()
                    .target,
            )
            assertEquals(placement, result.candidate.shot.boardState.striker.circle.center)
            assertEquals(Vec2(20.0, 20.0), scene.striker.circle.center)
        }
    }

    @Test
    fun `single bank matches analytic golden events and retains input`() {
        val result = requireNotNull(generate(request(), listOf(BankWall.LEFT)))
        assertEquals(ShotType.STRIKER_BANK, result.candidate.type)
        assertEquals(listOf(start, Vec2(5.0, 50.0), Vec2(50.0, 50.0)), result.path.points)
        assertEquals(listOf("LEFT", "s", "p"), result.simulation.events.map { it.target })
        assertEquals(
            listOf(ShotEventType.WALL_CONTACT, ShotEventType.BODY_CONTACT, ShotEventType.CAPTURE),
            result.simulation.events.map { it.type },
        )
        listOf(0.25, 0.7, 0.95).zip(result.simulation.events).forEach { (time, event) ->
            assertEquals(time, event.elapsedSeconds, 1e-7)
        }
        assertEquals(board, result.candidate.shot.boardState)
        val repeated = requireNotNull(generate(request(), listOf(BankWall.LEFT)))
        assertEquals(result.simulation.events, repeated.simulation.events)
        val evaluation =
            ShotEvaluation.fromSimulation(
                result.candidate,
                result.simulation,
                PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(1.0, 0.0, 0.0)),
            )
        assertTrue(evaluation.accepted)
        assertThrows(UnsupportedOperationException::class.java) { (result.path.points as MutableList).clear() }
    }

    @Test
    fun `double bank matches unfolded length and ordered golden events at two clocks`() {
        // Unfolded displacement (-60,-100), then LEFT and TOP give final direction (60,100).
        val length = sqrt(13600.0)
        val normal = Vec2(60.0 / length, 100.0 / length)
        val ghost = Vec2(40.0, 40.0)
        val placement = Vec2(30.0, 70.0)
        val discCenter = Vec2(ghost.x + 10.0 * normal.x, ghost.y + 10.0 * normal.y)
        val scene =
            BoardState(
                bounds,
                listOf(Pocket("p", Circle(Vec2(ghost.x + 60.0 * normal.x, ghost.y + 60.0 * normal.y), 20.0))),
                listOf(Disc("d", DiscKind.BLACK, Circle(discCenter, 5.0))),
                Striker("s", Circle(placement, 5.0)),
            )
        val proposal =
            request(scene, placement).copy(
                pocketAim =
                    scene.pockets
                        .single()
                        .circle.center,
            )
        val walls = listOf(BankWall.LEFT, BankWall.TOP)
        // Explicit drag also brings floating-point post-transfer striker residuals to rest.
        val settings = physics.copy(deceleration = 1.0)
        val coarse = requireNotNull(generate(proposal, walls, settings))
        val fine = requireNotNull(generate(proposal, walls, settings.copy(timeStepSeconds = 0.01, maxSteps = 1000)))
        assertEquals(ShotType.DOUBLE_BANK, coarse.candidate.type)
        assertEquals(listOf("LEFT", "TOP", "s", "p"), coarse.simulation.events.map { it.target })
        val distances = listOf(25.0 / normal.x, 65.0 / normal.y, length, length + 35.0)
        val times = distances.map { 2.0 * it / (100.0 + sqrt(10000.0 - 2.0 * it)) }
        times.zip(coarse.simulation.events).forEach { (time, event) -> assertEquals(time, event.elapsedSeconds, 1e-7) }
        coarse.simulation.events.zip(fine.simulation.events).forEach { (a, b) ->
            assertEquals(a.type, b.type)
            assertEquals(a.bodyId, b.bodyId)
            assertEquals(a.target, b.target)
            assertEquals(a.elapsedSeconds, b.elapsedSeconds, 1e-7)
        }
        assertEquals(4, fine.simulation.events.size)
        assertTrue(
            coarse.simulation.finalState.discs
                .isEmpty(),
        )
    }

    @Test
    fun `blocked early leg target interception tangency and illegal placement reject`() {
        for (center in listOf(Vec2(15.0, 50.0), Vec2(15.0, 60.0), Vec2(75.0, 50.0))) {
            val scene =
                BoardState(
                    bounds,
                    board.pockets,
                    board.discs + Disc("block", DiscKind.WHITE, Circle(center, 5.0)),
                    board.striker,
                )
            assertNull(generate(request(scene), listOf(BankWall.LEFT)))
        }
        // The target itself blocks the outward leg toward RIGHT.
        assertNull(generate(request(), listOf(BankWall.RIGHT)))
        assertNull(StrikerBankGenerator().generate(request(), emptyList(), listOf(BankWall.LEFT), config, physics))
    }

    @Test
    fun `early capture insufficient energy truncation and lossy wall reject`() {
        val scene =
            BoardState(
                bounds,
                board.pockets + Pocket("foul", Circle(Vec2(15.0, 50.0), 6.0)),
                board.discs,
                board.striker,
            )
        assertNull(generate(request(scene), listOf(BankWall.LEFT)))
        assertNull(generate(request(), listOf(BankWall.LEFT), physics.copy(deceleration = 1000.0)))
        assertNull(generate(request(), listOf(BankWall.LEFT), physics.copy(timeStepSeconds = 0.01, maxSteps = 1)))
        assertNull(generate(request(), listOf(BankWall.LEFT), physics.copy(wallRestitution = 0.0)))
    }

    @Test
    fun `moving scenes and simulator budget failures propagate`() {
        val moving = board.discs.single().copy(velocity = Vec2(1.0, 0.0))
        val scene = BoardState(bounds, board.pockets, listOf(moving), board.striker)
        assertThrows(IllegalArgumentException::class.java) { generate(request(scene), listOf(BankWall.LEFT)) }
        assertThrows(IllegalArgumentException::class.java) {
            generate(request(), listOf(BankWall.LEFT), physics.copy(maxCollisionIterations = 1))
        }
    }

    private fun request(
        scene: BoardState = board,
        placement: Vec2 = start,
    ): ContactShotRequest =
        ContactShotRequest(
            "bank",
            scene,
            PocketTarget("d", "p"),
            placement,
            scene.pockets
                .first()
                .circle.center,
            0.5,
        )

    private fun generate(
        request: ContactShotRequest,
        walls: List<BankWall>,
        settings: PhysicsConfig = physics,
    ): StrikerBankShot? =
        StrikerBankGenerator().generate(
            request = request,
            legalStrikerPositions = listOf(request.strikerPosition),
            walls = walls,
            config = config,
            physics = settings,
        )
}

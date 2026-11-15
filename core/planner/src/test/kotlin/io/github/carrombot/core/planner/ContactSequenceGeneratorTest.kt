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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ContactSequenceGeneratorTest {
    private val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0))
    private val config = ContactShotConfig(PowerModel(200.0), 0.0, 1e-7)
    private val physics = PhysicsConfig(2.0, 10, deceleration = 1.0)

    @Test
    fun `disc bank has analytic ordered transfer reflection and capture at two clocks`() {
        val root = kotlin.math.sqrt(17.0)
        val board = scene(listOf(disc("d", 60.0)), Vec2(60.0 - 120.0 / root, 50.0 - 30.0 / root), Vec2(0.0, 80.0))
        val request = request(board, "d", Vec2(10.0, 80.0))
        val coarse = requireNotNull(bank(request))
        val fine = requireNotNull(bank(request, physics.copy(timeStepSeconds = 0.01, maxSteps = 1000)))
        assertEquals(ShotType.DISC_BANK, coarse.candidate.type)
        assertEquals(listOf("s", "RIGHT", "p"), coarse.simulation.events.map { it.target })
        // Unfolded ray is (120,30); right rail hit is (95,58.75).
        listOf(
            20.0,
            20.0 + 35.0 * root / 4.0,
            20.0 + 35.0 * root / 4.0 + 401.25 / root - kotlin.math.sqrt(225.0 - 100.0 / 17.0),
        ).zip(coarse.simulation.events).forEach { (distance, event) ->
            val time = 2.0 * distance / (100.0 + kotlin.math.sqrt(10000.0 - 2.0 * distance))
            assertEquals(time, event.elapsedSeconds, 1e-7)
        }
        coarse.simulation.events.zip(fine.simulation.events).forEach { (a, b) ->
            assertEquals(a.type, b.type)
            assertEquals(a.elapsedSeconds, b.elapsedSeconds, 1e-7)
        }
    }

    @Test
    fun `two and three disc chains transfer in order and preserve ownership`() {
        for (ids in listOf(listOf("a", "b"), listOf("a", "b", "c"))) {
            val discs = ids.mapIndexed { index, id -> disc(id, 30.0 + 20.0 * index) }
            val scene = scene(discs, Vec2(10.0, 50.0), Vec2(100.0, 50.0))
            val request = request(scene, ids.last(), Vec2(90.0, 50.0))
            val mutableIds = ids.toMutableList()
            val result = requireNotNull(chain(request, mutableIds))
            mutableIds.clear()
            assertEquals(ids, result.discIds)
            assertEquals(ids.size + 1, result.simulation.events.size)
            assertEquals(
                ids.last(),
                result.simulation.captures
                    .single()
                    .bodyId,
            )
            assertEquals(scene, result.candidate.shot.boardState)
            assertThrows(UnsupportedOperationException::class.java) { (result.discIds as MutableList).clear() }
        }
    }

    @Test
    fun `obstructions tangency illegal placement and wrong order prune`() {
        val base = scene(listOf(disc("a", 30.0), disc("b", 60.0)), Vec2(10.0, 50.0), Vec2(100.0, 50.0))
        for (y in listOf(50.0, 60.0)) {
            val blocked = BoardState(bounds, base.pockets, base.discs + disc("block", 45.0, y), base.striker)
            assertNull(chain(request(blocked, "b", Vec2(90.0, 50.0)), listOf("a", "b")))
        }
        val req = request(base, "b", Vec2(90.0, 50.0))
        assertNull(CombinationShotGenerator().generate(req, emptyList(), listOf("a", "b"), config, physics))
        assertThrows(IllegalArgumentException::class.java) { chain(req, listOf("b", "a")) }
        assertThrows(IllegalArgumentException::class.java) { chain(req, listOf("a", "a", "b")) }
    }

    @Test
    fun `bank rejects corners early capture and blocked rebound`() {
        val corner = scene(listOf(disc("d", 60.0, 60.0)), Vec2(40.0, 40.0), Vec2(20.0, 20.0))
        assertNull(
            DiscBankGenerator().generate(
                request(corner, "d", Vec2(20.0, 20.0)),
                listOf(Vec2(40.0, 40.0)),
                listOf(BankWall.RIGHT, BankWall.BOTTOM),
                config,
                physics,
            ),
        )
        val base = scene(listOf(disc("d", 60.0)), Vec2(40.0, 50.0), Vec2(0.0, 50.0))
        val blocked = BoardState(bounds, base.pockets, base.discs + disc("block", 80.0), base.striker)
        assertNull(bank(request(blocked, "d", Vec2(10.0, 50.0))))
        val earlyPocket = Pocket("early", Circle(Vec2(80.0, 50.0), 6.0))
        val early = BoardState(bounds, base.pockets + earlyPocket, base.discs, base.striker)
        assertNull(bank(request(early, "d", Vec2(10.0, 50.0))))
    }

    @Test
    fun `truncated insufficient energy and invalid scene never produce success`() {
        val root = kotlin.math.sqrt(17.0)
        val start = Vec2(60.0 - 120.0 / root, 50.0 - 30.0 / root)
        val base = scene(listOf(disc("d", 60.0)), start, Vec2(0.0, 80.0))
        val req = request(base, "d", Vec2(10.0, 80.0))
        requireNotNull(bank(req))
        assertNull(bank(req, physics.copy(wallRestitution = 0.0)))
        assertNull(bank(req, physics.copy(timeStepSeconds = 0.01, maxSteps = 1)))
        assertNull(bank(req, physics.copy(deceleration = 1000.0)))
        val movingDisc = base.discs.single().copy(velocity = Vec2(1.0, 0.0))
        val moving = BoardState(bounds, base.pockets, listOf(movingDisc), base.striker)
        assertThrows(IllegalArgumentException::class.java) { bank(request(moving, "d", Vec2(10.0, 50.0))) }
        assertThrows(IllegalArgumentException::class.java) { bank(req, physics.copy(maxCollisionIterations = 1)) }
    }

    @Test
    fun `bounded enumeration is stable unique and owns nested lists`() {
        val discs = listOf(disc("a", 30.0), disc("b", 50.0), disc("c", 70.0))
        val board = scene(discs, Vec2(10.0, 50.0), Vec2(100.0, 50.0))
        val generator = CombinationShotGenerator()
        val sequences = generator.enumerateSequences(board, "c", 3, 10)
        val expected = listOf(listOf("a", "c"), listOf("b", "c"), listOf("a", "b", "c"), listOf("b", "a", "c"))
        assertEquals(expected, sequences)
        assertEquals(sequences.take(1), generator.enumerateSequences(board, "c", 3, 1))
        assertThrows(UnsupportedOperationException::class.java) { (sequences as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (sequences.first() as MutableList).clear() }
        assertThrows(IllegalArgumentException::class.java) { generator.enumerateSequences(board, "c", 5, 10) }
        assertEquals(16, DiscBankGenerator().enumerateWallSequences(2).size)
    }

    @Test
    fun `cut transfer carom completes without extra impulses`() {
        val root = kotlin.math.sqrt(2.0)
        val start = Vec2(40.0 - 30.0 / root, 50.0 - 30.0 / root)
        val board = scene(listOf(disc("a", 40.0), disc("b", 60.0, 60.0)), start, Vec2(90.0, 60.0))
        val req = request(board, "b", Vec2(80.0, 60.0))
        val result =
            requireNotNull(
                CombinationShotGenerator().generate(
                    request = req,
                    legalStrikerPositions = listOf(start),
                    discIds = listOf("a", "b"),
                    config = config,
                    physics = physics.copy(deceleration = 60.0),
                ),
            )
        assertEquals(listOf("a", "b"), result.discIds)
        assertEquals(3, result.simulation.events.size)
    }

    @Test
    fun `double disc bank completes the requested two rail order`() {
        val root = kotlin.math.sqrt(13600.0)
        val start = Vec2(30.0 + 1200.0 / root, 70.0 + 2000.0 / root)
        val board = scene(listOf(disc("d", 30.0, 70.0)), start, Vec2(40.0, 40.0))
        val result =
            requireNotNull(
                DiscBankGenerator().generate(
                    request = request(board, "d", Vec2(40.0, 40.0)),
                    legalStrikerPositions = listOf(start),
                    walls = listOf(BankWall.LEFT, BankWall.TOP),
                    config = config,
                    physics = physics,
                ),
            )
        assertEquals(listOf("s", "LEFT", "TOP", "p"), result.simulation.events.map { it.target })
        val evaluated =
            ShotEvaluation.fromSimulation(
                result.candidate,
                result.simulation,
                PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(1.0, 0.0, 0.0)),
            )
        org.junit.Assert.assertTrue(evaluated.accepted)
    }

    @Test
    fun `four disc bound succeeds and unknown or oversized chains throw`() {
        val discs = listOf(disc("a", 25.0), disc("b", 40.0), disc("c", 55.0), disc("d", 70.0))
        val board = scene(discs, Vec2(10.0, 50.0), Vec2(100.0, 50.0))
        val req = request(board, "d", Vec2(90.0, 50.0))
        val result = requireNotNull(chain(req, listOf("a", "b", "c", "d")))
        assertEquals(4, result.candidate.constraints.minBodyContacts)
        assertThrows(IllegalArgumentException::class.java) { chain(req, listOf("missing", "d")) }
        assertThrows(IllegalArgumentException::class.java) { chain(req, listOf("x", "a", "b", "c", "d")) }
    }

    private fun disc(
        id: String,
        x: Double,
        y: Double = 50.0,
    ): Disc = Disc(id, DiscKind.BLACK, Circle(Vec2(x, y), 5.0))

    private fun scene(
        discs: List<Disc>,
        start: Vec2,
        pocket: Vec2,
    ): BoardState =
        BoardState(
            bounds = bounds,
            pockets = listOf(Pocket("p", Circle(pocket, 20.0))),
            discs = discs,
            striker = Striker("s", Circle(start, 5.0)),
        )

    private fun request(
        board: BoardState,
        id: String,
        aim: Vec2,
    ): ContactShotRequest =
        ContactShotRequest(
            id = "sequence",
            board = board,
            target = PocketTarget(id, "p"),
            strikerPosition = board.striker.circle.center,
            pocketAim = aim,
            power = 0.5,
        )

    private fun bank(
        request: ContactShotRequest,
        settings: PhysicsConfig = physics,
    ): ContactSequenceShot? =
        DiscBankGenerator().generate(request, listOf(request.strikerPosition), listOf(BankWall.RIGHT), config, settings)

    private fun chain(
        request: ContactShotRequest,
        ids: List<String>,
    ): ContactSequenceShot? =
        CombinationShotGenerator().generate(
            request = request,
            legalStrikerPositions = listOf(request.strikerPosition),
            discIds = ids,
            config = config,
            physics = physics,
        )
}

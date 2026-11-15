package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotSimulatorTest {
    @Test
    fun `checkpoint preserves golden evidence and abort exceptions propagate unchanged`() {
        val fixture = ShotFixture.load().first()
        val shot = ShotInput(fixture.board, Vec2(100.0, 0.0))
        val config = PhysicsConfig(0.01, 100, deceleration = 10.0)
        val simulator = ShotSimulator()
        val baseline = simulator.simulate(fixture.board, shot, config)
        var checkpoints = 0
        val checked = simulator.simulate(fixture.board, shot, config) { checkpoints++ }
        assertTrue(checkpoints > config.maxSteps)
        assertEquals(baseline.prediction.frames, checked.prediction.frames)
        assertEquals(baseline.events, checked.events)
        assertEquals(baseline.captures, checked.captures)
        val cancelled = java.util.concurrent.CancellationException("stop")
        var visits = 0
        val thrown =
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                simulator.simulate(fixture.board, shot, config) {
                    if (++visits == 5) throw cancelled
                }
            }
        org.junit.Assert.assertSame(cancelled, thrown)
        assertEquals(5, visits)
    }

    @Test
    fun `fractional striker capture at wall preserves strict containment`() {
        val initial =
            BoardState(
                BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
                listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.2))),
                emptyList(),
                Striker("s", Circle(Vec2(50.0, 50.0), 10.1)),
            )
        val result = run(initial, Vec2(50.0, 0.0), PhysicsConfig(1.0, 1))
        assertTrue(result.strikerCaptured)
        assertTrue(initial.bounds.contains(result.finalState.striker.circle))
        assertTrue(result.events.none { it.type == ShotEventType.WALL_CONTACT })
    }

    @Test
    fun `corner aperture captures before both walls and records queen outcome`() {
        val initial =
            board(
                listOf(Disc("q", DiscKind.QUEEN, Circle(Vec2(60.0, 60.0), 5.0), Vec2(100.0, 100.0))),
                listOf(Pocket("p", Circle(Vec2(100.0, 100.0), 20.0))),
                Vec2(20.0, 50.0),
            )
        val result = run(initial, Vec2(1.0, 0.0), PhysicsConfig(1.0, 1))
        assertEquals(DiscKind.QUEEN, result.captures.single().kind)
        assertEquals(0.4 - 0.15 / kotlin.math.sqrt(2.0), result.captures.single().elapsedSeconds, 1e-8)
        assertTrue(result.events.none { it.type == ShotEventType.WALL_CONTACT })
        assertTrue(result.finalState.discs.isEmpty())
    }

    @Test
    fun `striker capture snapshot uses event position and shared budget still fails explicitly`() {
        val initial = board(emptyList(), listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))), Vec2(30.0, 50.0))
        val result = run(initial, Vec2(100.0, 0.0), PhysicsConfig(1.0, 3))
        assertEquals(85.0, result.finalState.striker.circle.center.x, 1e-8)
        assertEquals(0.55, result.captures.single().elapsedSeconds, 1e-8)
        assertEquals(io.github.carrombot.core.model.PredictionTermination.REST, result.prediction.termination)
        assertThrows(IllegalArgumentException::class.java) {
            run(initial, Vec2(100.0, 0.0), PhysicsConfig(1.0, 1, maxCollisionIterations = 1))
        }
    }

    @Test
    fun `versioned golden shot families share one deterministic simulator`() {
        for (fixture in ShotFixture.load()) {
            val fields = fixture.fields
            val discs = fixture.board.discs
            val board = fixture.board
            val velocity = fixture.velocity
            val config = fixture.config
            val result = run(board, velocity, config)
            val expectedStriker = fields[9].split(',').map(String::toDouble)
            assertEquals(expectedStriker[0], result.finalState.striker.circle.center.x, 1e-7)
            assertEquals(expectedStriker[1], result.finalState.striker.circle.center.y, 1e-7)
            if (fields[10] != "-") {
                val expected = fields[10].split(',')
                val disc = result.finalState.discs.single { it.id == expected[0] }
                assertEquals(expected[1].toDouble(), disc.circle.center.x, 1e-7)
                assertEquals(expected[2].toDouble(), disc.circle.center.y, 1e-7)
            }
            assertEquals(fields[0], fields[4].split(','), result.captures.map { it.bodyId })
            fields[5].split(',').map(String::toDouble).zip(result.captures).forEach { (time, capture) ->
                assertEquals(fields[0], time, capture.elapsedSeconds, 1e-7)
            }
            assertEquals(fields[6].toInt(), result.events.count { it.type == ShotEventType.BODY_CONTACT })
            assertEquals(fields[7].toInt(), result.events.count { it.type == ShotEventType.WALL_CONTACT })
            assertTrue(result.events.zipWithNext().all { (a, b) -> a.elapsedSeconds <= b.elapsedSeconds })
            assertEquals(result.events, run(board, velocity, config).events)
            val fine = run(board, velocity, config.copy(timeStepSeconds = 0.01, maxSteps = 200))
            assertEquals(result.captures.map { it.bodyId }, fine.captures.map { it.bodyId })
            result.captures.zip(fine.captures).forEach { (a, b) ->
                assertEquals(a.elapsedSeconds, b.elapsedSeconds, 1e-7)
            }
            assertEquals(board.discs, discs)
            assertEquals(
                discs.filterNot { it.id in result.captures.map { c -> c.bodyId } }.map { it.id },
                result.finalState.discs.map { it.id },
            )
        }
    }

    @Test
    fun `captured striker freezes at capture and never collides again`() {
        val initial =
            board(
                listOf(disc("d", 60.0, 50.0, -100.0)),
                listOf(Pocket("p", Circle(Vec2(30.0, 50.0), 10.0))),
                Vec2(30.0, 50.0),
            )
        val result = run(initial, Vec2(100.0, 0.0), PhysicsConfig(0.1, 5))
        assertTrue(result.strikerCaptured)
        assertEquals(0.0, result.captures.first { it.bodyId == "s" }.elapsedSeconds, 0.0)
        assertEquals(Vec2(30.0, 50.0), result.finalState.striker.circle.center)
        assertEquals(Vec2(0.0, 0.0), result.finalState.striker.velocity)
        assertTrue(result.events.none { it.type == ShotEventType.BODY_CONTACT })
    }

    @Test
    fun `capture wins wall and body ties with deterministic overlapping pocket selection`() {
        val initial =
            board(
                listOf(disc("a", 90.0, 50.0, 100.0), disc("b", 80.0, 50.0, 100.0)),
                listOf(
                    Pocket("z", Circle(Vec2(100.0, 50.0), 10.0)),
                    Pocket("p", Circle(Vec2(100.0, 50.0), 10.0)),
                ),
                Vec2(20.0, 80.0),
            )
        val result = run(initial, Vec2(1.0, 0.0), PhysicsConfig(0.2, 1))
        assertEquals(listOf("a", "b"), result.captures.map { it.bodyId })
        assertTrue(result.captures.all { it.pocketId == "p" })
        assertTrue(result.events.all { it.type == ShotEventType.CAPTURE })
    }

    @Test
    fun `aperture near miss undersized opening and drag stopping remain active`() {
        for (pocket in listOf(
            Pocket("p", Circle(Vec2(100.0, 50.0), 4.0)),
            Pocket("p", Circle(Vec2(100.0, 70.0), 20.0)),
        )) {
            val initial = board(emptyList(), listOf(pocket), Vec2(30.0, 50.0))
            assertTrue(run(initial, Vec2(100.0, 0.0), PhysicsConfig(1.0, 1)).captures.isEmpty())
        }
        val initial = board(emptyList(), listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))), Vec2(30.0, 50.0))
        val stopped = run(initial, Vec2(100.0, 0.0), PhysicsConfig(2.0, 1, deceleration = 100.0))
        assertTrue(stopped.captures.isEmpty())
        assertEquals(80.0, stopped.finalState.striker.circle.center.x, 1e-8)
        val captured = run(initial, Vec2(120.0, 0.0), PhysicsConfig(2.0, 1, deceleration = 100.0))
        assertTrue(captured.strikerCaptured)
        assertEquals(1.2 - kotlin.math.sqrt(0.34), captured.captures.single().elapsedSeconds, 1e-8)
    }

    @Test
    fun `result lists own immutable snapshots and mismatched input fails`() {
        val initial = board(emptyList(), emptyList(), Vec2(30.0, 50.0))
        val result = run(initial, Vec2(10.0, 0.0), PhysicsConfig(0.1, 1))
        assertThrows(UnsupportedOperationException::class.java) { (result.events as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (result.captures as MutableList).clear() }
        val other = board(emptyList(), emptyList(), Vec2(40.0, 50.0))
        assertThrows(IllegalArgumentException::class.java) {
            ShotSimulator().simulate(other, ShotInput(initial, Vec2(1.0, 0.0)), PhysicsConfig(1.0, 1))
        }
    }

    private fun disc(
        id: String,
        x: Double,
        y: Double,
        vx: Double,
    ): Disc = Disc(id, DiscKind.WHITE, Circle(Vec2(x, y), 5.0), Vec2(vx, 0.0))

    private fun board(
        discs: List<Disc>,
        pockets: List<Pocket>,
        center: Vec2,
    ): BoardState =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            pockets,
            discs,
            Striker("s", Circle(center, 5.0)),
        )

    private fun run(
        board: BoardState,
        velocity: Vec2,
        config: PhysicsConfig,
    ): SimulationResult = ShotSimulator().simulate(board, ShotInput(board, velocity), config)

    @Test
    fun `direct shot captures disc before closed wall`() {
        val board =
            BoardState(
                BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
                listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))),
                listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(60.0, 50.0), 5.0))),
                Striker("s", Circle(Vec2(30.0, 50.0), 5.0)),
            )
        val result = ShotSimulator().simulate(board, ShotInput(board, Vec2(100.0, 0.0)), PhysicsConfig(1.0, 1))
        assertTrue(result.finalState.discs.isEmpty())
        assertEquals("d", result.captures.single().bodyId)
        assertEquals(0.45, result.captures.single().elapsedSeconds, 1e-8)
        assertEquals(
            board,
            result.prediction.frames
                .first()
                .boardState,
        )
    }
}

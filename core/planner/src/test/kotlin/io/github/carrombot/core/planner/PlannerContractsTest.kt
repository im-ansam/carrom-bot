package io.github.carrombot.core.planner

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.physics.PhysicsConfig
import io.github.carrombot.core.physics.ShotSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

class PlannerContractsTest {
    private val board =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(Pocket("p", Circle(Vec2(100.0, 50.0), 20.0))),
            listOf(Disc("d", DiscKind.BLACK, Circle(Vec2(60.0, 50.0), 5.0))),
            Striker("s", Circle(Vec2(30.0, 50.0), 5.0)),
        )
    private val shot = ShotInput(board, Vec2(100.0, 0.0))
    private val config = PlannerConfig(PowerBounds(0.1, 1.0), ScoringWeights(2.0, 1.0, 1.0))

    @Test
    fun `candidate validates references and owns targets`() {
        val targets = mutableListOf(PocketTarget("d", "p"))
        val candidate = CandidateShot("a", ShotType.DIRECT, shot, 0.5, targets)
        targets.clear()
        assertEquals(listOf(PocketTarget("d", "p")), candidate.targets)
        assertThrows(UnsupportedOperationException::class.java) { (candidate.targets as MutableList).clear() }
        for (invalid in listOf(PocketTarget("missing", "p"), PocketTarget("d", "missing"), PocketTarget("s", "p"))) {
            assertThrows(IllegalArgumentException::class.java) {
                CandidateShot("a", ShotType.CUT, shot, 0.5, listOf(invalid))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { CandidateShot("a", ShotType.CUT, shot, 0.5, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            CandidateShot("a", ShotType.CUT, shot, 0.5, listOf(PocketTarget("d", "p"), PocketTarget("d", "p")))
        }
    }

    @Test
    fun `invalid scalar settings fail rather than clamp`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            assertThrows(IllegalArgumentException::class.java) { candidate(power = value) }
            assertThrows(IllegalArgumentException::class.java) { PowerBounds(value, 1.0) }
        }
        assertThrows(IllegalArgumentException::class.java) { PowerBounds(0.8, 0.2) }
        assertThrows(IllegalArgumentException::class.java) { ScoringWeights(-1.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { TrajectoryConstraints(-1, 1) }
        assertThrows(IllegalArgumentException::class.java) { ShotType(" ") }
    }

    @Test
    fun `simulation evaluation scores physical targets and ranks ties by power then stable ID`() {
        val result = ShotSimulator().simulate(board, shot, PhysicsConfig(0.1, 20, deceleration = 1.0))
        val evaluated =
            listOf(candidate("z"), candidate("b", 0.4), candidate("a", 0.4)).map {
                ShotEvaluation.fromSimulation(it, result, config)
            }
        assertEquals(listOf("a", "b", "z"), evaluated.sortedWith(ShotEvaluation.RANKING).map { it.candidate.id })
        assertEquals(2.0, requireNotNull(evaluated.first().score).total, 0.0)
        assertEquals(emptyList<RejectionReason>(), evaluated.first().rejections)
        val rejected = ShotEvaluation.fromSimulation(candidate(power = 0.05), result, config)
        assertEquals(listOf(RejectionReason.POWER_OUT_OF_BOUNDS), rejected.rejections)
        assertEquals(null, rejected.score)
        assertEquals(evaluated.first(), listOf(rejected, evaluated.first()).sortedWith(ShotEvaluation.RANKING).first())
    }

    @Test
    fun `truncated missed and constraint violating shots remain unscored with stable reasons`() {
        val short = ShotSimulator().simulate(board, shot, PhysicsConfig(0.01, 1))
        val constrained =
            CandidateShot(
                "a",
                ShotType.DOUBLE_BANK,
                shot,
                0.5,
                listOf(PocketTarget("d", "p")),
                TrajectoryConstraints(minWallContacts = 2, minBodyContacts = 1),
            )
        val evaluated = ShotEvaluation.fromSimulation(constrained, short, config)
        assertEquals(
            listOf(
                RejectionReason.TIME_LIMIT,
                RejectionReason.TARGET_NOT_CAPTURED,
                RejectionReason.TRAJECTORY_CONSTRAINT,
            ),
            evaluated.rejections,
        )
        assertEquals(null, evaluated.score)
        assertThrows(UnsupportedOperationException::class.java) { (evaluated.rejections as MutableList).clear() }
        val failure = ShotEvaluation.simulationFailed(constrained)
        assertEquals(listOf(RejectionReason.SIMULATION_FAILED), failure.rejections)
        assertEquals(null, failure.simulation)
        assertEquals(null, failure.score)
        val other = ShotInput(board, Vec2(-100.0, 0.0))
        val unrelated = ShotSimulator().simulate(board, other, PhysicsConfig(0.01, 1))
        assertThrows(IllegalArgumentException::class.java) {
            ShotEvaluation.fromSimulation(candidate(), unrelated, config)
        }
    }

    @Test
    fun `captured striker is a hard rejection`() {
        val foulBoard =
            BoardState(
                board.bounds,
                board.pockets + Pocket("foul", Circle(Vec2(30.0, 50.0), 10.0)),
                board.discs,
                board.striker,
            )
        val foulShot = ShotInput(foulBoard, Vec2(100.0, 0.0))
        val foulCandidate = CandidateShot("foul", ShotType.DIRECT, foulShot, 0.5, listOf(PocketTarget("d", "p")))
        val result = ShotSimulator().simulate(foulBoard, foulShot, PhysicsConfig(0.1, 20))
        val evaluated = ShotEvaluation.fromSimulation(foulCandidate, result, config)
        assertEquals(
            listOf(RejectionReason.STRIKER_CAPTURED, RejectionReason.TARGET_NOT_CAPTURED),
            evaluated.rejections,
        )
        assertEquals(null, evaluated.score)
    }

    @Test
    fun `score terms expose penalties and reject overflow`() {
        assertEquals(3.0, ShotScore(10.0, 3.0, 4.0).total, 0.0)
        assertThrows(IllegalArgumentException::class.java) { ShotScore(0.0, Double.MAX_VALUE, Double.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { ShotScore(Double.NaN, 0.0, 0.0) }
    }

    @Test
    fun `bank impulse bounds and wall penalty use the unified event timeline`() {
        val bankShot = ShotInput(board, Vec2(-100.0, 0.0))
        val constraints = TrajectoryConstraints(1, 1, 1, 1)
        val bank =
            CandidateShot(
                "bank",
                ShotType.STRIKER_BANK,
                bankShot,
                0.5,
                listOf(PocketTarget("d", "p")),
                constraints,
            )
        val result = ShotSimulator().simulate(board, bankShot, PhysicsConfig(0.1, 30, deceleration = 1.0))
        val evaluated = ShotEvaluation.fromSimulation(bank, result, config)
        assertEquals(ShotScore(2.0, 0.0, 1.0), evaluated.score)
        val forbidden =
            CandidateShot(
                "forbidden",
                bank.type,
                bankShot,
                0.5,
                bank.targets,
                constraints.copy(maxWallContacts = 0, minWallContacts = 0),
            )
        assertEquals(
            listOf(RejectionReason.TRAJECTORY_CONSTRAINT),
            ShotEvaluation.fromSimulation(forbidden, result, config).rejections,
        )
    }

    @Test
    fun `multiple pocket targets score together and incidental captures reduce utility`() {
        val moving = board.discs.single().copy(velocity = Vec2(100.0, 0.0))
        val second = Disc("e", DiscKind.WHITE, Circle(Vec2(40.0, 80.0), 5.0), Vec2(-100.0, 0.0))
        val multi =
            BoardState(
                board.bounds,
                board.pockets + Pocket("q", Circle(Vec2(0.0, 80.0), 20.0)),
                listOf(moving, second),
                board.striker.copy(circle = Circle(Vec2(20.0, 20.0), 5.0)),
            )
        val input = ShotInput(multi, Vec2(1.0, 0.0))
        val result = ShotSimulator().simulate(multi, input, PhysicsConfig(0.1, 30, deceleration = 1.0))
        val targets = listOf(PocketTarget("d", "p"), PocketTarget("e", "q"))
        val all = CandidateShot("all", ShotType.MULTI_POCKET, input, 0.5, targets)
        val one = CandidateShot("one", ShotType.DIRECT, input, 0.5, targets.take(1))
        val first = ShotEvaluation.fromSimulation(all, result, config)
        val other = ShotEvaluation.fromSimulation(one, result, config)
        assertEquals(4.0, requireNotNull(first.score).total, 0.0)
        assertEquals(1.0, requireNotNull(other.score).total, 0.0)
        assertEquals(first, listOf(other, first).sortedWith(ShotEvaluation.RANKING).first())
    }

    @Test
    fun `version one fixture round trips family keys power targets and contact bounds`() {
        val fixture = Properties()
        requireNotNull(javaClass.getResourceAsStream("/candidates-v1.properties")).use(fixture::load)
        assertEquals("1", fixture.getProperty("version"))
        val encoded = StringWriter().also { fixture.store(it, "test fixture only") }.toString()
        val restored = Properties().also { it.load(StringReader(encoded)) }
        val families =
            listOf(
                ShotType.DIRECT,
                ShotType.CUT,
                ShotType.STRIKER_BANK,
                ShotType.DISC_BANK,
                ShotType.DOUBLE_BANK,
                ShotType.COMBINATION,
                ShotType.MULTI_POCKET,
                ShotType("custom-hybrid"),
            )
        families.forEachIndexed { index, type ->
            val key = "candidate.$index"
            val fields = restored.getProperty(key).split(',')
            val constraints = TrajectoryConstraints(fields[4].toInt(), fields[5].toInt())
            val actual =
                CandidateShot(
                    key,
                    ShotType(fields[0]),
                    shot,
                    fields[1].toDouble(),
                    listOf(PocketTarget(fields[2], fields[3])),
                    constraints,
                )
            val expected =
                CandidateShot(
                    key,
                    type,
                    shot,
                    0.5,
                    listOf(PocketTarget("d", "p")),
                    TrajectoryConstraints(index, index + 1),
                )
            assertEquals(expected, actual)
            assertEquals(expected.hashCode(), actual.hashCode())
        }
    }

    private fun candidate(
        id: String = "a",
        power: Double = 0.5,
    ): CandidateShot = CandidateShot(id, ShotType.DIRECT, shot, power, listOf(PocketTarget("d", "p")))
}

package io.github.carrombot.core.physics

import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ShotFixtureTest internal constructor(
    private val fixture: ShotFixture,
) {
    @Test
    fun `ordered event snapshot matches every event including identity and time`() {
        val rows = resourceLines("/shot-events-v1.csv").filter { it.substringBefore(';') == fixture.name }
        assertTrue("Missing snapshot for $fixture", rows.isNotEmpty())
        val expected =
            rows.map { row ->
                val f = row.split(';')
                ShotEvent(f[1].toDouble(), ShotEventType.valueOf(f[2]), f[3], f[4])
            }
        val actual = fixture.simulate().events
        assertEquals("$fixture event count", expected.size, actual.size)
        expected.zip(actual).forEach { (a, b) ->
            assertEquals(a.copy(elapsedSeconds = b.elapsedSeconds), b)
            assertEquals(a.elapsedSeconds, b.elapsedSeconds, ShotFixture.TOLERANCE)
        }
    }

    @Test
    fun `repeated and fine clocks preserve complete events and terminal bodies`() {
        val result = fixture.simulate()
        repeat(3) {
            val repeated = fixture.simulate()
            assertEquals(result.events, repeated.events)
            assertEquals(result.captures, repeated.captures)
            assertEquals(result.prediction.frames, repeated.prediction.frames)
        }
        val fine = fixture.simulate(fixture.config.copy(timeStepSeconds = 0.01, maxSteps = 200))
        assertEquals(result.events.size, fine.events.size)
        result.events.zip(fine.events).forEach { (a, b) ->
            assertEquals(a.copy(elapsedSeconds = b.elapsedSeconds), b)
            assertEquals(a.elapsedSeconds, b.elapsedSeconds, ShotFixture.TOLERANCE)
        }
        assertVector(result.finalState.striker.circle.center, fine.finalState.striker.circle.center)
        assertVector(result.finalState.striker.velocity, fine.finalState.striker.velocity)
        assertEquals(result.finalState.discs.map { it.id }, fine.finalState.discs.map { it.id })
        result.finalState.discs.zip(fine.finalState.discs).forEach { (a, b) ->
            assertVector(a.circle.center, b.circle.center)
            assertVector(a.velocity, b.velocity)
        }
        assertEquals(result.prediction.termination, fine.prediction.termination)
    }

    private fun assertVector(
        expected: Vec2,
        actual: Vec2,
    ) {
        assertEquals(expected.x, actual.x, ShotFixture.TOLERANCE)
        assertEquals(expected.y, actual.y, ShotFixture.TOLERANCE)
    }

    @Test
    fun `captures remove identities permanently and correspond to timeline events`() {
        val result = fixture.simulate(fixture.config.copy(timeStepSeconds = 0.01, maxSteps = 200))
        var active =
            fixture.board.discs
                .map { it.id }
                .toSet()
        result.prediction.frames.forEach { frame ->
            val current =
                frame.boardState.discs
                    .map { it.id }
                    .toSet()
            assertTrue("$fixture no resurrection", active.containsAll(current))
            active = current
        }
        val captured = result.captures.filter { it.kind != null }.map { it.bodyId }
        assertEquals(captured.size, captured.distinct().size)
        assertEquals(
            fixture.board.discs
                .map { it.id }
                .toSet() - captured.toSet(),
            active,
        )
        val events = result.events.filter { it.type == ShotEventType.CAPTURE }
        assertEquals(result.captures.size, events.size)
        result.captures.zip(events).forEach { (capture, event) ->
            assertEquals(capture.bodyId, event.bodyId)
            assertEquals(capture.pocketId, event.target)
            assertEquals(capture.elapsedSeconds, event.elapsedSeconds, 0.0)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<Any>> = ShotFixture.load().map { arrayOf(it) }
    }
}

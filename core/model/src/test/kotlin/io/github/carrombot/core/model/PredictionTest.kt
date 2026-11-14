package io.github.carrombot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PredictionTest {
    private val state =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            emptyList(),
            listOf(Disc("disc-1", DiscKind.BLACK, Circle(Vec2(50.0, 50.0), 5.0))),
            Striker("striker-1", Circle(Vec2(50.0, 90.0), 10.0)),
        )
    private val shot = ShotInput(state, Vec2(0.0, -100.0))

    @Test
    fun `prediction owns ordered frames and preserves value equality`() {
        val frames = mutableListOf(PredictionFrame(0.0, state), PredictionFrame(0.1, state))
        val prediction = ShotPrediction(shot, frames, PredictionTermination.TIME_LIMIT)
        frames.clear()
        assertEquals(2, prediction.frames.size)
        assertEquals(prediction, ShotPrediction(shot, prediction.frames, PredictionTermination.TIME_LIMIT))
        val equal = ShotPrediction(shot, prediction.frames, PredictionTermination.TIME_LIMIT)
        assertEquals(prediction.hashCode(), equal.hashCode())
        assertThrows(UnsupportedOperationException::class.java) { (prediction.frames as MutableList).clear() }
    }

    @Test
    fun `zero shot velocity invalid times and unordered frames fail explicitly`() {
        assertThrows(IllegalArgumentException::class.java) { ShotInput(state, Vec2(-0.0, 0.0)) }
        for (time in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { PredictionFrame(time, state) }
        }
        for (times in listOf(emptyList(), listOf(0.1), listOf(0.0, 0.0), listOf(0.0, 0.2, 0.1))) {
            assertThrows(IllegalArgumentException::class.java) {
                ShotPrediction(shot, times.map { PredictionFrame(it, state) }, PredictionTermination.TIME_LIMIT)
            }
        }
    }

    @Test
    fun `frames reject identity or geometry changes but allow removed discs`() {
        val removed = BoardState(state.bounds, state.pockets, emptyList(), state.striker)
        val frames = listOf(PredictionFrame(0.0, state), PredictionFrame(1.0, removed))
        ShotPrediction(shot, frames, PredictionTermination.REST)
        val disc = state.discs.single()
        val invalidStates =
            listOf(
                BoardState(state.bounds, state.pockets, listOf(disc.copy(id = "new-id")), state.striker),
                BoardState(state.bounds, state.pockets, listOf(disc.copy(kind = DiscKind.QUEEN)), state.striker),
                BoardState(
                    state.bounds,
                    state.pockets,
                    listOf(disc.copy(circle = disc.circle.copy(radius = 6.0))),
                    state.striker,
                ),
                BoardState(state.bounds, state.pockets, state.discs, state.striker.copy(id = "new-striker")),
                BoardState(BoardBounds(Vec2(0.0, 0.0), Vec2(200.0, 200.0)), state.pockets, state.discs, state.striker),
            )
        for (invalid in invalidStates) {
            assertThrows(IllegalArgumentException::class.java) {
                val invalidFrames = listOf(PredictionFrame(0.0, state), PredictionFrame(1.0, invalid))
                ShotPrediction(shot, invalidFrames, PredictionTermination.REST)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShotPrediction(shot, listOf(PredictionFrame(0.0, removed)), PredictionTermination.REST)
        }
    }

    @Test
    fun `positions and velocities change without changing IDs and removed discs cannot return`() {
        val disc = state.discs.single()
        val moved =
            BoardState(
                state.bounds,
                state.pockets,
                listOf(disc.copy(circle = disc.circle.copy(center = Vec2(55.0, 45.0)), velocity = Vec2(5.0, -5.0))),
                state.striker.copy(circle = state.striker.circle.copy(center = Vec2(50.0, 80.0))),
            )
        val frames = listOf(PredictionFrame(0.0, state), PredictionFrame(1.0, moved))
        val prediction = ShotPrediction(shot, frames, PredictionTermination.TIME_LIMIT)
        assertEquals(
            disc.id,
            prediction.frames
                .last()
                .boardState.discs
                .single()
                .id,
        )
        val removed = BoardState(state.bounds, state.pockets, emptyList(), state.striker)
        assertThrows(IllegalArgumentException::class.java) {
            ShotPrediction(
                shot,
                frames + PredictionFrame(2.0, removed) + PredictionFrame(3.0, moved),
                PredictionTermination.TIME_LIMIT,
            )
        }
    }
}

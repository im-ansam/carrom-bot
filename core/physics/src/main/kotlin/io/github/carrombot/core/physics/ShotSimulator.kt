package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.PredictionFrame
import io.github.carrombot.core.model.PredictionTermination
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.ShotPrediction

/** Unified bounded shot simulation. Invalid arithmetic/contact geometry throws without partial success. */
public class ShotSimulator {
    /**
     * [boardState] must equal the shot's pre-impulse snapshot. Units are mm and seconds.
     * [checkpoint] cooperatively aborts by throwing; no partial evidence is returned.
     * The hook must not mutate inputs. The simulator itself reads no clock.
     */
    public fun simulate(
        boardState: BoardState,
        shot: ShotInput,
        config: PhysicsConfig,
        checkpoint: () -> Unit = {},
    ): SimulationResult {
        require(boardState == shot.boardState) { "Shot and board snapshots must agree" }
        checkpoint()
        val timeline = ShotTimeline()
        val engine = CollisionEngine(config, timeline, checkpoint)
        var board =
            BoardState(
                boardState.bounds,
                boardState.pockets,
                boardState.discs,
                boardState.striker.copy(velocity = shot.initialVelocity),
            )
        val frames = arrayListOf(PredictionFrame(0.0, boardState))
        for (step in 1..config.maxSteps) {
            checkpoint()
            board = engine.advance(board, (step - 1) * config.timeStepSeconds)
            frames.add(PredictionFrame(step * config.timeStepSeconds, board))
            if (atRest(board) && !engine.needsSeparation(board)) break
        }
        val reason = if (atRest(board)) PredictionTermination.REST else PredictionTermination.TIME_LIMIT
        checkpoint()
        return SimulationResult(
            ShotPrediction(shot, frames, reason),
            timeline.events,
            timeline.captures,
            timeline.resolvedEvents,
        )
    }

    private fun atRest(board: BoardState): Boolean =
        (board.discs.map { it.velocity } + board.striker.velocity).all { it.x == 0.0 && it.y == 0.0 }
}

internal class ShotTimeline {
    val events = arrayListOf<ShotEvent>()
    val captures = arrayListOf<PocketCapture>()
    val resolvedEvents = arrayListOf<ResolvedShotEvent>()
    val removed = hashSetOf<String>()
    var capturedStriker: ContactBody? = null
}

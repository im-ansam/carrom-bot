package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.PredictionFrame
import io.github.carrombot.core.model.PredictionTermination
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.ShotPrediction
import java.util.Collections

/**
 * Deterministic motion with optional constant deceleration, in mm and mm/s.
 * Between contacts, zero deceleration retains p(next) = p + v * dt, v(next) = v.
 * Resolves body contacts in stable ID order. No randomness or
 * pocketing is applied. Exact zero velocity defines rest.
 *
 * Each call owns its trajectory. Invalid/nonfinite arithmetic or leaving board bounds
 * throws [IllegalArgumentException]; no clamped or partial successful trajectory escapes.
 * Wall response and pocket capture are not implemented.
 */
public class MotionSimulator(
    public val config: PhysicsConfig,
) {
    private val collisions = CollisionEngine(config)

    /** Includes the initial snapshot; stops at rest or the configured time cap. */
    public fun simulate(boardState: BoardState): List<SimulationState> {
        val states = ArrayList<SimulationState>()
        var state = SimulationState(boardState)
        states.add(state)
        for (step in 1..config.maxSteps) {
            if (isAtRest(state.boardState) && !collisions.needsSeparation(state.boardState)) break
            state = advance(state.boardState, step)
            states.add(state)
        }
        return Collections.unmodifiableList(states)
    }

    /**
     * Applies the shot velocity only to the striker. Frame zero remains the pre-impulse
     * snapshot required by the shared prediction contract; the first moved frame is at dt.
     */
    public fun predict(shot: ShotInput): ShotPrediction {
        val board = shot.boardState
        val launched =
            BoardState(board.bounds, board.pockets, board.discs, board.striker.copy(velocity = shot.initialVelocity))
        val states = simulate(launched)
        val frames =
            listOf(PredictionFrame(0.0, board)) +
                states.drop(1).map { PredictionFrame(it.elapsedSeconds, it.boardState) }
        val reason =
            if (isAtRest(states.last().boardState)) PredictionTermination.REST else PredictionTermination.TIME_LIMIT
        return ShotPrediction(shot, frames, reason)
    }

    private fun advance(
        board: BoardState,
        step: Int,
    ): SimulationState = SimulationState(collisions.advance(board), step * config.timeStepSeconds)

    private fun isAtRest(board: BoardState): Boolean =
        board.discs.all { it.velocity.x == 0.0 && it.velocity.y == 0.0 } &&
            board.striker.velocity.x == 0.0 && board.striker.velocity.y == 0.0
}

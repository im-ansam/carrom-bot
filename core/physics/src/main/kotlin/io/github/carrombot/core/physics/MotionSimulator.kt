package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.PredictionFrame
import io.github.carrombot.core.model.PredictionTermination
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.ShotPrediction
import io.github.carrombot.core.model.Vec2
import java.util.Collections

/**
 * Deterministic motion with optional constant deceleration, in mm and mm/s.
 * Zero configured deceleration retains p(next) = p + v * dt, v(next) = v.
 * Traverses discs in input order then the striker. No randomness, collisions or
 * pocketing is applied. Exact zero velocity defines rest.
 *
 * Each call owns its trajectory. Invalid/nonfinite arithmetic or leaving board bounds
 * throws [IllegalArgumentException]; no clamped or partial successful trajectory escapes.
 * Callers must currently supply collision-free inputs for the entire time horizon.
 */
public class MotionSimulator(
    public val config: PhysicsConfig,
) {
    private val friction: FrictionModel? = config.deceleration.takeIf { it > 0.0 }?.let(::FrictionModel)

    /** Includes the initial snapshot; stops at rest or the configured time cap. */
    public fun simulate(boardState: BoardState): List<SimulationState> {
        val states = ArrayList<SimulationState>()
        var state = SimulationState(boardState)
        states.add(state)
        for (step in 1..config.maxSteps) {
            if (isAtRest(state.boardState)) break
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
    ): SimulationState {
        val discs =
            board.discs.map {
                val motion = motion(it.velocity)
                it.copy(circle = move(it.circle, motion.displacement), velocity = motion.velocity)
            }
        val motion = motion(board.striker.velocity)
        val striker =
            board.striker.copy(
                circle = move(board.striker.circle, motion.displacement),
                velocity = motion.velocity,
            )
        return SimulationState(BoardState(board.bounds, board.pockets, discs, striker), step * config.timeStepSeconds)
    }

    private fun move(
        circle: Circle,
        displacement: Vec2,
    ): Circle =
        circle.copy(
            center =
                Vec2(
                    circle.center.x + displacement.x,
                    circle.center.y + displacement.y,
                ),
        )

    private fun motion(velocity: Vec2): FrictionStep =
        friction?.step(velocity, config.timeStepSeconds) ?: FrictionStep(
            Vec2(velocity.x * config.timeStepSeconds, velocity.y * config.timeStepSeconds),
            velocity,
        )

    private fun isAtRest(board: BoardState): Boolean =
        board.discs.all { it.velocity.x == 0.0 && it.velocity.y == 0.0 } &&
            board.striker.velocity.x == 0.0 && board.striker.velocity.y == 0.0
}

package io.github.carrombot

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import io.github.carrombot.core.overlay.AnnotationKind
import io.github.carrombot.core.overlay.BodyTrajectory
import io.github.carrombot.core.overlay.OverlayScene
import io.github.carrombot.core.overlay.RenderedCandidate
import io.github.carrombot.core.overlay.ShotAnnotation
import io.github.carrombot.core.physics.ShotEventType
import io.github.carrombot.core.physics.SimulationResult
import io.github.carrombot.core.planner.PlanningResult
import io.github.carrombot.core.planner.ShotEvaluation

/** Adapts accepted simulations including resolved vertices, never sketches intended routes. */
internal object PlannerSceneAdapter {
    private const val MAX_VISIBLE_CANDIDATES = 3

    fun scene(
        result: PlanningResult,
        bounds: BoardBounds,
    ): OverlayScene {
        val candidates = result.plans.take(MAX_VISIBLE_CANDIDATES).map(::candidate)
        return OverlayScene(bounds, candidates, candidates.firstOrNull()?.id)
    }

    private fun candidate(evaluation: ShotEvaluation): RenderedCandidate {
        val simulation = checkNotNull(evaluation.simulation)
        check(simulation.events == simulation.resolvedEvents.map { it.event }) {
            "Missing resolved event geometry"
        }
        val initial = evaluation.candidate.shot.boardState
        val ids = initial.discs.map { it.id } + initial.striker.id
        val trajectories = ids.mapNotNull { id -> trajectory(simulation, id) }
        val annotations =
            simulation.resolvedEvents.map { resolved ->
                val kind =
                    when (resolved.event.type) {
                        ShotEventType.BODY_CONTACT -> AnnotationKind.CONTACT
                        ShotEventType.WALL_CONTACT -> AnnotationKind.REBOUND
                        ShotEventType.CAPTURE -> AnnotationKind.POCKET
                    }
                val position =
                    if (resolved.event.type == ShotEventType.CAPTURE) {
                        initial.pockets
                            .single { it.id == resolved.event.target }
                            .circle.center
                    } else if (resolved.event.type == ShotEventType.BODY_CONTACT) {
                        contactPoint(simulation, resolved)
                    } else {
                        resolved.bodyCenter
                    }
                ShotAnnotation(kind, position)
            }
        return RenderedCandidate(evaluation.candidate.id, trajectories, annotations, evaluation.candidate.power, null)
    }

    private fun contactPoint(
        simulation: SimulationResult,
        resolved: io.github.carrombot.core.physics.ResolvedShotEvent,
    ): Vec2 {
        val initial = simulation.prediction.shot.boardState
        val target = checkNotNull(resolved.targetCenter)
        val radius =
            initial.discs
                .find { it.id == resolved.event.bodyId }
                ?.circle
                ?.radius
                ?: initial.striker.circle.radius
        val dx = target.x - resolved.bodyCenter.x
        val dy = target.y - resolved.bodyCenter.y
        val distance = kotlin.math.hypot(dx, dy)
        return Vec2(
            resolved.bodyCenter.x + dx * radius / distance,
            resolved.bodyCenter.y + dy * radius / distance,
        )
    }

    private fun trajectory(
        simulation: SimulationResult,
        id: String,
    ): BodyTrajectory? {
        val captureTime = simulation.captures.firstOrNull { it.bodyId == id }?.elapsedSeconds
        val vertices = arrayListOf<Pair<Double, Vec2>>()
        simulation.prediction.frames.forEach { frame ->
            if (captureTime == null || frame.elapsedSeconds < captureTime) {
                val circle =
                    frame.boardState.discs
                        .find { it.id == id }
                        ?.circle
                        ?: frame.boardState.striker
                            .takeIf { it.id == id }
                            ?.circle
                circle?.let { vertices.add(frame.elapsedSeconds to it.center) }
            }
        }
        simulation.resolvedEvents.forEach { resolved ->
            if (resolved.event.bodyId == id) {
                vertices.add(resolved.event.elapsedSeconds to resolved.bodyCenter)
            }
            if (resolved.event.type == ShotEventType.BODY_CONTACT && resolved.event.target == id) {
                vertices.add(resolved.event.elapsedSeconds to checkNotNull(resolved.targetCenter))
            }
        }
        val points =
            vertices
                .sortedBy { it.first }
                .map { it.second }
                .fold(arrayListOf<Vec2>()) { values, point ->
                    if (values.lastOrNull() != point) values.add(point)
                    values
                }
        val striker = simulation.prediction.shot.boardState.striker.id
        return if (points.size < 2) null else BodyTrajectory(id, id == striker, points)
    }
}

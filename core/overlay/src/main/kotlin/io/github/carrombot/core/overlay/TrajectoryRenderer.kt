package io.github.carrombot.core.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import io.github.carrombot.core.model.Vec2

/**
 * Stateless Canvas renderer using local pixels and explicit rectified mapping. Call on the
 * Canvas-owner thread (main thread for live Views). Creates no windows and retains no scene or resources.
 * Draws alternatives first (dashed), selected last (solid); striker lines are thicker than disc lines.
 * Caller owns Canvas sizing and clearing. Canvas save/clip state is restored even if drawing fails.
 */
public class TrajectoryRenderer {
    public fun draw(
        canvas: Canvas,
        scene: OverlayScene,
        mapping: BoardMapping,
    ) {
        require(scene.bounds == mapping.bounds) { "Mapping belongs to a different board" }
        val prepared = scene.candidates.mapIndexed { index, candidate -> prepare(candidate, index, mapping) }
        val minimum = pixel(mapping.toScreen(scene.bounds.min))
        val maximum = pixel(mapping.toScreen(scene.bounds.max))
        val checkpoint = canvas.save()
        try {
            canvas.clipRect(minimum.first, minimum.second, maximum.first, maximum.second)
            prepared.sortedBy { it.candidate.id == scene.selectedId }.forEach { item ->
                drawCandidate(canvas, item, item.candidate.id == scene.selectedId)
            }
        } finally {
            canvas.restoreToCount(checkpoint)
        }
        ShotAnnotationRenderer().drawLegend(canvas, scene)
    }

    private fun prepare(
        candidate: RenderedCandidate,
        index: Int,
        mapping: BoardMapping,
    ): PreparedCandidate =
        PreparedCandidate(
            candidate,
            CANDIDATE_COLORS[index % CANDIDATE_COLORS.size],
            candidate.trajectories.map { trajectory ->
                trajectory to trajectory.points.map { pixel(mapping.toScreen(it)) }
            },
            candidate.annotations.map { it.kind to pixel(mapping.toScreen(it.position)) },
        )

    private fun drawCandidate(
        canvas: Canvas,
        item: PreparedCandidate,
        selected: Boolean,
    ) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.color
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                if (!selected) pathEffect = DashPathEffect(floatArrayOf(DASH_LENGTH, DASH_LENGTH), 0f)
            }
        item.paths.forEach { (trajectory, points) ->
            paint.strokeWidth = if (trajectory.isStriker) STRIKER_WIDTH else DISC_WIDTH
            val path = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.first, point.second) else path.lineTo(point.first, point.second)
            }
            canvas.drawPath(path, paint)
        }
        paint.pathEffect = null
        paint.strokeWidth = DISC_WIDTH
        item.markers.forEach { (kind, point) ->
            ShotAnnotationRenderer().drawMarker(canvas, kind, point.first, point.second, paint)
        }
    }

    private data class PreparedCandidate(
        val candidate: RenderedCandidate,
        val color: Int,
        val paths: List<Pair<BodyTrajectory, List<Pair<Float, Float>>>>,
        val markers: List<Pair<AnnotationKind, Pair<Float, Float>>>,
    )

    private companion object {
        const val DASH_LENGTH = 10f
        const val STRIKER_WIDTH = 5f
        const val DISC_WIDTH = 3f
    }
}

internal val CANDIDATE_COLORS: List<Int> = listOf(Color.CYAN, Color.YELLOW, Color.MAGENTA)

internal fun pixel(point: Vec2): Pair<Float, Float> {
    val x = point.x.toFloat()
    val y = point.y.toFloat()
    require(x.isFinite() && y.isFinite()) { "Mapped geometry exceeds Canvas precision" }
    return x to y
}

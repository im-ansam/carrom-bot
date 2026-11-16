package io.github.carrombot.core.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

/** Fixed local-pixel marker sizes and readable candidate/power/sensitivity legend, independent of board scale. */
public class ShotAnnotationRenderer {
    internal fun drawMarker(
        canvas: Canvas,
        kind: AnnotationKind,
        x: Float,
        y: Float,
        paint: Paint,
    ) {
        when (kind) {
            AnnotationKind.CONTACT -> {
                canvas.drawCircle(x, y, MARKER_RADIUS, paint)
            }

            AnnotationKind.REBOUND -> {
                canvas.drawRect(
                    x - MARKER_RADIUS,
                    y - MARKER_RADIUS,
                    x + MARKER_RADIUS,
                    y + MARKER_RADIUS,
                    paint,
                )
            }

            AnnotationKind.POCKET -> {
                canvas.drawCircle(x, y, POCKET_RADIUS, paint)
                canvas.drawLine(x - MARKER_RADIUS, y, x + MARKER_RADIUS, y, paint)
                canvas.drawLine(x, y - MARKER_RADIUS, x, y + MARKER_RADIUS, paint)
            }
        }
    }

    /** Legend at Canvas top-left; selected/alternative words preserve distinction without colour. */
    public fun drawLegend(
        canvas: Canvas,
        scene: OverlayScene,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TEXT_SIZE }
        scene.candidates.forEachIndexed { index, candidate ->
            val selected = candidate.id == scene.selectedId
            val confidence = candidate.confidence?.let { "${(it * PERCENT).roundToInt()}%" } ?: "unknown"
            val label =
                "${if (selected) "Selected" else "Alternative"} ${candidate.id.take(MAX_LABEL_LENGTH)}" +
                    " | Power ${(candidate.power * PERCENT).roundToInt()}% | Sensitivity $confidence"
            val baseline = LEGEND_MARGIN + TEXT_SIZE + index * ROW_HEIGHT
            paint.color = Color.BLACK
            canvas.drawRect(
                LEGEND_MARGIN,
                baseline - TEXT_SIZE,
                LEGEND_MARGIN + paint.measureText(label),
                baseline + TEXT_DESCENT,
                paint,
            )
            paint.color = CANDIDATE_COLORS[index % CANDIDATE_COLORS.size]
            canvas.drawText(label, LEGEND_MARGIN, baseline, paint)
        }
    }

    private companion object {
        const val MARKER_RADIUS = 6f
        const val POCKET_RADIUS = 10f
        const val TEXT_SIZE = 14f
        const val ROW_HEIGHT = 22f
        const val TEXT_DESCENT = 4f
        const val LEGEND_MARGIN = 8f
        const val PERCENT = 100
        const val MAX_LABEL_LENGTH = 24
    }
}

package io.github.carrombot.core.overlay

import android.content.Context
import android.graphics.Canvas
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * Passive Canvas surface for a caller-owned host. No window, permission request or input generation.
 * Mapping is in this view's local pixels; host must resubmit after resize/rotation. Window integration
 * belongs to the application pipeline; embedding this view does not grant overlay permission.
 */
public class TrajectoryView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private val renderer = TrajectoryRenderer()
        private var content: Pair<OverlayScene, BoardMapping>? = null

        /** Replaces assistance atomically on the main thread; invalid mapping fails before changing content. */
        public fun submit(
            scene: OverlayScene,
            mapping: BoardMapping,
        ) {
            checkMain()
            require(scene.bounds == mapping.bounds) { "Mapping belongs to a different board" }
            pixel(mapping.toScreen(scene.bounds.min))
            pixel(mapping.toScreen(scene.bounds.max))
            content = scene to mapping
            invalidate()
        }

        /** Clears retained advisory geometry without substituting placeholder data. */
        public fun clear() {
            checkMain()
            content = null
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            content?.let { (scene, mapping) -> renderer.draw(canvas, scene, mapping) }
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            val hadLayout = maxOf(oldw, oldh) > 0
            val resized = w != oldw || h != oldh
            if (hadLayout && resized) clear()
        }

        private fun checkMain() {
            check(Looper.myLooper() == Looper.getMainLooper()) { "Trajectory view operations require the main looper" }
        }
    }

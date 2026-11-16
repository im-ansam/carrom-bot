package io.github.carrombot.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Small accessible control surface. Trajectory rendering and board mapping are separate work.
 * Constructed only by OverlayController with its required Hide action, never inflated from XML.
 */
@SuppressLint("ViewConstructor")
public class AimOverlayView(
    context: Context,
    onHide: () -> Unit,
    public val controls: FloatingControls? = null,
) : FrameLayout(context) {
    init {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(column)
        column.addView(
            Button(context).apply {
                setText(R.string.hide_overlay)
                isAllCaps = false
                minimumHeight = (MINIMUM_TOUCH_DP * resources.displayMetrics.density).toInt()
                minimumWidth = minimumHeight
                setOnClickListener { onHide() }
            },
        )
        controls?.let(column::addView)
    }

    private companion object {
        const val MINIMUM_TOUCH_DP = 48
    }
}

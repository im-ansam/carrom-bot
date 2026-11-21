package io.github.carrombot

import io.github.carrombot.core.overlay.SyntheticPreviewLayout

/** Conservatively refuse a source covered by the opaque top/end controls before asking for consent. */
internal object DemoLayout {
    fun hasVerticalClearance(
        sourceTop: Int,
        sourceSize: Int,
        availableBottom: Int,
        displayWidth: Int,
        density: Float,
    ): Boolean {
        if (sourceTop < 0 || sourceSize <= 0 || availableBottom <= 0) return false
        val preview = SyntheticPreviewLayout.dimensions(displayWidth, density)
        return preview != null &&
            sourceTop.toLong() + sourceSize < availableBottom.toLong() - preview.height - preview.gap
    }

    /** Pixel dimensions only. A one-pixel fallback remains subject to the caller's minimum-size guard. */
    fun fitSourceSize(
        preferredSize: Int,
        displayWidth: Int,
        controlsWidth: Int,
        gap: Int,
    ): Int {
        val available = displayWidth.toLong() - controlsWidth - gap
        return minOf(preferredSize.toLong(), available).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun hasHorizontalClearance(
        sourceLeft: Int,
        sourceSize: Int,
        displayWidth: Int,
        controlsWidth: Int,
        gap: Int,
    ): Boolean {
        val invalidSource = sourceLeft < 0 || sourceSize <= 0
        val invalidControls = controlsWidth <= 0 || gap < 0
        if (invalidSource || invalidControls) return false
        return sourceLeft.toLong() + sourceSize <= displayWidth.toLong() - controlsWidth - gap
    }
}

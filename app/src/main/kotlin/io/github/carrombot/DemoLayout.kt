package io.github.carrombot

/** Conservatively refuse a source covered by the opaque top/end controls before asking for consent. */
internal object DemoLayout {
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

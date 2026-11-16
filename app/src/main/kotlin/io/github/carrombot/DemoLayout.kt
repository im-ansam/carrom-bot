package io.github.carrombot

/** Conservatively refuse a source covered by the opaque top/end controls before asking for consent. */
internal object DemoLayout {
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

package io.github.carrombot.core.overlay

/** Pixel dimensions shared by the synthetic preview window and its pre-consent overlap guard. */
public data class SyntheticPreviewDimensions internal constructor(
    public val width: Int,
    public val height: Int,
    public val legendHeight: Int,
    public val titleHeight: Int,
    public val gap: Int,
)

/** Synthetic-only layout calculation; independent of the captured source board's fitted size. */
public object SyntheticPreviewLayout {
    /** Returns null when display metrics cannot produce a positive, representable preview window. */
    @Suppress("MagicNumber") // Fixed synthetic preview dimensions in dp, rounded per child view.
    public fun dimensions(displayWidth: Int, density: Float): SyntheticPreviewDimensions? {
        if (displayWidth <= 0 || !density.isFinite() || density <= 0f) return null
        val gap = (16 * density).toInt()
        val width = displayWidth / 2 - gap
        val legendHeight = (100 * density).toInt()
        val titleHeight = (48 * density).toInt()
        val height = width.toLong() + legendHeight + titleHeight
        val invalidChildSize = width <= 0 || legendHeight <= 0 || titleHeight <= 0
        return if (invalidChildSize || height > Int.MAX_VALUE) {
            null
        } else {
            SyntheticPreviewDimensions(width, height.toInt(), legendHeight, titleHeight, gap)
        }
    }
}

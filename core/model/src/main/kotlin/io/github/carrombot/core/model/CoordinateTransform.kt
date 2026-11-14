package io.github.carrombot.core.model

/**
 * Axis-aligned, uniform board-mm to screen-pixel mapping with no rounding or clipping.
 * [boardOrigin] maps to [screenOrigin]; both coordinate systems have +y down.
 * Cropping/translation and positive scale are supported; rotation/perspective must be
 * rectified by an adapter before using this contract. Invalid scale or nonfinite results
 * throw [IllegalArgumentException]. Round-trip precision is limited by IEEE 754 Double.
 */
public data class CoordinateTransform(
    public val boardOrigin: Vec2,
    public val screenOrigin: Vec2,
    public val pixelsPerMillimetre: Double,
) {
    init {
        require(pixelsPerMillimetre.isFinite() && pixelsPerMillimetre > 0.0) {
            "Pixel scale must be finite and positive"
        }
    }

    public fun toScreen(boardPoint: Vec2): Vec2 =
        Vec2(
            screenOrigin.x + (boardPoint.x - boardOrigin.x) * pixelsPerMillimetre,
            screenOrigin.y + (boardPoint.y - boardOrigin.y) * pixelsPerMillimetre,
        )

    public fun toBoard(screenPoint: Vec2): Vec2 =
        Vec2(
            boardOrigin.x + (screenPoint.x - screenOrigin.x) / pixelsPerMillimetre,
            boardOrigin.y + (screenPoint.y - screenOrigin.y) / pixelsPerMillimetre,
        )
}

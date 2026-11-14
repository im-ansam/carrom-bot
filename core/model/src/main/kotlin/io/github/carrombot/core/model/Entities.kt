package io.github.carrombot.core.model

/** Stable caller-assigned pocket identity and aperture in millimetres; no pocketing rule is implied. */
public data class Pocket(
    public val id: String,
    public val circle: Circle,
) {
    init {
        require(id.isNotBlank()) { "Pocket ID must not be blank" }
    }
}

/** Visual disc classification, independent of player ownership or game rules. */
public enum class DiscKind {
    BLACK,
    WHITE,
    QUEEN,
}

/**
 * Active disc with a caller-assigned stable ID, geometry in mm and velocity in mm/s.
 * IDs persist when position/velocity changes; classification does not assign a player.
 */
public data class Disc(
    public val id: String,
    public val kind: DiscKind,
    public val circle: Circle,
    public val velocity: Vec2 = Vec2(0.0, 0.0),
) {
    init {
        require(id.isNotBlank()) { "Disc ID must not be blank" }
    }
}

/** Active striker; stable caller-assigned ID, geometry in mm and velocity in mm/s. */
public data class Striker(
    public val id: String,
    public val circle: Circle,
    public val velocity: Vec2 = Vec2(0.0, 0.0),
) {
    init {
        require(id.isNotBlank()) { "Striker ID must not be blank" }
    }
}

package io.github.carrombot.core.model

import java.util.Collections

/**
 * Immutable active-body snapshot, shared by all modules. Owns defensive copies of the
 * ordered lists; equality includes order. IDs are unique across pockets, discs and striker.
 * Bodies must fit fully inside bounds; pocket centres lie on/inside bounds but rims may cross.
 * Removed discs are absent; overlap and game legality are deliberately not inferred.
 * No capture resources, player ownership, timestamps or simulation behavior are owned here.
 * Invalid snapshots throw [IllegalArgumentException].
 */
public class BoardState(
    public val bounds: BoardBounds,
    pockets: List<Pocket>,
    discs: List<Disc>,
    public val striker: Striker,
) {
    public val pockets: List<Pocket> = immutableCopy(pockets)
    public val discs: List<Disc> = immutableCopy(discs)

    init {
        val ids = this.pockets.map { it.id } + this.discs.map { it.id } + striker.id
        require(ids.distinct().size == ids.size) { "Entity IDs must be unique" }
        require(this.pockets.all { bounds.contains(it.circle.center) }) { "Pocket centres must be in bounds" }
        require(this.discs.all { bounds.contains(it.circle) }) { "Discs must fit in bounds" }
        require(bounds.contains(striker.circle)) { "Striker must fit in bounds" }
    }

    override fun equals(other: Any?): Boolean =
        other is BoardState && bounds == other.bounds && pockets == other.pockets &&
            discs == other.discs && striker == other.striker

    override fun hashCode(): Int = listOf(bounds, pockets, discs, striker).hashCode()

    override fun toString(): String = "BoardState(bounds=$bounds, pockets=$pockets, discs=$discs, striker=$striker)"
}

internal fun <T> immutableCopy(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

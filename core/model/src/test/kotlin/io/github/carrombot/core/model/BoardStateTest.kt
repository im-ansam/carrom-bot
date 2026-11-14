package io.github.carrombot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Properties

class BoardStateTest {
    @Test
    fun `version one fixture reconstructs equal snapshots with stable IDs`() {
        val values = Properties()
        requireNotNull(javaClass.getResourceAsStream("/board-v1.properties")).use(values::load)
        assertEquals("1", values.getProperty("version"))
        val state =
            BoardState(
                BoardBounds(vector(values, "bounds.min"), vector(values, "bounds.max")),
                listOf(
                    Pocket(
                        values.getProperty("pocket.id"),
                        Circle(vector(values, "pocket.center"), number(values, "pocket.radius")),
                    ),
                ),
                listOf(
                    Disc(
                        values.getProperty("disc.id"),
                        DiscKind.valueOf(values.getProperty("disc.kind")),
                        Circle(vector(values, "disc.center"), number(values, "disc.radius")),
                        vector(values, "disc.velocity"),
                    ),
                ),
                Striker(
                    values.getProperty("striker.id"),
                    Circle(vector(values, "striker.center"), number(values, "striker.radius")),
                    vector(values, "striker.velocity"),
                ),
            )
        assertEquals(fixture("disc-1"), state)
        assertEquals(fixture().hashCode(), state.hashCode())
        assertEquals("striker-1", state.striker.id)
        assertEquals("pocket-tl", state.pockets.single().id)
        assertNotEquals(fixture("disc-2"), state)
        assertEquals(state.discs.single(), state.discs.single().copy())
    }

    @Test
    fun `snapshot owns collections and rejects mutations through JVM views`() {
        val original = fixture()
        val discs = original.discs.toMutableList()
        val pockets = original.pockets.toMutableList()
        val state = BoardState(original.bounds, pockets, discs, original.striker)
        discs.clear()
        pockets.clear()
        assertEquals(original, state)
        assertThrows(UnsupportedOperationException::class.java) { (state.discs as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (state.pockets as MutableList).clear() }
    }

    @Test
    fun `blank and duplicate IDs are rejected across entity types`() {
        val state = fixture()
        for (id in listOf("", " ", "\t")) {
            assertThrows(IllegalArgumentException::class.java) { state.discs.single().copy(id = id) }
            assertThrows(IllegalArgumentException::class.java) { state.striker.copy(id = id) }
            assertThrows(IllegalArgumentException::class.java) { state.pockets.single().copy(id = id) }
        }
        for (discs in listOf(state.discs + state.discs, listOf(state.discs.single().copy(id = state.striker.id)))) {
            assertThrows(IllegalArgumentException::class.java) {
                BoardState(state.bounds, state.pockets, discs, state.striker)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoardState(state.bounds, state.pockets + state.pockets, state.discs, state.striker)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoardState(state.bounds, listOf(state.pockets.single().copy(id = "disc-1")), state.discs, state.striker)
        }
    }

    @Test
    fun `out of bounds bodies and pocket centres are rejected but rim may cross edge`() {
        val state = fixture()
        assertEquals(
            Vec2(0.0, 0.0),
            state.pockets
                .single()
                .circle.center,
        )
        assertThrows(IllegalArgumentException::class.java) {
            val outside = state.discs.single().copy(circle = Circle(Vec2(0.0, 0.0), 5.0))
            BoardState(state.bounds, state.pockets, listOf(outside), state.striker)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val outside = state.striker.copy(circle = Circle(Vec2(100.0, 100.0), 10.0))
            BoardState(state.bounds, state.pockets, state.discs, outside)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val outside = state.pockets.single().copy(circle = Circle(Vec2(-1.0, 0.0), 5.0))
            BoardState(state.bounds, listOf(outside), state.discs, state.striker)
        }
        BoardState(state.bounds, emptyList(), emptyList(), state.striker)
    }

    private fun number(
        values: Properties,
        key: String,
    ): Double = values.getProperty(key).toDouble()

    private fun vector(
        values: Properties,
        key: String,
    ): Vec2 = Vec2(number(values, "$key.x"), number(values, "$key.y"))

    private fun fixture(discId: String = "disc-1"): BoardState =
        BoardState(
            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
            listOf(Pocket("pocket-tl", Circle(Vec2(0.0, 0.0), 5.0))),
            listOf(Disc(discId, DiscKind.WHITE, Circle(Vec2(50.0, 50.0), 5.0))),
            Striker("striker-1", Circle(Vec2(50.0, 90.0), 10.0)),
        )
}

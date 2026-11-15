package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.Disc
import io.github.carrombot.core.model.DiscKind
import io.github.carrombot.core.model.Pocket
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2

/** Synthetic v1 inputs in mm and seconds, shared by regression tests and the external timer. */
internal data class ShotFixture(
    val name: String,
    val board: BoardState,
    val velocity: Vec2,
    val config: PhysicsConfig,
    val fields: List<String>,
) {
    fun simulate(settings: PhysicsConfig = config): SimulationResult =
        ShotSimulator().simulate(
            board,
            ShotInput(board, velocity),
            settings,
        )

    override fun toString(): String = name

    companion object {
        const val TOLERANCE = 1e-7

        fun load(): List<ShotFixture> =
            resourceLines("/shot-v1.csv")
                .map { row ->
                    val f = row.split(';')
                    require(f.size == 11) { "Invalid v1 fixture: $row" }
                    val launch = f[1].split(',').map(String::toDouble)
                    val discs = f[2].split('/').map(::parseDisc)
                    val pockets = f[3].split('/').map(::parsePocket)
                    ShotFixture(
                        f[0],
                        BoardState(
                            BoardBounds(Vec2(0.0, 0.0), Vec2(100.0, 100.0)),
                            pockets,
                            discs,
                            Striker("s", Circle(Vec2(launch[0], launch[1]), 5.0)),
                        ),
                        Vec2(launch[2], launch[3]),
                        PhysicsConfig(2.0, 1, wallTangentialRestitution = f[8].toDouble()),
                        f,
                    )
                }.also { fixtures ->
                    require(fixtures.isNotEmpty() && fixtures.map { it.name }.distinct().size == fixtures.size)
                }

        private fun parseDisc(value: String): Disc {
            val f = value.split(',')
            return Disc(
                f[0],
                DiscKind.BLACK,
                Circle(Vec2(f[1].toDouble(), f[2].toDouble()), 5.0),
                Vec2(f[3].toDouble(), f[4].toDouble()),
            )
        }

        private fun parsePocket(value: String): Pocket {
            val f = value.split(',')
            return Pocket(f[0], Circle(Vec2(f[1].toDouble(), f[2].toDouble()), 20.0))
        }
    }
}

internal fun resourceLines(path: String): List<String> =
    requireNotNull(ShotFixture::class.java.getResourceAsStream(path)) { "Missing fixture $path" }
        .bufferedReader()
        .use { it.readLines() }
        .filterNot { it.isBlank() || it.startsWith('#') }

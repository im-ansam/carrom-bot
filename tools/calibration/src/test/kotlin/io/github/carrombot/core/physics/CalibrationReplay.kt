package io.github.carrombot.core.physics

import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.BoardState
import io.github.carrombot.core.model.Circle
import io.github.carrombot.core.model.ShotInput
import io.github.carrombot.core.model.Striker
import io.github.carrombot.core.model.Vec2
import java.io.File
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

/** Offline test-tool entry point. The explicit 1000 reference units/BW are arbitrary, never measured mm. */
internal object CalibrationReplay {
    private const val REFERENCE_LENGTH = 1000.0
    private const val STEP_SECONDS = 0.001

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "One sanitized replay CSV required" }
        val rows =
            File(args.single())
                .readLines()
                .filterNot { it.startsWith("#") || it.isBlank() }
                .map { it.split(',') }
        require(rows.isNotEmpty() && rows.all { it.size == 14 }) { "Invalid replay-v1 CSV" }
        val grouped = rows.groupBy { it[0] }
        println("id,n,kotlin_rmse_bw,python_kotlin_max_difference_bw,bounces,rest_seconds")
        for ((id, shotRows) in grouped) {
            replayShot(id, shotRows)
        }
    }

    private fun replayShot(
        id: String,
        shotRows: List<List<String>>,
    ) {
        val numbers = shotRows.map { row -> row.drop(1).map(String::toDouble) }
        require(numbers.flatten().all(Double::isFinite)) { "Nonfinite replay data" }
        val first = numbers.first()
        require(numbers.zipWithNext().all { (a, b) -> a[0] < b[0] }) { "Unordered times" }
        require(first[0] >= 0.0) { "Negative elapsed time" }
        require(numbers.all { it.subList(1, 9) == first.subList(1, 9) }) { "Changed shot parameters" }
        val board =
            BoardState(
                BoardBounds(Vec2(0.0, 0.0), Vec2(REFERENCE_LENGTH, REFERENCE_LENGTH)),
                emptyList(),
                emptyList(),
                Striker(
                    "s",
                    Circle(
                        Vec2(first[1] * REFERENCE_LENGTH, first[2] * REFERENCE_LENGTH),
                        23.0 / 680.0 * REFERENCE_LENGTH,
                    ),
                ),
            )
        val speed = first[5] * first[7] * REFERENCE_LENGTH
        val result =
            ShotSimulator().simulate(
                board,
                ShotInput(board, Vec2(first[3] * speed, first[4] * speed)),
                PhysicsConfig(
                    timeStepSeconds = STEP_SECONDS,
                    maxSteps = 30_000,
                    deceleration = first[6] * REFERENCE_LENGTH,
                    wallRestitution = first[8],
                ),
            )
        val frames = result.prediction.frames
        var sumSquared = 0.0
        var maxDifference = 0.0
        for (row in numbers) {
            require(row[0] <= 30.0) { "Replay exceeds bounded horizon" }
            val upper = ceil(row[0] / STEP_SECONDS).toInt().coerceAtMost(frames.lastIndex)
            val lower = (upper - 1).coerceAtLeast(0)
            val a = frames[lower]
            val b = frames[upper]
            val fraction = if (a == b) 0.0 else ((row[0] - a.elapsedSeconds) / STEP_SECONDS).coerceIn(0.0, 1.0)
            val pa = a.boardState.striker.circle.center
            val pb = b.boardState.striker.circle.center
            val x = (pa.x + (pb.x - pa.x) * fraction) / REFERENCE_LENGTH
            val y = (pa.y + (pb.y - pa.y) * fraction) / REFERENCE_LENGTH
            sumSquared += hypot(x - row[9], y - row[10]).let { it * it }
            maxDifference = maxOf(maxDifference, hypot(x - row[11], y - row[12]))
        }
        // Interpolation across a contact is bounded by the 1ms sampling interval.
        check(maxDifference <= 0.002) { "Offline/production disagreement: $id $maxDifference" }
        println(
            "$id,${numbers.size},${sqrt(
                sumSquared / numbers.size,
            )},$maxDifference,${result.events.size},${frames.last().elapsedSeconds}",
        )
    }
}

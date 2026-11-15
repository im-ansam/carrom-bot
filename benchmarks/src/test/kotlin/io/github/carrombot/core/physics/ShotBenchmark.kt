package io.github.carrombot.core.physics

import java.util.Locale

/** External monotonic timer. Fixture loading and warmup are excluded from measured candidate batches. */
internal object ShotBenchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Expected warmup rounds and measured rounds" }
        val warmup = args[0].toInt().also { require(it in 1..100_000) }
        val rounds = args[1].toInt().also { require(it in 1..100_000) }
        val fixtures = ShotFixture.load()
        var warmupChecksum = 0.0
        repeat(warmup) { fixtures.forEach { fixture -> warmupChecksum += evaluate(fixture) } }
        println("synthetic-shot-v1;java=${System.getProperty("java.version")};warmupRounds=$warmup;rounds=$rounds")
        println("warmupChecksum=$warmupChecksum;measuredShots=${rounds * fixtures.size}")
        println("family;shots;elapsedMs;nsPerShot;events;frames;checksum")
        fixtures.forEach { fixture -> measure(fixture, rounds) }
    }

    private fun evaluate(fixture: ShotFixture): Double {
        val result = fixture.simulate()
        return result.finalState.striker.circle.center.x + result.events.size + result.prediction.frames.size
    }

    private fun measure(
        fixture: ShotFixture,
        rounds: Int,
    ) {
        var checksum = 0.0
        var events = 0L
        var frames = 0L
        val start = System.nanoTime()
        repeat(rounds) {
            val result = fixture.simulate()
            checksum += result.finalState.striker.circle.center.x
            events += result.events.size
            frames += result.prediction.frames.size
        }
        val elapsed = System.nanoTime() - start
        println(
            String.format(
                Locale.ROOT,
                "%s;%d;%.3f;%.1f;%d;%d;%.6f",
                fixture.name,
                rounds,
                elapsed / 1e6,
                elapsed.toDouble() / rounds,
                events,
                frames,
                checksum,
            ),
        )
    }
}

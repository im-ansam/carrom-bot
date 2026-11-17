package io.github.carrombot.core.physics

import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

/** Exercises the offline CSV/unit adapter using original independent analytic values. */
internal class CalibrationReplayTest {
    @Test
    fun `arbitrary reference conversion replays analytic launch bounce and stop`() {
        val content =
            requireNotNull(javaClass.getResourceAsStream("/replay-v1.csv"))
                .bufferedReader()
                .use { it.readText() }
        withInput(content) { CalibrationReplay.main(arrayOf(it)) }
    }

    @Test
    fun `nonfinite and inconsistent input fail explicitly`() {
        val content =
            requireNotNull(javaClass.getResourceAsStream("/replay-v1.csv"))
                .bufferedReader()
                .use { it.readText() }
        for (bad in listOf(content.replace("0.25,0.5", "NaN,0.5"), content.replace("4,0.5,0.4", "4,0.6,0.4"))) {
            withInput(bad) { input ->
                assertThrows(IllegalArgumentException::class.java) { CalibrationReplay.main(arrayOf(input)) }
            }
        }
    }

    @Test
    fun `analytic replay discrepancy fails rather than fabricates agreement`() {
        val content =
            requireNotNull(javaClass.getResourceAsStream("/replay-v1.csv"))
                .bufferedReader()
                .use { it.readText() }
        withInput(content.replace("0.8327205882352941", "0.9")) { input ->
            assertThrows(IllegalStateException::class.java) { CalibrationReplay.main(arrayOf(input)) }
        }
    }

    private fun withInput(
        content: String,
        action: (String) -> Unit,
    ) {
        val path = Files.createTempFile("calibration-replay-", ".csv")
        try {
            Files.writeString(path, content)
            action(path.toString())
        } finally {
            Files.delete(path)
        }
    }
}

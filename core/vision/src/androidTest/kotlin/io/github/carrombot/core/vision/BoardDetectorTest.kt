package io.github.carrombot.core.vision

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

class BoardDetectorTest {
    private val bounds = BoardBounds(Vec2(0.0, 0.0), Vec2(740.0, 740.0))
    private val detector = BoardDetector.create(bounds)

    @Test
    fun goldenScreenshotsNormalizeRotatedScaledPerspectiveAndNoisyBoards() {
        val cases =
            mapOf(
                "square" to listOf(Vec2(100.0, 150.0), Vec2(500.0, 150.0), Vec2(500.0, 550.0), Vec2(100.0, 550.0)),
                "rotated" to listOf(Vec2(170.0, 100.0), Vec2(550.0, 220.0), Vec2(430.0, 600.0), Vec2(50.0, 480.0)),
                "scaled" to listOf(Vec2(180.0, 230.0), Vec2(420.0, 230.0), Vec2(420.0, 470.0), Vec2(180.0, 470.0)),
                "perspective" to listOf(Vec2(130.0, 140.0), Vec2(490.0, 170.0), Vec2(520.0, 550.0), Vec2(80.0, 530.0)),
                "noisy" to listOf(Vec2(100.0, 150.0), Vec2(500.0, 150.0), Vec2(500.0, 550.0), Vec2(100.0, 550.0)),
            )
        cases.forEach { (name, expected) ->
            withFixture(name) { frame ->
                val before = Core.sumElems(frame)
                val geometry = found(frame)
                assertTrue("$name confidence ${geometry.confidence}", geometry.confidence in 0.5..1.0)
                expected.zip(geometry.corners).forEach { (p, q) ->
                    assertEquals("$name x", p.x, q.x, 3.0)
                    assertEquals("$name y", p.y, q.y, 3.0)
                }
                val center = Vec2(370.0, 370.0)
                val roundTrip = geometry.transform.toBoard(geometry.transform.toScreen(center))
                assertEquals(center.x, roundTrip.x, 1e-7)
                assertEquals(center.y, roundTrip.y, 1e-7)
                val normalized = detector.normalize(frame, geometry, 256)
                try {
                    assertEquals(256, normalized.rows())
                    assertEquals(256, normalized.cols())
                    assertEquals(CvType.CV_8UC4, normalized.type())
                    // Synthetic center marker is blue: verifies image warp and corner order.
                    val pixel = normalized.get(128, 128)
                    assertTrue("$name center marker", pixel[2] > pixel[0] + 80)
                } finally {
                    normalized.release()
                }
                assertTrue(before.`val`.contentEquals(Core.sumElems(frame).`val`))
                assertEquals(geometry.corners, found(frame).corners)
            }
        }
    }

    @Test
    fun diamondBoundaryDoesNotBecomeAmbiguousFromCannyCornerTie() {
        withFixture("diamond") { frame -> found(frame) }
        withFixture("diamond-noisy") { frame -> found(frame) }
    }

    @Test
    fun smallCornerJitterDoesNotRotateNormalizedDiamondByNinetyDegrees() {
        lateinit var reference: BoardDetection.Found
        withFixture("diamond") { reference = found(it) }
        withFixture("diamond-jitter") { frame ->
            val geometry = found(frame)
            reference.corners.zip(geometry.corners).forEach { (expected, actual) ->
                assertEquals(expected.x, actual.x, 4.0)
                assertEquals(expected.y, actual.y, 4.0)
            }
        }
    }

    @Test
    fun missingClippedThinAndMultipleBoardsFailExplicitly() {
        mapOf(
            "blank" to BoardDetectionFailure.BOARD_NOT_FOUND,
            "clipped" to BoardDetectionFailure.BOARD_NOT_FOUND,
            "thin" to BoardDetectionFailure.BOARD_NOT_FOUND,
            "multiple" to BoardDetectionFailure.AMBIGUOUS_BOARD,
        ).forEach { (name, reason) ->
            withFixture(name) { assertEquals(name, BoardDetection.Failed(reason), detector.detect(it)) }
        }
    }

    @Test
    fun invalidFramesAreRejectedAndNormalizationValidatesSize() {
        val empty = Mat()
        val gray = Mat(50, 50, CvType.CV_8UC1, Scalar(0.0))
        try {
            listOf(empty, gray).forEach {
                assertEquals(BoardDetection.Failed(BoardDetectionFailure.INVALID_FRAME), detector.detect(it))
            }
            withFixture("square") {
                val geometry = found(it)
                assertThrows(IllegalArgumentException::class.java) { detector.normalize(it, geometry, 0) }
            }
        } finally {
            empty.release()
            gray.release()
        }
    }

    @Test
    fun repeatedCallsOwnResultsAfterFrameIsReleased() {
        lateinit var geometry: BoardDetection.Found
        withFixture("square") { frame ->
            repeat(5) { found(frame) }
            val elapsed = LongArray(100)
            repeat(elapsed.size) { index ->
                val start = System.nanoTime()
                geometry = found(frame)
                elapsed[index] = System.nanoTime() - start
            }
            elapsed.sort()
            Log.i("CB21Vision", "600x700 synthetic n=100 medianMs=${elapsed[50] / 1e6} p95Ms=${elapsed[94] / 1e6}")
        }
        assertEquals(0.0, geometry.transform.toBoard(geometry.corners.first()).x, 1e-7)
        assertThrows(UnsupportedOperationException::class.java) { (geometry.corners as MutableList).clear() }
    }

    private fun found(frame: Mat): BoardDetection.Found {
        val result = detector.detect(frame)
        assertTrue("Expected board, got $result", result is BoardDetection.Found)
        return result as BoardDetection.Found
    }

    private fun withFixture(
        name: String,
        action: (Mat) -> Unit,
    ) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open("board-v1/$name.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        val frame = Mat()
        try {
            Utils.bitmapToMat(bitmap, frame)
            action(frame)
        } finally {
            frame.release()
            bitmap.recycle()
        }
    }
}

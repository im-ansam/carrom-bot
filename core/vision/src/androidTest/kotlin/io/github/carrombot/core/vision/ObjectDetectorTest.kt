package io.github.carrombot.core.vision

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

class ObjectDetectorTest {
    private val bounds = BoardBounds(Vec2(10.0, 20.0), Vec2(410.0, 420.0))
    private val red = ObjectProfile(ObjectKind.QUEEN, HsvRange(0, 10, 180, 255, 180, 255), 0.03, 0.08)
    private val detector = ObjectDetector.create(listOf(red))

    @Test
    fun completeCircleHasBoardGeometryAndOwnedConfidence() {
        val frame = frame()
        try {
            Imgproc.circle(frame, Point(200.0, 180.0), 20, Scalar(255.0, 0.0, 0.0, 255.0), -1)
            val result = detector.detect(frame, bounds) as ObjectDetection.Observed
            val disc = result.objects.single()
            assertEquals(ObjectKind.QUEEN, disc.kind)
            assertEquals(210.0, disc.circle.center.x, 0.1)
            assertEquals(200.0, disc.circle.center.y, 0.1)
            assertEquals(20.0, disc.circle.radius, 0.2)
            assertTrue(disc.confidence in 0.8..1.0)
        } finally {
            frame.release()
        }
    }

    @Test
    fun overlappingSameColorDiscsAndClippedCircleAreNotInvented() {
        val frame = frame()
        try {
            listOf(Point(100.0, 100.0), Point(125.0, 100.0), Point(0.0, 250.0)).forEach {
                Imgproc.circle(frame, it, 20, Scalar(255.0, 0.0, 0.0, 255.0), -1)
            }
            assertTrue((detector.detect(frame, bounds) as ObjectDetection.Observed).objects.isEmpty())
        } finally {
            frame.release()
        }
    }

    @Test
    fun ambiguousProfilesDoNotAssignAClass() {
        val ambiguous = ObjectDetector.create(listOf(red, red.copy(kind = ObjectKind.STRIKER)))
        val frame = frame()
        try {
            Imgproc.circle(frame, Point(200.0, 180.0), 20, Scalar(255.0, 0.0, 0.0, 255.0), -1)
            assertTrue((ambiguous.detect(frame, bounds) as ObjectDetection.Observed).objects.isEmpty())
        } finally {
            frame.release()
        }
    }

    @Test
    fun annotatedScreenshotsMeasurePrecisionRecallAcrossClassesAndRejections() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val annotations = JSONArray(assets.open("objects-v1/annotations.json").bufferedReader().use { it.readText() })
        val calibrated = ObjectDetector.create(fixtureProfiles())
        var truePositives = 0
        var falsePositives = 0
        var falseNegatives = 0
        repeat(annotations.length()) { index ->
            val fixture = annotations.getJSONObject(index)
            val name = fixture.getString("name")
            val bitmap = assets.open("objects-v1/$name.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
            val frame = Mat()
            try {
                Utils.bitmapToMat(bitmap, frame)
                val before = frame.clone()
                try {
                    val result = calibrated.detect(frame, bounds) as ObjectDetection.Observed
                    assertEquals("Input mutated", 0.0, Core.norm(before, frame, Core.NORM_INF), 0.0)
                    val unmatched = result.objects.toMutableList()
                    val expected = fixture.getJSONArray("objects")
                    repeat(expected.length()) { n ->
                        val item = expected.getJSONObject(n)
                        val match =
                            unmatched.firstOrNull {
                                it.kind.name == item.getString("kind") &&
                                    kotlin.math.hypot(
                                        it.circle.center.x - 10 - item.getDouble("x"),
                                        it.circle.center.y - 20 - item.getDouble("y"),
                                    ) < 2.0 &&
                                    kotlin.math.abs(it.circle.radius - item.getDouble("radius")) < 2.0
                            }
                        if (match == null) {
                            falseNegatives++
                        } else {
                            unmatched.remove(match)
                            truePositives++
                        }
                    }
                    falsePositives += unmatched.size
                    assertEquals("$name unmatched detections: $unmatched", 0, unmatched.size)
                    assertEquals("$name count", expected.length(), result.objects.size)
                    assertThrows(UnsupportedOperationException::class.java) { (result.objects as MutableList).clear() }
                } finally {
                    before.release()
                }
            } finally {
                bitmap.recycle()
                frame.release()
            }
        }
        Log.i("CB22Vision", "objects-v1 TP=$truePositives FP=$falsePositives FN=$falseNegatives")
        assertEquals(27, truePositives)
        assertEquals(0, falsePositives)
        assertEquals(0, falseNegatives)
    }

    @Test
    fun invalidFramesAndIncompatiblePhysicalGeometryFailExplicitly() {
        val empty = Mat()
        val gray = Mat(10, 10, CvType.CV_8UC1)
        val frame = frame()
        try {
            listOf(empty, gray).forEach {
                assertEquals(ObjectDetection.Failed(ObjectDetectionFailure.INVALID_FRAME), detector.detect(it, bounds))
            }
            val rectangle = BoardBounds(Vec2(0.0, 0.0), Vec2(400.0, 200.0))
            assertEquals(
                ObjectDetection.Failed(ObjectDetectionFailure.INCOMPATIBLE_GEOMETRY),
                detector.detect(frame, rectangle),
            )
        } finally {
            empty.release()
            gray.release()
            frame.release()
        }
    }

    private fun fixtureProfiles(): List<ObjectProfile> =
        listOf(
            ObjectProfile(ObjectKind.POCKET, HsvRange(0, 179, 0, 255, 0, 30), 0.025, 0.045),
            ObjectProfile(ObjectKind.BLACK_DISC, HsvRange(0, 179, 0, 80, 40, 80), 0.035, 0.055),
            ObjectProfile(ObjectKind.WHITE_DISC, HsvRange(0, 179, 0, 50, 220, 255), 0.035, 0.055),
            red.copy(
                color = HsvRange(170, 10, 180, 255, 180, 255),
                minRadiusFraction = 0.035,
                maxRadiusFraction = 0.055,
            ),
            ObjectProfile(ObjectKind.STRIKER, HsvRange(105, 125, 180, 255, 180, 255), 0.055, 0.075),
            ObjectProfile(ObjectKind.OBSTACLE, HsvRange(45, 75, 180, 255, 180, 255), 0.035, 0.065, 0.60, 0.60),
        )

    @Test
    fun roundingOfRectifiedDimensionsPreservesBothPhysicalAxes() {
        val frame = Mat(403, 401, CvType.CV_8UC4, Scalar(180.0, 160.0, 120.0, 255.0))
        try {
            Imgproc.circle(frame, Point(200.0, 201.0), 20, Scalar(255.0, 0.0, 0.0, 255.0), -1)
            val disc = (detector.detect(frame, bounds) as ObjectDetection.Observed).objects.single()
            assertEquals(210.0, disc.circle.center.x, 0.1)
            assertEquals(220.0, disc.circle.center.y, 0.1)
            assertEquals(20.0, disc.circle.radius, 0.2)
        } finally {
            frame.release()
        }
    }

    private fun frame(): Mat = Mat(401, 401, CvType.CV_8UC4, Scalar(180.0, 160.0, 120.0, 255.0))
}

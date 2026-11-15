package io.github.carrombot.core.overlay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.CoordinateTransform
import io.github.carrombot.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic Canvas fixtures only; goldens are reviewed API31 software-rendered bitmaps, never captured screen data. */
@RunWith(AndroidJUnit4::class)
class TrajectoryRendererTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bounds = BoardBounds(Vec2(10.0, 20.0), Vec2(310.0, 220.0))

    @Test
    fun multipleReboundsCombinationChainAndAlternativeMatchGoldens() {
        assertGolden("chain-bank-v1.png", render(1.5, Vec2(80.0, 100.0)))
        assertGolden("translated-bank-v1.png", render(1.0, Vec2(180.0, 160.0)))
    }

    @Test
    fun emptySceneDrawsNothingAndCanvasStateIsRestored() {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val count = canvas.saveCount
            TrajectoryRenderer().draw(canvas, OverlayScene(bounds, emptyList(), null), mapping(1.0, Vec2(0.0, 0.0)))
            assertEquals(count, canvas.saveCount)
            val pixels = IntArray(640 * 480)
            bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 480)
            assertTrue(pixels.all { it == Color.TRANSPARENT })
            assertThrows(IllegalArgumentException::class.java) {
                TrajectoryRenderer().draw(canvas, scene(), mapping(Double.MAX_VALUE, Vec2(0.0, 0.0)))
            }
            assertEquals(count, canvas.saveCount)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun passiveViewClearsOnResizeAndDoesNotConsumeTouches() {
        instrumentation.runOnMainSync {
            val view = TrajectoryView(instrumentation.context)
            view.layout(0, 0, 640, 480)
            assertFalse(view.isClickable)
            view.submit(scene(), mapping(1.5, Vec2(80.0, 100.0)))
            val before = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            val after = Bitmap.createBitmap(641, 480, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(before))
                assertTrue(before.getPixel(245, 280) != Color.TRANSPARENT)
                view.layout(0, 0, 641, 480)
                view.draw(Canvas(after))
                assertEquals(Color.TRANSPARENT, after.getPixel(245, 280))
                view.clear()
            } finally {
                before.recycle()
                after.recycle()
            }
        }
    }

    private fun mapping(
        scale: Double,
        origin: Vec2,
    ) = BoardMapping(bounds, CoordinateTransform(bounds.min, origin, scale))

    private fun render(
        scale: Double,
        origin: Vec2,
    ): Bitmap =
        Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.DKGRAY)
            TrajectoryRenderer().draw(canvas, scene(), mapping(scale, origin))
        }

    private fun scene(): OverlayScene {
        val striker =
            BodyTrajectory(
                "striker",
                true,
                listOf(Vec2(30.0, 180.0), Vec2(160.0, 30.0), Vec2(300.0, 140.0), Vec2(120.0, 140.0)),
            )
        val first = BodyTrajectory("disc-1", false, listOf(Vec2(120.0, 140.0), Vec2(80.0, 100.0)))
        val second = BodyTrajectory("disc-2", false, listOf(Vec2(80.0, 100.0), Vec2(30.0, 40.0)))
        val markers =
            listOf(
                ShotAnnotation(AnnotationKind.REBOUND, Vec2(160.0, 30.0)),
                ShotAnnotation(AnnotationKind.REBOUND, Vec2(300.0, 140.0)),
                ShotAnnotation(AnnotationKind.CONTACT, Vec2(120.0, 140.0)),
                ShotAnnotation(AnnotationKind.CONTACT, Vec2(80.0, 100.0)),
                ShotAnnotation(AnnotationKind.POCKET, Vec2(30.0, 40.0)),
            )
        val selected = RenderedCandidate("A", listOf(striker, first, second), markers, 0.65, 0.8)
        val alternative =
            RenderedCandidate(
                "B",
                listOf(BodyTrajectory("striker", true, listOf(Vec2(30.0, 180.0), Vec2(250.0, 50.0)))),
                emptyList(),
                0.4,
                null,
            )
        return OverlayScene(bounds, listOf(selected, alternative), "A")
    }

    private fun assertGolden(
        name: String,
        actual: Bitmap,
    ) {
        try {
            if (InstrumentationRegistry.getArguments().getString("recordGoldens") == "true") {
                recordGolden(name, actual)
            } else {
                compareGolden(name, actual)
            }
        } finally {
            actual.recycle()
        }
    }

    private fun recordGolden(
        name: String,
        actual: Bitmap,
    ) {
        val directory = File(instrumentation.context.getExternalFilesDir(null), "goldens")
        check(directory.mkdirs() || directory.isDirectory)
        val output = File(directory, name)
        output.outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Gradle uninstalls the test APK after the run; retain synthetic fixtures for explicit adb pull.
        val command = "cp ${output.absolutePath} /data/local/tmp/carrombot-$name"
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(descriptor)
                .use { it.readBytes() }
        }
    }

    private fun compareGolden(
        name: String,
        actual: Bitmap,
    ) {
        val expected =
            instrumentation.context.assets
                .open("goldens/$name")
                .use { BitmapFactory.decodeStream(it) }
        try {
            assertEquals(expected.width, actual.width)
            assertEquals(expected.height, actual.height)
            assertTrue("Golden mismatch: $name", actual.sameAs(expected))
        } finally {
            expected.recycle()
        }
    }
}

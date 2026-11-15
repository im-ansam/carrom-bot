package io.github.carrombot.core.vision

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.github.carrombot.core.model.BoardBounds
import io.github.carrombot.core.model.Vec2
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.security.MessageDigest

/** Exports actual native detector results; offline scoring never substitutes annotations. */
class VisionEvaluationTest {
    @Test
    fun exportVersionedPredictionsForOfflineEvaluation() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val manifest =
            JSONObject(
                context.assets
                    .open("vision-v1.json")
                    .bufferedReader()
                    .use { it.readText() },
            )
        val frames = manifest.getJSONArray("frames")
        val results = JSONArray()
        val objects = ObjectDetector.create(profiles())
        repeat(frames.length()) { index ->
            val fixture = frames.getJSONObject(index)
            val bytes = context.assets.open(fixture.getString("asset")).use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(fixture.getString("sha256"), digest)
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            val mat = Mat()
            try {
                Utils.bitmapToMat(bitmap, mat)
                val result =
                    if (fixture.getString("mode") == "board") {
                        val bounds =
                            BoardBounds(
                                Vec2(0.0, 0.0),
                                Vec2(fixture.getDouble("width_mm"), fixture.getDouble("height_mm")),
                            )
                        boardResult(BoardDetector.create(bounds).detect(mat))
                    } else {
                        val b = fixture.getJSONArray("bounds_mm")
                        val bounds =
                            BoardBounds(
                                Vec2(b.getDouble(0), b.getDouble(1)),
                                Vec2(b.getDouble(2), b.getDouble(3)),
                            )
                        objectResult(
                            objects.detect(mat, bounds),
                        )
                    }
                results.put(result.put("id", fixture.getString("id")).put("sha256", digest))
            } finally {
                mat.release()
                bitmap.recycle()
            }
        }
        val output = JSONObject().put("version", 1).put("dataset", manifest.getString("dataset")).put("frames", results)
        File(context.filesDir, "vision-predictions.json").writeText(output.toString(2))
        assertEquals(18, results.length())
    }

    private fun boardResult(result: BoardDetection): JSONObject =
        when (result) {
            is BoardDetection.Failed -> {
                JSONObject().put("status", "failed").put("reason", result.reason.name)
            }

            is BoardDetection.Found -> {
                JSONObject().put("status", "found").put(
                    "corners",
                    JSONArray(result.corners.map { JSONArray(listOf(it.x, it.y)) }),
                )
            }
        }

    private fun objectResult(result: ObjectDetection): JSONObject =
        when (result) {
            is ObjectDetection.Failed -> {
                JSONObject().put("status", "failed").put("reason", result.reason.name)
            }

            is ObjectDetection.Observed -> {
                JSONObject().put("status", "observed").put(
                    "objects",
                    JSONArray(
                        result.objects.map {
                            JSONObject()
                                .put("kind", it.kind.name)
                                .put("x", it.circle.center.x)
                                .put("y", it.circle.center.y)
                                .put("radius", it.circle.radius)
                        },
                    ),
                )
            }
        }

    private fun profiles(): List<ObjectProfile> =
        listOf(
            ObjectProfile(ObjectKind.POCKET, HsvRange(0, 179, 0, 255, 0, 30), 0.025, 0.045),
            ObjectProfile(ObjectKind.BLACK_DISC, HsvRange(0, 179, 0, 80, 40, 80), 0.035, 0.055),
            ObjectProfile(ObjectKind.WHITE_DISC, HsvRange(0, 179, 0, 50, 220, 255), 0.035, 0.055),
            ObjectProfile(ObjectKind.QUEEN, HsvRange(170, 10, 180, 255, 180, 255), 0.035, 0.055),
            ObjectProfile(ObjectKind.STRIKER, HsvRange(105, 125, 180, 255, 180, 255), 0.055, 0.075),
            ObjectProfile(ObjectKind.OBSTACLE, HsvRange(45, 75, 180, 255, 180, 255), 0.035, 0.065, 0.60, 0.60),
        )
}

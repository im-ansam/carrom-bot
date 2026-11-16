package io.github.carrombot

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class SyntheticPipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation

    @Test
    fun syntheticConsentDenialCanRetryWithoutStartingCapture() {
        val context = instrumentation.targetContext
        val original = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val mode =
            Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
                .find(original)
                ?.groupValues
                ?.get(1) ?: "default"
        val component =
            context
                .getSystemService(android.media.projection.MediaProjectionManager::class.java)
                .createScreenCaptureIntent()
                .component
        val requests = AtomicInteger()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                    if (intent.component == component) {
                        requests.incrementAndGet()
                        Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                    } else {
                        null
                    }
            }
        instrumentation.addMonitor(monitor)
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        try {
            ActivityScenario.launch(SyntheticDemoActivity::class.java).use { scenario ->
                await("Synthetic source has focus") {
                    var focused = false
                    scenario.onActivity { focused = it.hasWindowFocus() }
                    focused
                }
                repeat(2) {
                    scenario.onActivity { activity ->
                        val label = context.getString(R.string.synthetic_start)
                        buttons(activity.window.decorView).single { it.text == label }.performClick()
                    }
                    instrumentation.waitForIdleSync()
                }
                org.junit.Assert.assertEquals(2, requests.get())
                assertFalse(shell("dumpsys activity services ${context.packageName}").contains("CaptureService"))
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
        }
    }

    @Test
    fun generatedFixtureRunsNativeVisionTrackingPlannerAndSceneAdapter() =
        runBlocking {
            val frame = fixtureFrame()
            val vision = SyntheticVision(DemoViewport(0, 0, 500, 500, 500))
            var ready = AimingObservation()
            val start = SystemClock.elapsedRealtimeNanos()
            repeat(5) { index ->
                val timestamp = 100_000_000L * (index + 1)
                ready = vision.observe(RgbaFrame.copy(500, 500, timestamp, ByteBuffer.wrap(frame), 2000, 4), timestamp)
            }
            assertNotNull("Fixture readiness: ${ready.reason}", ready.board)
            val scene = SyntheticProfile.plan(checkNotNull(ready.board))
            assertFalse("Actual evaluated fixture must recommend a shot", scene.candidates.isEmpty())
            val elapsed = SystemClock.elapsedRealtimeNanos() - start
            Log.i(
                "CB35Profile",
                "fixture-v1 five observations plus plan nanos=$elapsed " +
                    "candidates=${scene.candidates.size}",
            )
            val missingMarker = frame.copyOf().apply { fill(0, 0, 500 * 30 * 4) }
            assertNull(
                vision
                    .observe(
                        RgbaFrame.copy(
                            500,
                            500,
                            600_000_000,
                            ByteBuffer.wrap(missingMarker),
                            2000,
                            4,
                        ),
                        600_000_000,
                    ).board,
            )
        }

    @Test
    fun consentedSyntheticCaptureShowsRealRecommendationAndRepeatedStartStopClearsWindows() {
        val context = instrumentation.targetContext
        val original = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val mode =
            Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
                .find(original)
                ?.groupValues
                ?.get(1) ?: "default"
        val info = automation.serviceInfo
        val flags = info.flags
        info.flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        try {
            ActivityScenario.launch(SyntheticDemoActivity::class.java).use { scenario ->
                await("Synthetic source Activity laid out with focus") {
                    var focused = false
                    scenario.onActivity { focused = it.hasWindowFocus() }
                    focused
                }
                scenario.onActivity { activity ->
                    val start = context.getString(R.string.synthetic_start)
                    buttons(activity.window.decorView).single { it.text == start }.performClick()
                }
                await("System consent") {
                    automation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByViewId("android:id/button1")
                        ?.any { it.isEnabled && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
                }
                click("Start aiming")
                await("Real capture-to-recommendation") { recommendationVisible() }
                assertTrue(shell("dumpsys window windows").contains("CarromBotSyntheticPreview"))
                click("Pause aiming")
                await("Pause clears preview") { !shell("dumpsys window windows").contains("CarromBotSyntheticPreview") }
                click("Resume aiming")
                await("Fresh recommendation after resume") { recommendationVisible() }
                click("Stop aiming")
                await("Stop clears preview") { !shell("dumpsys window windows").contains("CarromBotSyntheticPreview") }
                click("Start aiming")
                await("Fresh recommendation after restart") {
                    recommendationVisible()
                }
                click("End screen sharing")
                await("All resources released") {
                    !shell("dumpsys activity services ${context.packageName}").contains("CaptureService") &&
                        !shell("dumpsys window windows").contains("CarromBotSyntheticPreview")
                }
            }
        } finally {
            context.stopService(Intent(context, CaptureService::class.java))
            info.flags = flags
            automation.serviceInfo = info
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
        }
    }

    private fun fixtureFrame(): ByteArray {
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync {
            SyntheticBoardView(instrumentation.targetContext).apply {
                layout(0, 0, 500, 500)
                draw(Canvas(bitmap))
            }
        }
        // BoardDetector owns explicit OpenCV initialization, before Utils invokes native methods.
        io.github.carrombot.core.vision.BoardDetector
            .create(SyntheticProfile.bounds)
        val mat = Mat()
        try {
            Utils.bitmapToMat(bitmap, mat)
            return ByteArray(500 * 500 * 4).also { mat.get(0, 0, it) }
        } finally {
            mat.release()
            bitmap.recycle()
        }
    }

    private fun buttons(view: View): List<Button> =
        when (view) {
            is Button -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun text(value: String): Boolean =
        automation.windows.any {
            it.root?.findAccessibilityNodeInfosByText(value)?.isNotEmpty() == true
        }

    private fun recommendationVisible(): Boolean {
        val evaluatedPower = text("Recommended power:")
        return evaluatedPower && text("SYNTHETIC FIXTURE ONLY — no real-game calibration")
    }

    private fun click(value: String) {
        var node: AccessibilityNodeInfo? = null
        await(value) {
            node =
                automation.windows
                    .flatMap { it.root?.findAccessibilityNodeInfosByText(value).orEmpty() }
                    .firstOrNull { it.className == Button::class.java.name && it.isEnabled }
            node != null
        }
        checkNotNull(node).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        assertTrue(checkNotNull(node).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun await(
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue(description, condition())
    }

    private fun shell(command: String): String =
        automation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(it)
                .bufferedReader()
                .use { reader -> reader.readText() }
        }
}

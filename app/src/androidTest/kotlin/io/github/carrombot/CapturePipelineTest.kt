package io.github.carrombot

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real system consent on a disposable emulator; never stores or logs screen contents. */
@RunWith(AndroidJUnit4::class)
class CapturePipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation

    @Test
    fun explicitConsentDeliversFramesAcrossBackgroundAndRecreationUntilStop() {
        val context = instrumentation.targetContext
        val original = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        val mode =
            Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
                .find(original)
                ?.groupValues
                ?.get(1) ?: "default"
        val info = automation.serviceInfo
        val originalFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        try {
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_0))
            await {
                context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                startCapture(scenario)
                // More frames than ImageReader's capacity proves earlier native slots are released.
                await { frameCount(status(scenario)) >= 6 }
                // Activity recreation and backgrounding must not consume a second consent token.
                scenario.recreate()
                await { status(scenario).startsWith("Capturing authorized screen frames") }
                val portrait = dimensions(status(scenario))
                assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
                await("Rotation from $portrait") { dimensions(status(scenario)) == portrait.second to portrait.first }
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                assertTrue(shell("dumpsys activity services ${context.packageName}").contains("CaptureService"))
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                scenario.onActivity { it.findViewById<Button>(R.id.stop_button).performClick() }
                await { status(scenario) == context.getString(R.string.status_idle) }
                assertReleased(context)
                // Restart requires fresh real consent; the notification must also release capture.
                startCapture(scenario)
                val notification =
                    context
                        .getSystemService(NotificationManager::class.java)
                        .activeNotifications
                        .single { it.notification.actions?.isNotEmpty() == true }
                        .notification
                notification.actions
                    .first()
                    .actionIntent
                    .send()
                assertReleased(context)
            }
        } finally {
            context.stopService(Intent().setClassName(context, "io.github.carrombot.CaptureService"))
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            await {
                context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            }
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            info.flags = originalFlags
            automation.serviceInfo = info
            shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
        }
    }

    private fun startCapture(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.findViewById<Button>(R.id.start_button).performClick() }
        await { clickConsent() }
        await { status(scenario).startsWith("Capturing authorized screen frames") }
    }

    private fun assertReleased(context: Context) {
        await { !shell("dumpsys activity services ${context.packageName}").contains("CaptureService") }
        await { shell("dumpsys media_projection").contains("null") }
    }

    private fun dimensions(status: String): Pair<Int, Int> {
        val match = Regex("\\((\\d+) × (\\d+)\\)").find(status) ?: return 0 to 0
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    private fun frameCount(status: String): Long =
        Regex("frames: (\\d+)")
            .find(status)
            ?.groupValues
            ?.get(1)
            ?.toLong() ?: 0

    private fun clickConsent(): Boolean {
        val root = automation.rootInActiveWindow ?: return false
        val buttons = root.findAccessibilityNodeInfosByViewId("android:id/button1")
        return buttons.any { it.isEnabled && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
    }

    private fun status(scenario: ActivityScenario<MainActivity>): String {
        var result = ""
        scenario.onActivity { result = it.findViewById<TextView>(R.id.setup_status).text.toString() }
        return result
    }

    private fun await(
        description: String = "Capture lifecycle condition",
        condition: () -> Boolean,
    ) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("$description timed out", condition())
    }

    private fun shell(command: String): String =
        automation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(descriptor)
                .bufferedReader()
                .use { it.readText() }
        }
}

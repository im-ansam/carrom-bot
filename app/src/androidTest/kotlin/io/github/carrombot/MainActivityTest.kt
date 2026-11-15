package io.github.carrombot

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Run on a disposable emulator: temporarily changes only this test app's overlay app-op and restores it. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var originalOverlayMode = "default"
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()

    @Before
    fun rememberOverlayMode() {
        val output = shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        originalOverlayMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
            .find(output)
            ?.groupValues
            ?.get(1) ?: "default"
    }

    @After
    fun restorePermissionAndMonitors() {
        monitors.forEach(instrumentation::removeMonitor)
        setOverlayMode(originalOverlayMode)
    }

    @Test
    fun launchAndRecreationStayStoppedWithoutPermissionRequests() {
        setOverlayMode("allow")
        val requests = interceptPermissionScreens(Activity.RESULT_CANCELED)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertStatus(activity, R.string.status_idle)
                activity.findViewById<Button>(R.id.stop_button).performClick()
            }
            scenario.recreate()
            scenario.onActivity { assertStatus(it, R.string.status_idle) }
        }
        assertEquals(0, requests.get())
    }

    @Test
    fun overlayDenialAndRegrantDoNotSilentlyRequestCapture() {
        setOverlayMode("deny")
        val requests = interceptPermissionScreens(Activity.RESULT_CANCELED)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<Button>(R.id.start_button).performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertStatus(it, R.string.status_overlay_denied) }
            // Recreation must also discard all in-progress permission setup.
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.overlay_not_allowed), text(activity, R.id.overlay_status))
            }
            assertEquals(1, requests.get())
            setOverlayMode("allow")
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.overlay_allowed), text(activity, R.id.overlay_status))
                assertStatus(activity, R.string.status_idle)
            }
            assertEquals(1, requests.get())
        }
    }

    @Test
    fun captureDenialCanRetryAndInvalidGrantFailsWithoutFrames() {
        setOverlayMode("allow")
        val deniedRequests = interceptPermissionScreens(Activity.RESULT_CANCELED)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<Button>(R.id.start_button).performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertStatus(it, R.string.status_capture_denied) }
            assertEquals(1, deniedRequests.get())
            monitors.forEach(instrumentation::removeMonitor)
            monitors.clear()
            val grantedRequests = interceptPermissionScreens(Activity.RESULT_OK)
            scenario.onActivity { it.findViewById<Button>(R.id.start_button).performClick() }
            instrumentation.waitForIdleSync()
            awaitStatus(scenario, R.string.status_capture_failed)
            scenario.onActivity { activity ->
                assertStatus(activity, R.string.status_capture_failed)
                activity.findViewById<Button>(R.id.stop_button).performClick()
                assertStatus(activity, R.string.status_idle)
            }
            assertEquals(1, grantedRequests.get())
        }
    }

    private fun awaitStatus(
        scenario: ActivityScenario<MainActivity>,
        resource: Int,
    ) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        var observed = ""
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { observed = text(it, R.id.setup_status).toString() }
            if (observed == context.getString(resource)) return
            Thread.sleep(100)
        }
        assertEquals(context.getString(resource), observed)
    }

    @Test
    fun settingsHasAWorkingReturnControl() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.back_button)
                assertEquals(activity.getString(R.string.back_to_controls), button.text)
                activity.findViewById<Button>(R.id.back_button).performClick()
            }
        }
    }

    private fun interceptPermissionScreens(resultCode: Int): AtomicInteger {
        val requests = AtomicInteger()
        val projectionComponent =
            context
                .getSystemService(MediaProjectionManager::class.java)
                .createScreenCaptureIntent()
                .component
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                    if (intent.action == Settings.ACTION_MANAGE_OVERLAY_PERMISSION ||
                        intent.component == projectionComponent
                    ) {
                        requests.incrementAndGet()
                        Instrumentation.ActivityResult(resultCode, Intent())
                    } else {
                        null
                    }
            }
        monitors.add(monitor)
        instrumentation.addMonitor(monitor)
        return requests
    }

    private fun setOverlayMode(mode: String) {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(descriptor)
                .bufferedReader()
                .use { it.readText() }
        }

    private fun assertStatus(
        activity: Activity,
        resource: Int,
    ) {
        assertEquals(activity.getString(resource), text(activity, R.id.setup_status))
    }

    private fun text(
        activity: Activity,
        viewId: Int,
    ): CharSequence = activity.findViewById<TextView>(viewId).text
}

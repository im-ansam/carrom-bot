package io.github.carrombot.core.overlay

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.UiAutomation
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Test-only touch target outside the top-end overlay bounds. */
class TouchTestActivity : Activity() {
    var taps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            FrameLayout(this).apply {
                addView(
                    Button(this@TouchTestActivity).apply {
                        text = "Underlying touch target"
                        setOnClickListener { taps++ }
                    },
                    FrameLayout.LayoutParams(400, 160, Gravity.BOTTOM or Gravity.START),
                )
            },
        )
    }
}

/** Disposable emulator only: changes this APK's app-op, restores it after every test. */
@RunWith(AndroidJUnit4::class)
class OverlayControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private var originalMode = "default"
    private var controller: OverlayController? = null
    private var originalAccessibilityFlags = 0

    @Before
    fun rememberPermission() {
        val info = instrumentation.uiAutomation.serviceInfo
        originalAccessibilityFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
        originalMode = Regex("SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default|foreground)")
            .find(shell("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))
            ?.groupValues
            ?.get(1) ?: "default"
    }

    @After
    fun cleanup() {
        main { controller?.close() }
        permission(originalMode)
        val info = instrumentation.uiAutomation.serviceInfo
        info.flags = originalAccessibilityFlags
        instrumentation.uiAutomation.serviceInfo = info
    }

    @Test
    fun deniedPermissionCannotAttachAndRegrantRequiresExplicitShow() {
        permission("deny")
        main {
            val overlay = OverlayController(context)
            controller = overlay
            assertEquals(OverlayShowResult.PERMISSION_DENIED, overlay.show())
            assertFalse(overlay.isShowing)
        }
        permission("allow")
        main {
            val overlay = checkNotNull(controller)
            assertFalse(overlay.isShowing)
            assertEquals(OverlayShowResult.SHOWN, overlay.show())
        }
        instrumentation.waitForIdleSync()
        main {
            val overlay = checkNotNull(controller)
            assertTrue(overlay.isShowing)
            assertEquals(OverlayShowResult.SHOWN, overlay.show())
            overlay.hide()
            assertFalse(overlay.isShowing)
            overlay.hide()
            overlay.close()
            assertEquals(OverlayShowResult.CLOSED, overlay.show())
        }
    }

    @Test
    fun permissionRevocationRemovesWindowWithoutActivityResume() {
        permission("allow")
        main {
            controller = OverlayController(context)
            assertEquals(OverlayShowResult.SHOWN, checkNotNull(controller).show())
        }
        instrumentation.waitForIdleSync()
        main { assertTrue(checkNotNull(controller).isShowing) }
        permission("deny")
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var showing = true
        while (showing && SystemClock.elapsedRealtime() < deadline) {
            main { showing = checkNotNull(controller).isShowing }
            Thread.sleep(50)
        }
        assertFalse("Revocation must detach without resuming an Activity", showing)
    }

    @Test
    fun outsideTouchesReachActivityAndHideButtonDetachesImmediately() {
        permission("allow")
        ActivityScenario.launch(TouchTestActivity::class.java).use { scenario ->
            main {
                controller = OverlayController(context)
                assertEquals(OverlayShowResult.SHOWN, checkNotNull(controller).show())
            }
            instrumentation.waitForIdleSync()
            var point = 0 to 0
            scenario.onActivity { activity ->
                val button =
                    (activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout)
                        .getChildAt(0)
                val location = IntArray(2)
                button.getLocationOnScreen(location)
                point = location[0] + button.width / 2 to location[1] + button.height / 2
            }
            shell("input tap ${point.first} ${point.second}")
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(1, it.taps) }
            // Real input into the floating control, rather than invoking the controller directly.
            val hide =
                instrumentation.uiAutomation.windows
                    .flatMap { it.root?.findAccessibilityNodeInfosByText("Hide overlay").orEmpty() }
                    .first()
            val bounds = android.graphics.Rect()
            hide.getBoundsInScreen(bounds)
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            instrumentation.waitForIdleSync()
            main { assertFalse(checkNotNull(controller).isShowing) }
        }
    }

    @Test
    fun rotationHidesWithoutReattachingAndCloseReleasesWindow() {
        permission("allow")
        val automation = instrumentation.uiAutomation
        try {
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_0))
            Thread.sleep(500)
            ActivityScenario.launch(TouchTestActivity::class.java).use {
                main {
                    controller = OverlayController(context)
                    assertEquals(OverlayShowResult.SHOWN, checkNotNull(controller).show())
                }
                instrumentation.waitForIdleSync()
                main { assertTrue(checkNotNull(controller).isShowing) }
                assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
                val deadline = SystemClock.elapsedRealtime() + 5_000
                var showing = true
                while (showing && SystemClock.elapsedRealtime() < deadline) {
                    main { showing = checkNotNull(controller).isShowing }
                    Thread.sleep(50)
                }
                assertFalse("Rotation must invalidate the overlay", showing)
                main {
                    assertEquals(OverlayShowResult.SHOWN, checkNotNull(controller).show())
                    checkNotNull(controller).close()
                    assertFalse(checkNotNull(controller).isShowing)
                }
            }
        } finally {
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun permission(mode: String) {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(descriptor)
                .bufferedReader()
                .use { it.readText() }
        }
}

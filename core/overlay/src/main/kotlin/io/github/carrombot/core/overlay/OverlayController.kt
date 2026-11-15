package io.github.carrombot.core.overlay

import android.app.AppOpsManager
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import java.io.Closeable

/** Explicit outcome; a denied or failed window never represents active assistance. */
public enum class OverlayShowResult { SHOWN, PERMISSION_DENIED, FAILED, CLOSED }

/**
 * Main-looper-owned single overlay window. The service must close this before destruction.
 * Show is explicit, permission-checked and idempotent; hide detaches synchronously and releases
 * listeners. Permission loss hides and notifies the owner; display changes hide conservatively
 * rather than retain stale coordinates. Neither event automatically shows the overlay again.
 * Only the small control bounds consume touches; no gestures or gameplay inputs are generated.
 */
public class OverlayController(
    context: Context,
    private val onPermissionLost: () -> Unit = {},
) : Closeable {
    private val context = context.applicationContext
    private val windows = this.context.getSystemService(WindowManager::class.java)
    private val appOps = this.context.getSystemService(AppOpsManager::class.java)
    private val displays = this.context.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var view: AimOverlayView? = null
    private var watching = false
    private var closed = false
    private val permissionChanged =
        AppOpsManager.OnOpChangedListener { _, packageName ->
            if (packageName == this.context.packageName) {
                main.post {
                    if (view != null && !Settings.canDrawOverlays(this.context)) {
                        hide()
                        onPermissionLost()
                    }
                }
            }
        }
    private val displayChanged =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) hide()
            }

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) hide()
            }
        }

    /** True only while this controller owns an attached window. Read on the main looper. */
    public val isShowing: Boolean
        get() {
            checkMain()
            return view?.isAttachedToWindow == true
        }

    /** Does not open permission UI. The Activity must obtain user authorization first. */
    public fun show(): OverlayShowResult {
        checkMain()
        return when {
            closed -> {
                OverlayShowResult.CLOSED
            }

            !Settings.canDrawOverlays(context) -> {
                hide()
                OverlayShowResult.PERMISSION_DENIED
            }

            view != null -> {
                OverlayShowResult.SHOWN
            }

            else -> {
                attach()
            }
        }
    }

    private fun attach(): OverlayShowResult {
        val candidate = AimOverlayView(context, ::hide)
        return try {
            watching = true
            appOps.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, permissionChanged)
            displays.registerDisplayListener(displayChanged, main)
            // Recheck after registration so revocation cannot fall between checking and watching.
            if (!Settings.canDrawOverlays(context)) {
                hide()
                OverlayShowResult.PERMISSION_DENIED
            } else {
                view = candidate
                windows.addView(candidate, layout())
                OverlayShowResult.SHOWN
            }
        } catch (_: WindowManager.BadTokenException) {
            hide()
            OverlayShowResult.FAILED
        } catch (_: WindowManager.InvalidDisplayException) {
            hide()
            OverlayShowResult.FAILED
        } catch (_: SecurityException) {
            hide()
            OverlayShowResult.PERMISSION_DENIED
        }
    }

    /** Immediate removal, including while backgrounded. Repeated calls are safe. */
    public fun hide() {
        checkMain()
        val previous = view
        view = null
        try {
            if (previous != null) {
                try {
                    windows.removeViewImmediate(previous)
                } catch (_: IllegalArgumentException) {
                    // Android already removed the window (for example on app-op revocation).
                }
            }
        } finally {
            if (watching) {
                watching = false
                try {
                    appOps.stopWatchingMode(permissionChanged)
                } finally {
                    displays.unregisterDisplayListener(displayChanged)
                    main.removeCallbacksAndMessages(null)
                }
            }
        }
    }

    /** Terminal cleanup. A closed controller cannot be shown again. */
    override fun close() {
        checkMain()
        closed = true
        hide()
    }

    private fun layout(): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                title = "CarromBotOverlay"
            }

    private fun checkMain() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Overlay operations require the main looper" }
    }
}

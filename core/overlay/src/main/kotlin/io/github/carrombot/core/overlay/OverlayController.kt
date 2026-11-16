package io.github.carrombot.core.overlay

import android.app.AppOpsManager
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import io.github.carrombot.core.model.CoordinateTransform
import io.github.carrombot.core.model.Vec2
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
@Suppress("TooManyFunctions") // Cohesive main-looper window ownership and teardown.
public class OverlayController(
    context: Context,
    private val onPermissionLost: () -> Unit = {},
    private val onAction: ((AimAction) -> Unit)? = null,
    private val onEndSharing: () -> Unit = {},
) : Closeable {
    private val context = context.applicationContext
    private val windows = this.context.getSystemService(WindowManager::class.java)
    private val appOps = this.context.getSystemService(AppOpsManager::class.java)
    private val displays = this.context.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var view: AimOverlayView? = null
    private var watching = false
    private var closed = false
    private var controlsState = AimControlsState()
    private var preview: FrameLayout? = null
    private var previewScene: OverlayScene? = null
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
        val controls = onAction?.let { FloatingControls(context, it, onEndSharing).apply { render(controlsState) } }
        val candidate = AimOverlayView(context, ::hide, controls)
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

    /** Retains presentation only; updating a hidden window never reattaches it. */
    public fun updateControls(state: AimControlsState) {
        checkMain()
        if (closed) return
        controlsState = state
        view?.controls?.render(state)
    }

    /**
     * Synthetic-only separate preview below the source board, never a claimed live screen alignment.
     * Bounded non-touchable window stays below Android's obscuring-opacity threshold. Hidden controls
     * never reattach it. Keeping preview off the source viewport avoids capture/overlay feedback.
     */
    @Suppress("MagicNumber", "ReturnCount") // Fixed preview layout/opacity with explicit refusal paths.
    public fun updateSyntheticPreview(scene: OverlayScene?): Boolean {
        checkMain()
        if (alreadyShowing(scene)) return true
        removePreview()
        if (closed || view == null) return true
        if (scene == null) return true
        if (!Settings.canDrawOverlays(context)) return false
        val density = context.resources.displayMetrics.density
        val width = context.resources.displayMetrics.widthPixels / 2 - (16 * density).toInt()
        val legendHeight = (100 * density).toInt()
        val titleHeight = (48 * density).toInt()
        if (width <= 0) return false
        val trajectory = TrajectoryView(context)
        val candidate = previewView(trajectory, width, legendHeight, titleHeight)
        try {
            val parameters =
                WindowManager
                    .LayoutParams(
                        width,
                        width + legendHeight + titleHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        gravity = Gravity.BOTTOM or Gravity.START
                        title = "CarromBotSyntheticPreview"
                        alpha = previewOpacity()
                    }
            preview = candidate
            previewScene = scene
            windows.addView(candidate, parameters)
            trajectory.submit(
                scene,
                BoardMapping(
                    scene.bounds,
                    CoordinateTransform(
                        scene.bounds.min,
                        Vec2(0.0, legendHeight.toDouble()),
                        width / scene.bounds.width,
                    ),
                ),
            )
            return true
        } catch (_: WindowManager.BadTokenException) {
            removePreview()
        } catch (_: WindowManager.InvalidDisplayException) {
            removePreview()
        } catch (_: SecurityException) {
            hide()
            onPermissionLost()
        }
        return false
    }

    private fun alreadyShowing(scene: OverlayScene?): Boolean {
        val attached = preview?.isAttachedToWindow == true
        return scene === previewScene && attached
    }

    private fun previewView(
        trajectory: TrajectoryView,
        width: Int,
        legendHeight: Int,
        titleHeight: Int,
    ): FrameLayout =
        FrameLayout(context).apply {
            addView(
                trajectory,
                FrameLayout.LayoutParams(width, width + legendHeight).apply {
                    topMargin = titleHeight
                },
            )
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.synthetic_preview)
                    setTextColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.WHITE)
                },
                FrameLayout.LayoutParams(width, titleHeight),
            )
        }

    @Suppress("MagicNumber") // Half the system limit, capped at 0.5 for the one preview window.
    private fun previewOpacity(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maximum = context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
            minOf(0.5f, maximum / 2)
        } else {
            0.5f
        }

    private fun removePreview() {
        val previous = preview
        preview = null
        previewScene = null
        if (previous != null) {
            try {
                windows.removeViewImmediate(previous)
            } catch (_: IllegalArgumentException) {
                // Platform already removed this window.
            }
        }
    }

    /** Immediate removal, including while backgrounded. Repeated calls are safe. */
    public fun hide() {
        checkMain()
        removePreview()
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

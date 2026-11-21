package io.github.carrombot

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.carrombot.core.overlay.FloatingControls

/** Explicit synthetic source, not gameplay. Fresh system consent is required for every capture run. */
@Suppress("MagicNumber") // Fixed synthetic-fixture UI dimensions, never game calibration.
public class SyntheticDemoActivity : Activity() {
    private lateinit var board: SyntheticBoardView
    private lateinit var status: TextView
    private var pendingRequest: Int? = null
    private var nextRequestCode = FIRST_CONSENT_REQUEST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val gap = (16 * density).toInt()
        val width = resources.displayMetrics.widthPixels
        val size = DemoLayout.fitSourceSize(width / 2 - gap, width, FloatingControls.widthPixels(this), gap)
        board = SyntheticBoardView(this)
        status = TextView(this).apply { setText(R.string.synthetic_explanation) }
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                fitsSystemWindows = true
                addView(status)
                addView(board, LinearLayout.LayoutParams(size, size))
                addView(
                    Button(this@SyntheticDemoActivity).apply {
                        setText(R.string.synthetic_start)
                        minHeight = (48 * density).toInt()
                        setOnClickListener { requestConsent() }
                    },
                )
                addView(
                    Button(this@SyntheticDemoActivity).apply {
                        setText(R.string.stop_assistant)
                        minHeight = (48 * density).toInt()
                        setOnClickListener {
                            pendingRequest = null
                            stopService(Intent(this@SyntheticDemoActivity, CaptureService::class.java))
                        }
                    },
                )
            }
        setContentView(content)
    }

    @Suppress("DEPRECATION", "ReturnCount") // Explicit refusal paths before starting consent UI.
    private fun requestConsent() {
        if (pendingRequest != null) return
        if (!Settings.canDrawOverlays(this)) {
            status.setText(R.string.synthetic_needs_overlay)
            return
        }
        // One source viewport plus a separate bottom preview; refuse overlap on small displays.
        val location = IntArray(2)
        board.getLocationOnScreen(location)
        val metrics = displaySize()
        val visibleFrame = android.graphics.Rect()
        board.getWindowVisibleDisplayFrame(visibleFrame)
        val gap = (16 * resources.displayMetrics.density).toInt()
        val horizontal =
            DemoLayout.hasHorizontalClearance(
                location[0],
                board.width,
                minOf(metrics.widthPixels, visibleFrame.right),
                FloatingControls.widthPixels(this),
                gap,
            )
        val vertical =
            DemoLayout.hasVerticalClearance(
                location[1],
                board.height,
                minOf(metrics.heightPixels, visibleFrame.bottom),
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.density,
            )
        if (!horizontal || board.width < 100 || !vertical) {
            status.setText(R.string.synthetic_needs_space)
            return
        }
        stopService(Intent(this, CaptureService::class.java))
        val requestCode = nextRequestCode++
        pendingRequest = requestCode
        val manager = getSystemService(MediaProjectionManager::class.java)
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            pendingRequest = null
            status.setText(R.string.status_unavailable)
        }
    }

    @Deprecated("Framework consent callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pendingRequest) return
        pendingRequest = null
        if (resultCode != RESULT_OK || data == null || !Settings.canDrawOverlays(this)) return
        val location = IntArray(2)
        board.getLocationOnScreen(location)
        val display = displaySize()
        try {
            startForegroundService(
                Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START
                    putExtra(CaptureService.EXTRA_CONSENT, data)
                    putExtra(
                        CaptureService.EXTRA_DEMO_VIEWPORT,
                        intArrayOf(location[0], location[1], board.width, display.widthPixels, display.heightPixels),
                    )
                },
            )
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            status.setText(R.string.status_capture_failed)
        }
    }

    @Suppress("DEPRECATION")
    private fun displaySize(): android.util.DisplayMetrics =
        android.util.DisplayMetrics().also {
            windowManager.defaultDisplay.getRealMetrics(it)
        }

    private companion object {
        const val FIRST_CONSENT_REQUEST = 350
    }
}

/** Original generated synthetic-fixture-v1. A changing margin tick causes fresh compositor frames. */
@Suppress("MagicNumber")
internal class SyntheticBoardView(
    context: android.content.Context,
) : View(context) {
    private val paint = Paint()
    private var tick = 0

    init {
        contentDescription = context.getString(R.string.synthetic_board)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 500f, height / 500f)
        paint.color = Color.rgb(110, 110, 110)
        canvas.drawRect(0f, 0f, 500f, 500f, paint)
        paint.color = Color.MAGENTA
        canvas.drawRect(0f, 0f, 30f, 30f, paint)
        paint.color = Color.CYAN
        canvas.drawRect(470f, 0f, 500f, 30f, paint)
        paint.color = Color.BLACK
        canvas.drawRect(48f, 48f, 452f, 452f, paint)
        paint.color = Color.rgb(210, 180, 130)
        canvas.drawRect(52f, 52f, 448f, 448f, paint)
        paint.color = Color.GREEN
        canvas.drawCircle(390f, 110f, 32f, paint)
        paint.color = Color.RED
        canvas.drawCircle(270f, 110f, 20f, paint)
        paint.color = Color.BLUE
        canvas.drawCircle(150f, 110f, 24f, paint)
        paint.color = if (tick++ % 2 == 0) Color.WHITE else Color.BLACK
        canvas.drawRect(200f, 470f, 220f, 490f, paint)
        canvas.restore()
        if (isAttachedToWindow) postInvalidateDelayed(100)
    }
}

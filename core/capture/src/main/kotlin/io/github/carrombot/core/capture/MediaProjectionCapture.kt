package io.github.carrombot.core.capture

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import java.util.concurrent.CancellationException

/** Why a capture session ended. No exception text or screen data is exposed to diagnostics. */
public enum class CaptureEnd {
    STOPPED,
    REVOKED,
    FAILED,
}

/**
 * Owns one already-consented projection and its single virtual display. Construct, [start], and
 * [close] on [handler]'s looper. The caller must first start a mediaProjection foreground service.
 *
 * [onFrame] borrows an RGBA Image on that same looper, valid only until the callback returns.
 * Do not retain, close, enqueue, or asynchronously read it or its plane buffers. Images are always
 * closed, even on consumer failure. A slow consumer drops old frames: acquireLatestImage and two
 * reader slots bound native buffering; there is no application frame queue or pixel copy.
 * [onEnd] runs once after cleanup. Closing is terminal and never reuses a consent token.
 */
public class MediaProjectionCapture(
    private val context: Context,
    private val projection: MediaProjection,
    private val handler: Handler,
    private val onFrame: (Image) -> Unit,
    private val onEnd: (CaptureEnd) -> Unit,
) : AutoCloseable {
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var started = false
    private var ended = false
    private var width = 0
    private var height = 0
    private val callback =
        object : MediaProjection.Callback() {
            override fun onStop() = finish(CaptureEnd.REVOKED)

            override fun onCapturedContentResize(
                width: Int,
                height: Int,
            ) {
                guarded { resize(width, height) }
            }
        }
    private val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) finish(CaptureEnd.FAILED)
            }

            override fun onDisplayChanged(displayId: Int) {
                // API 34+ reports the actual captured app region through onCapturedContentResize.
                val usesDisplaySize = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                if (usesDisplaySize && displayId == Display.DEFAULT_DISPLAY) {
                    guarded {
                        val size = displaySize()
                        resize(size.x, size.y)
                    }
                }
            }
        }

    /** Creates at most one virtual display. All setup/consumer failures release the entire session. */
    public fun start() {
        checkThread()
        check(!started && !ended) { "A capture session is single-use" }
        started = true
        guarded {
            projection.registerCallback(callback, handler)
            displays.registerDisplayListener(displayListener, handler)
            val size = displaySize()
            reader = newReader(size.x, size.y)
            display = projection.createVirtualDisplay(
                "CarromBotCapture",
                size.x,
                size.y,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface,
                null,
                handler,
            ) ?: error("Virtual display unavailable")
        }
    }

    override fun close(): Unit = finish(CaptureEnd.STOPPED)

    @Suppress("DEPRECATION")
    private fun displaySize(): android.graphics.Point {
        val physicalDisplay = checkNotNull(displays.getDisplay(Display.DEFAULT_DISPLAY))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // A fresh display context avoids the Service's configuration lag during rotation.
            val metrics =
                context
                    .createDisplayContext(physicalDisplay)
                    .getSystemService(WindowManager::class.java)
                    .maximumWindowMetrics.bounds
            return android.graphics.Point(metrics.width(), metrics.height())
        }
        val point = android.graphics.Point()
        physicalDisplay.getRealSize(point)
        return point
    }

    // Every runtime setup failure must release the reader before ownership transfers to the session.
    @Suppress("TooGenericExceptionCaught")
    private fun newReader(
        newWidth: Int,
        newHeight: Int,
    ): ImageReader {
        require(newWidth > 0 && newHeight > 0)
        val next = ImageReader.newInstance(newWidth, newHeight, PixelFormat.RGBA_8888, MAX_IMAGES)
        try {
            next.setOnImageAvailableListener({ source ->
                if (!ended && source === reader) {
                    guarded { source.acquireLatestImage()?.use(onFrame) }
                }
            }, handler)
        } catch (failure: RuntimeException) {
            next.close()
            throw failure
        }
        width = newWidth
        height = newHeight
        return next
    }

    private fun resize(
        newWidth: Int,
        newHeight: Int,
    ) {
        if (ended || (newWidth == width && newHeight == height)) return
        val activeDisplay = display ?: return
        val previous = reader
        val next = newReader(newWidth, newHeight)
        // Assign before any fallible display operation, so failure cleanup owns the new reader too.
        reader = next
        try {
            activeDisplay.surface = null
            activeDisplay.resize(newWidth, newHeight, densityDpi)
            activeDisplay.surface = next.surface
        } finally {
            try {
                previous?.setOnImageAvailableListener(null, null)
            } finally {
                previous?.close()
            }
        }
    }

    private inline fun guarded(action: () -> Unit) {
        if (ended) return
        try {
            action()
        } catch (cancelled: CancellationException) {
            finish(CaptureEnd.FAILED)
            throw cancelled
        } catch (_: RuntimeException) {
            finish(CaptureEnd.FAILED)
        }
    }

    private fun finish(reason: CaptureEnd) {
        checkThread()
        if (ended) return
        ended = true
        // Nested finally blocks preserve ownership even if a platform release operation fails.
        try {
            try {
                displays.unregisterDisplayListener(displayListener)
            } finally {
                projection.unregisterCallback(callback)
            }
        } finally {
            releaseProjection(reason)
        }
    }

    private fun releaseProjection(reason: CaptureEnd) {
        try {
            releaseDisplayAndReader()
        } finally {
            try {
                projection.stop()
            } finally {
                onEnd(reason)
            }
        }
    }

    private fun releaseDisplayAndReader() {
        try {
            display?.release()
            display = null
        } finally {
            try {
                reader?.setOnImageAvailableListener(null, null)
            } finally {
                reader?.close()
                reader = null
            }
        }
    }

    private fun checkThread() {
        check(Looper.myLooper() == handler.looper) { "Capture must be accessed on its owner looper" }
    }

    private companion object {
        const val MAX_IMAGES = 2
    }
}

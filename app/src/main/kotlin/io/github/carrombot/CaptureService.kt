package io.github.carrombot

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import io.github.carrombot.core.capture.CaptureEnd
import io.github.carrombot.core.capture.MediaProjectionCapture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class CapturePhase { STARTING, CAPTURING, STOPPED, REVOKED, FAILED }

/** Metadata only; no pixels, consent data or exception messages leave the capture thread. */
internal data class CaptureSnapshot(
    val phase: CapturePhase,
    val frames: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
)

/** Started only by an Activity's fresh consent result; never restarts or persists a grant. */
public class CaptureService : Service() {
    private val snapshot = AtomicReference(CaptureSnapshot(CapturePhase.STARTING))
    private val main = Handler(Looper.getMainLooper())
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler

    // Owned exclusively by worker. Framework lifecycle calls serialize cleanup onto that looper.
    private var capture: MediaProjectionCapture? = null
    private var accepted = false
    private val stopping = AtomicBoolean(false)

    internal inner class CaptureBinder : Binder() {
        fun snapshot(): CaptureSnapshot = this@CaptureService.snapshot.get()
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("CarromBotCapture").apply { start() }
        worker = Handler(thread.looper)
    }

    override fun onBind(intent: Intent?): IBinder = CaptureBinder()

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when {
            intent?.action == ACTION_STOP -> stopCapture()
            !accepted -> startConsentedCapture(intent)
        }
        return START_NOT_STICKY
    }

    private fun startConsentedCapture(intent: Intent?) {
        val data = consentData(intent)
        intent?.removeExtra(EXTRA_CONSENT)
        if (intent?.action != ACTION_START || data == null) {
            snapshot.set(CaptureSnapshot(CapturePhase.FAILED))
            stopSelf()
            return
        }
        accepted = true
        snapshot.set(CaptureSnapshot(CapturePhase.STARTING))
        try {
            startCaptureForeground()
            worker.post { openCapture(data) }
        } catch (_: RuntimeException) {
            snapshot.set(CaptureSnapshot(CapturePhase.FAILED))
            stopCapture()
        }
    }

    @Suppress("DEPRECATION")
    private fun consentData(intent: Intent?): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CONSENT, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_CONSENT)
        }

    private fun openCapture(data: Intent) {
        if (stopping.get()) return
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = checkNotNull(manager.getMediaProjection(Activity.RESULT_OK, data))
            val session =
                MediaProjectionCapture(
                    this,
                    projection,
                    worker,
                    onFrame = { image ->
                        val previous = snapshot.get()
                        val active = previous.phase == CapturePhase.STARTING || previous.phase == CapturePhase.CAPTURING
                        if (!stopping.get() && active) {
                            snapshot.set(
                                CaptureSnapshot(CapturePhase.CAPTURING, previous.frames + 1, image.width, image.height),
                            )
                        }
                    },
                    onEnd = { reason ->
                        snapshot.set(snapshot.get().copy(phase = reason.toPhase()))
                        main.post {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    },
                )
            capture = session
            session.start()
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            snapshot.set(CaptureSnapshot(CapturePhase.FAILED))
            main.post {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startCaptureForeground() {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val controls =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            Notification
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(getString(R.string.capture_notification_title))
                .setContentText(getString(R.string.capture_notification_text))
                .setContentIntent(controls)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, getString(R.string.stop_assistant), stop).build())
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopCapture() {
        stopping.set(true)
        worker.post { capture?.close() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopping.set(true)
        worker.post {
            try {
                capture?.close()
                capture = null
            } finally {
                thread.quitSafely()
            }
        }
        super.onDestroy()
    }

    private fun CaptureEnd.toPhase(): CapturePhase =
        when (this) {
            CaptureEnd.STOPPED -> CapturePhase.STOPPED
            CaptureEnd.REVOKED -> CapturePhase.REVOKED
            CaptureEnd.FAILED -> CapturePhase.FAILED
        }

    internal companion object {
        const val ACTION_START = "io.github.carrombot.START_CAPTURE"
        const val ACTION_STOP = "io.github.carrombot.STOP_CAPTURE"
        const val EXTRA_CONSENT = "consent"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 20
    }
}

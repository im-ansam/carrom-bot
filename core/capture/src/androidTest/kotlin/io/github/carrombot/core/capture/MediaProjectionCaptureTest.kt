package io.github.carrombot.core.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.Image
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Test-only state in the library test APK; no hooks or captured pixels enter production. */
internal object CaptureProbe {
    var ready = CompletableFuture<ProjectionTestService>()
    var ended = CompletableFuture<CaptureEnd>()
    var consume: (Image) -> Unit = {}
}

/** Real system consent, never a fabricated successful projection grant. */
class ConsentTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Disposable capture lifecycle test" })
        @Suppress("DEPRECATION")
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 1)
    }

    @Deprecated("Test permission callback")
    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        check(resultCode == RESULT_OK && data != null)
        startForegroundService(Intent(this, ProjectionTestService::class.java).putExtra("consent", data))
    }
}

/** Foreground platform boundary used only by instrumentation to exercise the real adapter. */
class ProjectionTestService : Service() {
    private val thread = HandlerThread("CaptureTest").apply { start() }
    val handler = Handler(thread.looper)
    private var projection: MediaProjection? = null
    private var capture: MediaProjectionCapture? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("test", "Capture lifecycle test", NotificationManager.IMPORTANCE_LOW),
        )
        val notification =
            Notification
                .Builder(this, "test")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Disposable capture test")
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
        val data = checkNotNull(intent?.getParcelableExtra<Intent>("consent"))
        handler.post {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val grant = checkNotNull(manager.getMediaProjection(Activity.RESULT_OK, data))
            projection = grant
            capture =
                MediaProjectionCapture(this, grant, handler, CaptureProbe.consume) {
                    CaptureProbe.ended.complete(it)
                }
            capture?.start()
            CaptureProbe.ready.complete(this)
        }
        return START_NOT_STICKY
    }

    fun revoke() {
        handler.post { projection?.stop() }
    }

    override fun onDestroy() {
        handler.post {
            capture?.close()
            thread.quitSafely()
        }
        super.onDestroy()
    }
}

@RunWith(AndroidJUnit4::class)
class MediaProjectionCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun systemRevocationEndsSessionAndClosesBorrowedImages() {
        val count = AtomicInteger()
        val borrowed = CompletableFuture<Image>()
        withCapture({ image ->
            // Deliberately slow synchronous consumer: no queue or leaked reader slots.
            Thread.sleep(50)
            count.incrementAndGet()
            borrowed.complete(image)
        }) { service ->
            val image = borrowed.get(15, TimeUnit.SECONDS)
            service.revoke()
            assertEquals(CaptureEnd.REVOKED, CaptureProbe.ended.get(15, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) { image.planes }
            val finalCount = count.get()
            val drained = CompletableFuture<Unit>()
            service.handler.post { drained.complete(Unit) }
            drained.get(5, TimeUnit.SECONDS)
            assertEquals(finalCount, count.get())
        }
    }

    @Test
    fun consumerFailureClosesImageAndTerminatesProjection() {
        val borrowed = CompletableFuture<Image>()
        withCapture({ image ->
            borrowed.complete(image)
            error("Synthetic consumer failure")
        }) { _ ->
            val image = borrowed.get(15, TimeUnit.SECONDS)
            assertEquals(CaptureEnd.FAILED, CaptureProbe.ended.get(15, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) { image.planes }
        }
    }

    private fun withCapture(
        consumer: (Image) -> Unit,
        verify: (ProjectionTestService) -> Unit,
    ) {
        CaptureProbe.ready = CompletableFuture()
        CaptureProbe.ended = CompletableFuture()
        CaptureProbe.consume = consumer
        try {
            ActivityScenario.launch(ConsentTestActivity::class.java).use {
                acceptConsent()
                verify(CaptureProbe.ready.get(15, TimeUnit.SECONDS))
            }
        } finally {
            val context = instrumentation.targetContext
            context.stopService(Intent(context, ProjectionTestService::class.java))
            CaptureProbe.consume = {}
        }
    }

    private fun acceptConsent() {
        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
        var clicked = false
        while (!clicked && android.os.SystemClock.elapsedRealtime() < deadline) {
            clicked = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByViewId("android:id/button1")
                ?.any { node -> node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
            if (!clicked) Thread.sleep(100)
        }
        assertTrue("Real Android consent dialog must be accepted", clicked)
    }
}

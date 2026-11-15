package io.github.carrombot

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Activity-owned metadata subscription. Binding never creates or starts a capture service. */
internal class CaptureConnection(
    private val context: Context,
    private val onSnapshot: (CaptureSnapshot) -> Unit,
) {
    private val ui = Handler(Looper.getMainLooper())
    private var binder: CaptureService.CaptureBinder? = null
    private var bound = false
    private val poll =
        object : Runnable {
            override fun run() {
                val snapshot = binder?.snapshot() ?: return
                onSnapshot(snapshot)
                if (snapshot.phase in TERMINAL_PHASES) disconnect()
                if (bound) ui.postDelayed(this, STATUS_INTERVAL_MS)
            }
        }
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder,
            ) {
                binder = service as? CaptureService.CaptureBinder
                ui.removeCallbacks(poll)
                poll.run()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                val terminal =
                    binder?.snapshot()?.takeIf { it.phase in TERMINAL_PHASES }
                        ?: CaptureSnapshot(CapturePhase.STOPPED)
                // A non-creating binding does not keep a stopped service alive. Remove its dead
                // registration and preserve the local terminal result even if polling lost the race.
                disconnect()
                onSnapshot(terminal)
            }
        }

    fun connect(): Boolean {
        if (!bound) bound = context.bindService(Intent(context, CaptureService::class.java), connection, 0)
        return bound
    }

    fun disconnect() {
        ui.removeCallbacks(poll)
        binder = null
        if (bound) context.unbindService(connection)
        bound = false
    }

    private companion object {
        const val STATUS_INTERVAL_MS = 250L
        val TERMINAL_PHASES = setOf(CapturePhase.STOPPED, CapturePhase.REVOKED, CapturePhase.FAILED)
    }
}

internal fun CaptureSnapshot.statusText(context: Context): String =
    when (phase) {
        CapturePhase.STARTING -> context.getString(R.string.status_capture_starting)
        CapturePhase.CAPTURING -> context.getString(R.string.status_capturing, frames, width, height)
        CapturePhase.STOPPED -> context.getString(R.string.status_idle)
        CapturePhase.REVOKED -> context.getString(R.string.status_capture_revoked)
        CapturePhase.FAILED -> context.getString(R.string.status_capture_failed)
    }

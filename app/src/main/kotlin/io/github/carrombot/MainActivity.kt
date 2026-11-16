package io.github.carrombot

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import io.github.carrombot.core.model.AssistantSetupState
import io.github.carrombot.core.model.SetupStage
import io.github.carrombot.databinding.ActivityMainBinding

/**
 * Requests fresh user consent and hands it once to the capture service. Activity recreation
 * discards pending consent; an already-running service continues independently in the foreground.
 */
public class MainActivity : Activity() {
    private lateinit var binding: ActivityMainBinding
    private var state = AssistantSetupState()
    private var pendingCaptureRequest: Int? = null
    private var nextRequestCode = FIRST_CAPTURE_REQUEST
    private lateinit var captureConnection: CaptureConnection
    private var captureState: CaptureSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        captureConnection =
            CaptureConnection(this) { snapshot ->
                captureState = snapshot
                render()
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) binding.screenTitle.isAccessibilityHeading = true
        binding.startButton.setOnClickListener { startSetup() }
        binding.stopButton.setOnClickListener {
            pendingCaptureRequest = null
            stopCapture()
            update(DiagnosticEvent.STOP_PRESSED, state.stop())
        }
        binding.settingsButton.setOnClickListener {
            logDiagnostic(DiagnosticEvent.SETTINGS_OPENED, state)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.syntheticButton.setOnClickListener {
            startActivity(Intent(this, SyntheticDemoActivity::class.java))
        }
        update(DiagnosticEvent.APP_OPENED, state)
    }

    override fun onStart() {
        super.onStart()
        // A non-creating bind may succeed without a running service or any connection callback.
        // Restore active metadata only when the live service actually supplies a snapshot.
        if (captureState?.phase in ACTIVE_PHASES) captureState = null
        captureConnection.connect()
    }

    override fun onResume() {
        super.onResume()
        update(DiagnosticEvent.PERMISSION_STATE, state.observeOverlay(Settings.canDrawOverlays(this)))
        if (!state.overlayAllowed) stopCapture()
        if (state.stage != SetupStage.CAPTURE_REQUESTED) pendingCaptureRequest = null
    }

    override fun onStop() {
        super.onStop()
        captureConnection.disconnect()
        if (state.stage != SetupStage.OVERLAY_REQUESTED && state.stage != SetupStage.CAPTURE_REQUESTED) {
            update(DiagnosticEvent.SESSION_RESET, state.stop())
        }
    }

    // Framework Activity results keep the foundation free of an extra runtime UI dependency.
    @Deprecated("Framework callback retained for consent handling")
    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pendingCaptureRequest) return
        pendingCaptureRequest = null
        val observed = state.observeOverlay(Settings.canDrawOverlays(this))
        update(DiagnosticEvent.PERMISSION_STATE, observed.captureResult(resultCode == RESULT_OK && data != null))
        if (state.stage == SetupStage.CONSENT_CONFIRMED && data != null) {
            try {
                captureState = CaptureSnapshot(CapturePhase.STARTING)
                startForegroundService(
                    Intent(this, CaptureService::class.java).apply {
                        action = CaptureService.ACTION_START
                        putExtra(CaptureService.EXTRA_CONSENT, data)
                    },
                )
                captureConnection.connect()
                render()
            } catch (_: RuntimeException) {
                captureState = CaptureSnapshot(CapturePhase.FAILED)
                render()
            }
        }
    }

    private fun startSetup() {
        if (captureState?.phase == CapturePhase.CAPTURING || captureState?.phase == CapturePhase.STARTING) return
        captureState = null
        val observed = state.observeOverlay(Settings.canDrawOverlays(this))
        val requested = observed.start()
        update(DiagnosticEvent.START_PRESSED, requested)
        if (requested == observed) return
        try {
            if (requested.stage == SetupStage.OVERLAY_REQUESTED) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else if (requested.stage == SetupStage.CAPTURE_REQUESTED) {
                requestCaptureConsent()
            }
        } catch (_: ActivityNotFoundException) {
            pendingCaptureRequest = null
            update(DiagnosticEvent.PERMISSION_UI_UNAVAILABLE, state.launchFailed())
        } catch (_: SecurityException) {
            pendingCaptureRequest = null
            update(DiagnosticEvent.PERMISSION_UI_UNAVAILABLE, state.launchFailed())
        }
    }

    @Suppress("DEPRECATION")
    private fun requestCaptureConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val requestCode = nextRequestCode++
        pendingCaptureRequest = requestCode
        startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    private fun update(
        event: DiagnosticEvent,
        next: AssistantSetupState,
    ) {
        state = next
        logDiagnostic(event, state)
        render()
    }

    private fun render() {
        val overlayLabel = if (state.overlayAllowed) R.string.overlay_allowed else R.string.overlay_not_allowed
        binding.overlayStatus.setText(overlayLabel)
        binding.setupStatus.setText(
            when (state.stage) {
                SetupStage.IDLE -> R.string.status_idle
                SetupStage.OVERLAY_REQUESTED -> R.string.status_overlay_requested
                SetupStage.OVERLAY_DENIED -> R.string.status_overlay_denied
                SetupStage.CAPTURE_REQUESTED -> R.string.status_capture_requested
                SetupStage.CAPTURE_DENIED -> R.string.status_capture_denied
                SetupStage.CONSENT_CONFIRMED -> R.string.status_consent_confirmed
                SetupStage.UNAVAILABLE -> R.string.status_unavailable
            },
        )
        binding.startButton.isEnabled =
            state.stage != SetupStage.OVERLAY_REQUESTED && state.stage != SetupStage.CAPTURE_REQUESTED &&
            captureState?.phase != CapturePhase.STARTING && captureState?.phase != CapturePhase.CAPTURING
        captureState?.let { binding.setupStatus.text = it.statusText(this) }
    }

    private fun stopCapture() {
        stopService(Intent(this, CaptureService::class.java))
        captureConnection.disconnect()
        captureState = null
    }

    private companion object {
        const val FIRST_CAPTURE_REQUEST = 100
        val ACTIVE_PHASES = setOf(CapturePhase.STARTING, CapturePhase.CAPTURING)
    }
}

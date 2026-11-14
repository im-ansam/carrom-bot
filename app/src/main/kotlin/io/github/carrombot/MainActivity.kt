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
 * User-initiated permission setup, not a capture owner. Never obtains MediaProjection,
 * starts a service, attaches an overlay, or stores/forwards consent data. A fresh instance
 * starts stopped, including after rotation/process recreation. CB-20 owns actual capture.
 */
public class MainActivity : Activity() {
    private lateinit var binding: ActivityMainBinding
    private var state = AssistantSetupState()
    private var pendingCaptureRequest: Int? = null
    private var nextRequestCode = FIRST_CAPTURE_REQUEST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) binding.screenTitle.isAccessibilityHeading = true
        binding.startButton.setOnClickListener { startSetup() }
        binding.stopButton.setOnClickListener {
            pendingCaptureRequest = null
            update(DiagnosticEvent.STOP_PRESSED, state.stop())
        }
        binding.settingsButton.setOnClickListener {
            logDiagnostic(DiagnosticEvent.SETTINGS_OPENED, state)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        update(DiagnosticEvent.APP_OPENED, state)
    }

    override fun onResume() {
        super.onResume()
        update(DiagnosticEvent.PERMISSION_STATE, state.observeOverlay(Settings.canDrawOverlays(this)))
        if (state.stage != SetupStage.CAPTURE_REQUESTED) pendingCaptureRequest = null
    }

    override fun onStop() {
        super.onStop()
        if (state.stage != SetupStage.OVERLAY_REQUESTED && state.stage != SetupStage.CAPTURE_REQUESTED) {
            update(DiagnosticEvent.SESSION_RESET, state.stop())
        }
    }

    // Framework Activity results keep the foundation free of an extra runtime UI dependency.
    @Deprecated("Framework callback retained for the permission-only foundation")
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
        // The single-use consent Intent is deliberately discarded, even after RESULT_OK.
    }

    private fun startSetup() {
        val observed = state.observeOverlay(Settings.canDrawOverlays(this))
        val requested = observed.start()
        update(DiagnosticEvent.START_PRESSED, requested)
        if (requested == observed) return
        try {
            when (requested.stage) {
                SetupStage.OVERLAY_REQUESTED -> {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }

                SetupStage.CAPTURE_REQUESTED -> {
                    requestCaptureConsent()
                }

                else -> {
                    return
                }
            }
        } catch (_: ActivityNotFoundException) {
            permissionLaunchFailed()
        } catch (_: SecurityException) {
            permissionLaunchFailed()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestCaptureConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val requestCode = nextRequestCode++
        pendingCaptureRequest = requestCode
        startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    private fun permissionLaunchFailed() {
        pendingCaptureRequest = null
        update(DiagnosticEvent.PERMISSION_UI_UNAVAILABLE, state.launchFailed())
    }

    private fun update(
        event: DiagnosticEvent,
        next: AssistantSetupState,
    ) {
        state = next
        logDiagnostic(event, state)
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
            state.stage != SetupStage.OVERLAY_REQUESTED && state.stage != SetupStage.CAPTURE_REQUESTED
    }

    private companion object {
        const val FIRST_CAPTURE_REQUEST = 100
    }
}

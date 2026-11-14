# ADR 0003: User-initiated permission setup and closed diagnostics

Status: Proposed for CB-13 review, 2026-11-14.

## Scope and user experience

CB-13 adds launcher MainActivity, start/stop controls, permission state, settings
navigation and structured local logs. Native Activities, XML layouts and view binding
keep the foundation small. Both screens scroll, account for system insets, expose
48dp minimum touch targets and mark headings on API 28+ with a runtime version guard.
Settings is explicitly a placeholder, with an ordinary back stack and return button.

CB-20 owns the MediaProjection capture service and frame pipeline. CB-32 owns actual
WindowManager overlay attach/detach. No capture service or overlay is implemented here.
The UI clearly states that the assistant is unavailable and Start only checks permissions;
it never displays a fake Running state. This preserves dependency order while making
permission denial and retry concrete and testable now.

## Consent and lifecycle

AssistantSetupState is a platform-free immutable value in core:model. Its constructor
starts idle; Start is the only operation that requests a permission screen. An OS
permission observation can update status or invalidate setup, but never launches UI.
Overlay access is checked through Settings.canDrawOverlays on resume and before Start.
When missing, Start opens ACTION_MANAGE_OVERLAY_PERMISSION for the app. Returning
requires another Start tap before screen-sharing consent, so permission dialogs are
not silently chained. Denial, revocation and unavailable permission activities are
explicit retryable states. Only SYSTEM_ALERT_WINDOW is declared in the manifest.

When overlay access exists, Start opens MediaProjectionManager.createScreenCaptureIntent.
A matching pending request code, RESULT_OK, nonnull data and a current overlay recheck
are required to confirm consent. Results after Stop or from unrelated requests are
ignored. Duplicate Start taps are disabled while a request is pending. Activity/process
recreation resets setup and pending request identity; no permission state or consent
Intent is stored in a Bundle, preference, log or global variable. Leaving the screen
resets completed setup. The permission dialog's pause/stop transition retains only the
pending request identity until the result returns to that Activity instance.

Even a successful consent Intent is immediately discarded. No getMediaProjection,
VirtualDisplay, ImageReader, foreground service or WindowManager call occurs. A later
capture implementation must request fresh per-session consent, run the appropriate
foreground service, own/release resources and respond to revocation; it cannot reuse
this check as authorization. CB-20 must extend the integration rather than silently
turning CONSENT_CONFIRMED into active capture. Framework Activity result callbacks are
locally deprecated/suppressed only because this foundation uses framework Activities;
a future lifecycle integration may replace them with Activity Result APIs.

## Diagnostics

DiagnosticEvent is a closed enum. Formatting accepts only that enum and the immutable
permission state, yielding event, stage and overlay enum fields in Logcat under
CarromBot. There is no arbitrary string payload, frame/pixel/image input, consent token,
exception message, URI or user identifier. Logs are local; the app declares no network
permission and sends no telemetry. A JVM test checks the event format.

## Verification and limits

JVM transition tests cover no automatic prompts, overlay denial/regrant, duplicate
requests, capture denial/retry, explicit Stop, late results, revocation, recreation and
unavailable system UI. Instrumentation launches the actual Activities and checks
recreation, permission rechecks, intercepted capture denial/grant results, Stop and
settings return. Overlay app-op changes are scoped to this app and restored after
each test; tests should run on disposable emulators. The test monitor returns synthetic
consent results, never captures screens or accepts a real screen-sharing dialog.
These tests do not establish OEM permission-dialog behavior or a real capture pipeline.

References checked for CB-13:

- [Media projection](https://developer.android.com/media/grow/media-projection)
- [MediaProjectionManager](https://developer.android.com/reference/android/media/projection/MediaProjectionManager)
- [AndroidX Test releases](https://developer.android.com/jetpack/androidx/releases/test)

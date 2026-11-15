# ADR 0022: Service-owned, permission-safe floating overlay

Status: Proposed for CB-32 review, 2026-11-15.

## Decision

The existing user-initiated SYSTEM_ALERT_WINDOW settings flow remains in MainActivity.
OverlayController never opens permission UI. It checks Settings.canDrawOverlays before
showing and after registering its own-UID AppOps listener. WindowManager permission/token
failures produce explicit outcomes and clean up. CaptureService shows the overlay only
after a fresh consent grant starts capture; denied/failed attach stops capture explicitly.
Capture completion, failure, notification Stop, app Stop and service destruction close
the controller. Activity recreation/backgrounding does not own or duplicate the window.

All controller calls and window operations belong to the main looper. It owns at most
one TYPE_APPLICATION_OVERLAY window, one AppOps watcher and one display listener. Hide
calls removeViewImmediate synchronously and unregisters listeners; close is terminal.
AppOps notifications marshal onto the main looper, recheck actual permission and hide
before notifying the service. No captured pixels, permission Intent or exception messages
are stored or logged. The service remains non-exported and START_NOT_STICKY.

AimOverlayView currently contains one accessible 48dp-minimum Hide button, positioned at
the top/end inside system decorations. It does not render fake predictions. Hide affects
only the overlay; capture continues until Stop or Android ends sharing. The bounded window
uses FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCH_MODAL: touches inside activate Hide, touches
outside reach the underlying app. No full-screen touch-catching window, input injection,
dragging, focus theft or gameplay automation exists. A future non-touchable trajectory
surface must separately account for Android 12+ obscuring-opacity limits.

The window belongs to the default physical display. Its change/removal conservatively
hides rather than remaps stale coordinates. Other displays, including capture's virtual
display, cannot invalidate this window.
It never automatically reattaches; restarting sharing shows a fresh control. This is
deliberate until CB-33 supplies rendering and explicit coordinate transforms, and CB-34
supplies richer controls. No Android dependency enters model, physics or planner.

## Verification and limits

Instrumentation uses disposable emulator app-op changes and restores permission/rotation.
It checks denied attach, explicit regrant, repeated show/hide/close, revocation without an
Activity resume, rotation, real touch dispatch to underlying controls and the Hide button.
App integration exercises genuine screen-sharing consent and service Stop/destruction.
Actual commands, failures, device coverage and limits are in [validation](../validation-cb-32.md).
OEM window placement and permission UI behavior require separate device validation.

References:

- [Window flags and overlay input rules](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Own-UID AppOps permission watching](https://developer.android.com/reference/android/app/AppOpsManager)
- [Overlay permission checks](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context))

# ADR 0010: Consented, bounded Android screen capture

Status: Accepted for CB-20. Extends ADR 0003's permission-only foundation.

## Decision

The Activity remains the user-consent boundary. Only the currently pending Start request
may forward a successful Android consent result to the private CaptureService. Stop and
Activity recreation invalidate pending results. Grants are never saved or reused; the
service is START_NOT_STICKY and cannot be started by other apps, a boot receiver or a worker.
Existing overlay permission setup remains intact; overlay rendering is outside this ticket.

CaptureService enters a mediaProjection foreground service before obtaining MediaProjection.
Its notification supplies a Stop action. The started service owns a HandlerThread independently
of Activity binding, so capture continues while the app is backgrounded or recreated. A
non-creating local binding polls an immutable metadata snapshot every 250 ms while visible;
there are no per-frame UI tasks, pixel transfers, persisted grants or global production state.
The metadata count represents acquired frames, not vision processing or game accuracy.

core:capture owns MediaProjectionCapture, one projection callback, one virtual display and
an RGBA ImageReader with maxImages=2. A synchronous callback borrows each acquireLatestImage
result only until return. The adapter closes it even when the consumer throws. Consumers
must not retain images/planes or enqueue asynchronous work on borrowed buffers. Slow consumers
drop old images instead of accumulating a queue. Consumers must keep work bounded so lifecycle
callbacks can run promptly on the same owner looper. No image copies or frame storage exist.
The app currently consumes only frame count and dimensions; vision integration is future work.

All capture operations run on one looper. Stop, projection revocation, failed setup and consumer
failure converge on idempotent terminal cleanup: unregister listeners/callbacks, release the
virtual display, close ImageReader and stop projection. Nested finally blocks attempt every
release even if a platform release fails. Cancellation cleans up and propagates. Service
destruction schedules cleanup and quits its worker safely; pending startup checks a stop flag.

API 34+ uses onCapturedContentResize for the actual shared region, including app-only sharing.
Older versions observe physical-display changes. Resize detaches the old surface, resizes
the existing virtual display and installs a new reader, closing the previous one. It never
creates a second virtual display with the single-use grant. Dimensions are physical pixels;
Image plane row/pixel strides and timestamp semantics remain Android's Image contract. Board
coordinate mapping belongs to vision and does not enter platform-free model/physics/planner.

## Consequences and verification

Android foreground-service permissions are declared; notification permission is optional for
starting the service and no extra notification prompt is introduced. Android controls whether
the notification appears in the drawer or Active apps. Consent rejection produces no frames;
invalid/revoked grants fail explicitly. If capture ends while the Activity is absent, returning
shows stopped and requires another Start; terminal reasons are session-local, not persisted.

Tests accept genuine system consent only on a disposable emulator. App instrumentation checks
denial/retry, invalid consent, more delivered frames than reader slots, rotation, recreation,
background and Stop. Adapter instrumentation exercises system projection.stop revocation with
a slow consumer, terminal delivery, closed borrowed images and exception cleanup. Test-only
permission Activity/service fixtures live exclusively in the library's androidTest APK.
See the validation record for actual device/API coverage and outstanding modern-device checks.

Reference: [Android MediaProjection lifecycle and single-use consent rules](https://developer.android.com/media/grow/media-projection).

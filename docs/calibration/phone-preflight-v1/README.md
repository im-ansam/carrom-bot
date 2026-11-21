# Physical-phone preflight — synthetic diagnostics only

2026-11-20. User approved overlay access and each screen-sharing session on the
phone. No game shots were taken. Private captures and device identifiers remain
in ignored local validation files; they are not calibration inputs.

- Nothing Phone (2a), reported model A142, Android 16/API 36, build
  B4.1-260928-1157. Native display 1084 × 2412, density 420; no display overrides.
  Observed render rates included 60 and 120 Hz; game update clock is unknown.
- Installed Carrom Pool 19.6.0/code 1491 differs from the emulator's 19.6.1/1492.
  Equipment attributes, input mapping and dynamics remain unmeasured.
- Carrom Bot 0.1.0/code 1 captured its own app after user selected single-app
  sharing. Received-frame counter advanced from 669 to 1340. These are counters,
  not measured game frame rates. Floating controls appeared. Normal Start aiming
  refused with “Live aiming unavailable: calibrated game profile required”.
  Pause cleared predictions; stop aiming preserved sharing; ending sharing
  removed the app from MediaProjection state.
- Native vision instrumentation: 13 original-fixture tests passed on A142, no
  failures/errors/skips. This verifies fixture processing, not game detection.
- Original synthetic demo refused a 500 px source beside 630 px controls with
  a 42 px gap. Commit e52f98b fits the source to 412 px while retaining minimum
  size, horizontal overlap and vertical preview guards. Three regressions added.
- User approved fresh entire-screen sharing with the corrected synthetic demo
  visible. Its captured fixture produced a separate synthetic trajectory preview,
  candidate list and 40% power recommendation. Next best shot selected candidate 2
  and changed power to 50%. Pause cleared the preview and power. End screen sharing
  removed the app from MediaProjection state. Overlay access remains user-approved.

The synthetic demo uses artificial fixture physics and explicitly labels itself
“SYNTHETIC FIXTURE ONLY — no real-game calibration”. Its lines are not evidence
of Carrom Pool accuracy. Controlled physical-phone shots: **0**. Sealed heldouts:
**0**. No usable game profile, live game detector or real-game aiming enabled.
CB-39 remains incomplete; CB-40/CB-41 are not unblocked by this preflight.

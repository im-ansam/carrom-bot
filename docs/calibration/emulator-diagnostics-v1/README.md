# Emulator feasibility diagnostics — no calibration profile

2026-11-20. User authorized operating Pixel 10 and taking diagnostic practice
shots. These recordings do **not** validate Nothing Phone (2a), supply fresh
holdouts, or complete CB-39. All thirteen are unsealed diagnostics; setup and
trajectories were inspected during testing. There are zero sealed validation shots.
Source videos, UI/account imagery,
inspection sheets, native screenshots and controller scripts remain local and
ignored. Only sanitized numeric capture evidence is versioned here.

## Observed environment and setup constraints

- Pixel 10 Android emulator, x86_64 Android 17, package `com.miniclip.carrom`.
  Installed app version 19.6.1, code 1492. Settings additionally displayed
  CCFG 506, PCFG 159 and server 19.6.9 when inspected; those later readings cannot
  retroactively confirm configuration identity for the earlier clips.
- Native display 1080 × 2424, density 420, reported display mode about 60 Hz.
  The guest/engine update clock is unknown. Output encodings were 1080 × 2424,
  720 × 1616 or 540 × 1212; encoding resolution is distinct from display scope.
- A separate display-load experiment used override 720 × 1616/density 280.
  Its records remain separately scoped. Both overrides were reset afterward;
  `wm size` and `wm density` confirmed the original physical configuration.
- Practice begins with the normal rack. User confirmed coins can only be moved
  by normal striker shots; there is no confirmed free arrangement editor. A clear
  right-side lane is feasible without emptying the entire board. Isolated-contact
  and pocket setup feasibility is still unestablished. Do not replace those cells
  with rack breaks or assume arbitrary initial arrangements.
- The practice striker has a blue map-pin appearance. Its equipment identity,
  upgrade and effective Force/Aim/Time, and whether practice uses account equipment,
  remain unknown. The full input-graphic sweep/endpoints are not frozen. Touch
  displacement and an arrow length are not normalized game power.
- A tutorial shot confirmed the built-in instruction to drag the striker to aim
  and adjust force, then release. The tutorial uses another board/theme and a
  constrained target; completing it is control evidence, not dynamics validation.

## Capture findings

`capture-summary.json` records every created MP4's SHA-256, actual encoded PTS
statistics, resolution and disposition. The first nine are control/setup
diagnostics rather than a preassigned matrix. Incorrect/cancelled gestures and
turn resets are not physical trajectories. Some desktop emulator snapshots were
stale; subsequent input checks used fresh Android-native captures. No screenshot
is treated as timestamped trajectory ground truth.

In `emulator-feasibility-07`, two visually reviewed source intervals, 3.9–7.2s and
11.0–14.2s, have maximum encoded gaps 0.033789s and 0.049867s. Its whole recording
has a 0.081233s gap. Thus an idle gap does not automatically fail an unrelated
motion interval. The complete numeric PTS sequence is in `timing-07.json`.
These intervals were identified retrospectively for diagnostics, not preregistered
as a validation split. Timing alone does not establish correct centres, independent
annotations, event precision, identifiable dynamics or production replay accuracy.

After interruption/cold boot, `emulator-practice-resume-a1` captured a manually
reviewed moving interval 3.3–4.1s with a maximum gap **0.217033s** and eight gaps
over 0.05s. Its full recording has a 0.317344s gap. `timing-resume-a1.json`
preserves every PTS. This fails the existing moving-interval acquisition gate.
The reduced-display experiment timed out during setup and supplies no accepted
shot; its whole-recording gap reached 0.388256s, not an event-time measurement.
The next local guard cancelled before firing when placement/turn state was
ambiguous. No failing attempts are silently dropped or counted as zero error.

Practice also resets the striker shortly after its visible motion. A full second
of stationary rest at the actual end position was not corroborated in the reviewed
shots. Fading/teleportation toward the next baseline is a turn transition, never
slowing, a measured rest time or an extra collision. This observation does not
waive the [existing acquisition gates](../controlled-phone-protocol.md).

Cold-boot recovery was resource constrained on the 8 GB host. A requested 1536 MB
guest allocation was overridden by this emulator/image to 4096 MB. User closed
unused apps and Android/game boot eventually completed. The data do not isolate
host memory, translation, render load, display sampling or dynamics as a cause.
Lower encoding resolution improved some earlier diagnostics but did not establish
a consistently usable capture setup. Stop the acquisition stage here, rather than
collecting the formal batch under a failed timing/end-of-motion setup.

## Readiness and reproduction

No parameters are accepted or exported. Current blockers: reliable capture within
the predefined moving/event precision, independently corroborated end-of-motion,
confirmed effective equipment/configuration, full proxy sweep, two-pass numeric
annotations, preassigned development/untouched validation, frozen model/parameters
and actual production-simulator replay. No body, pocket, oblique-rail or full-board
capability is established. A future acquisition protocol must resolve the practical
rest/reset constraint before collecting a new sealed batch; it cannot narrow clock
brackets or remove a failed gate after seeing these outcomes. Phone evidence remains
separate and unavailable. CB-39 stays open; CB-40/41 remain blocked.

The reader uses pinned PyAV from `tools/calibration/requirements-media.txt`; the
PTS evaluator and its tests need only Python's standard library. Set `$source`
locally to the preserved external clip; never commit machine source paths.

```powershell
py -m unittest discover -s tools/calibration -v
py tools/calibration/capture_timing.py $source --label emulator-feasibility-07 --interval motion-1 3.9 7.2 --interval motion-2 11.0 14.2 --output .validation/reproduced-timing-07.json
```

Comparisons use rational PTS/time_base, retain all frames and count gaps intersecting
an interval including boundary-crossing gaps. Output has `diagnostic_only_no_profile`
status regardless of gap counts. Missing/noninteger/repeated/nonmonotonic PTS,
invalid time bases and unsupported interval bounds fail explicitly. The CLI rejects
an output equal to the original, including hard-link aliases. It never writes
decoded frames. Same original,
decoder and interval declarations reproduce the JSON bytes; numeric PTS auditing
does not infer an engine clock, interpolate missing observations or validate a profile.

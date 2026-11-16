# ADR 0024: Reversible advisory aiming controls

Status: Proposed for CB-34 review, 2026-11-16.

CaptureService owns AimViewModel on the main looper. It survives Activity recreation
without retaining an Activity, capture pixels or consent grant. This is a small state
holder, not an AndroidX lifecycle ViewModel: the service is its explicit lifetime owner.
The existing fresh-consent Activity flow still starts screen sharing. Within that
authorized session, Start aiming enters RUNNING, Stop aiming enters STOPPED, and
Pause/Resume enter PAUSED/RUNNING. Screen sharing continues during all aiming modes;
the UI says so. End screen sharing delegates to the existing terminal capture cleanup.
Hide remains pinned outside the scrolling controls and detaches immediately. Display
changes retain CB-32's conservative hide policy; no automatic reattachment is added.

Every mode change clears evidence and increments a generation token. submit/fail
accept only the active RUNNING generation. This prevents late work from a previous
pause/stop/start from restoring predictions. CB-35 must also own structured cancellation,
board-observation freshness and request invalidation; this token alone does not detect
a changed board. No background job, pipeline or scheduler is started in CB-34.

AimControlsState and AimAction are immutable overlay presentation contracts. A scene
is permitted only while RUNNING. A null scene means data unavailable; an explicit
empty scene means no available candidate. Failures clear predictions and require an
explicit Start retry. No exception text enters state. Candidate order is supplied by
the producer; Next best shot cycles that order and wraps. Direct selection and Next
replace OverlayScene.selectedId while keeping the same candidate evidence, so renderer
selection and displayed power agree. Recommended normalized power is rounded to a
percentage, not used to generate gestures. Confidence retains CB-33's interpretation.

FloatingControls uses native Buttons with at least 48dp touch targets, explicit
candidate selection/power descriptions, selected text and state, and a polite status
live region. Candidate button objects remain stable when only selection/power changes,
preserving accessibility focus. The scrollable panel is at most 240dp wide and the
smaller of 320dp or half the display height, below the separate Hide button. Large text
and candidate lists scroll rather than expand over the screen. Window flags, permission
watching, display ownership and immediate detachment remain those of CB-32. Overlay
still depends only on model; there is no new window or gameplay input mechanism.

Runtime starts with aiming stopped and contains no candidates. Starting displays an
honest unavailable-data status. CB-35 will supply actual planner/vision evidence and
attach trajectory rendering; CB-34 never invents a successful plan or trajectory.
The state and view APIs accept synthetic scenes only in tests at present. Phase 1
remains advisory and every actual shot is manual.

See [validation](../validation-cb-34.md) for executed checks and limitations.

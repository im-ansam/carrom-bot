# Phone input and timing diagnostics — no accepted profile

2026-11-20. User manually operated the Nothing Phone (2a)/A142 in Settings >
Account > Practice Mode > Play. These are precollection diagnostics, not any
preassigned Stage A attempt or sealed holdout. No private media, screenshots,
device identifiers or account data are versioned. Source originals remain local.

## Observed setup

Carrom Pool 19.6.0/code 1491, Android 16/API 36, native portrait 1084 × 2412,
density 420. Equipment UI marked Destination (Legendary) and Blur power USING.
The combined display showed bonus labels Force +1, Aim +2, Time +1 and segmented
bars. Upgrade level and numerical interpretation of the effective bars remain
unconfirmed; bonus labels are not total physics coefficients. No equipment was
changed or upgraded. Striker trails/animation and pocket effects remained enabled.
Both practice players displayed the same striker appearance. This does not prove
their hidden dynamics are identical. No physical dimensions or engine clock known.

Recordings used Android screenrecord v1.4, native-size request, 16,000,000 bit/s,
180s limit; each was stopped with SIGINT. Finalized copies were pulled after
process completion and their hashes matched Android's `sha256sum` results.
Decoded dimensions were 1084 × 2412, time base 1/90000, PyAV 19.0.2.

## Input survey

Original SHA-256:
`8f47e0c1b157a5220ce5dc8d24aeda164b224bd22bcb4f1348119c2416af4087`.
5,266 frames, presentation times 0–87.9791s. Reviewed sweep interval 57–69.5s:
maximum encoded gap 0.034533333s, zero gaps >0.05s. Whole clip has one 0.057988889s
gap at startup, outside that interval.

The coloured pull arrow grows/shrinks independently of the dotted aiming line;
the user returned the pull to zero. No rack displacement was seen. Later motion
belongs to the turn-reset sequence. Graphic endpoints, stable full-range extent,
measurement uncertainty and two independent annotation passes are not yet frozen.
The survey cannot establish normalized game power or a launch mapping.

An initial copy was pulled before the recorder had exited and failed decoding
(`InvalidDataError`). Retained locally as an incomplete transfer, not a second
original or accepted clip. After process completion, a separately named copy
decoded successfully; its hash above identifies the finalized source.

## One manual low-pull shot

Original SHA-256:
`dfb0112ae09c27f2ca7dab1202043906999cdb76383888c08a9890b0cacc2cfb`.
5,674 frames, presentation times 0–94.700533333s. Reviewed hold/motion interval
66–72.8s: maximum gap 0.019844444s, zero gaps >0.05s. Broader motion/reset interval
71–73.4s: maximum gap 0.0336s, zero gaps >0.05s. Whole clip contains one 0.0612s
gap at 59.314644444–59.375844444s, before the shot; it is retained, not erased.

The striker travels through the clear right lane; no rail or disc contact was
seen. The rack remains undisturbed. A narrow, unvalidated map-pin colour feature
changes between 71.547522222s and 71.565755556s; this is an observation bracket,
not a known impulse time or calibrated body centre. It approaches a terminal
region by about 72.73s. The feature repeats over approximately 72.732555556–
72.797511111s, then changes by 72.815511111s; by 72.848455556s the visible reset
motion is unmistakable. Sprite/animation uncertainty prevents treating those
feature repeats as certified physical rest. The following trip through the
off-board region and return to the other baseline is reset animation, not a bounce.

**The required one second of corroborated stationary rest is unavailable.** This
shot therefore cannot pass the existing protocol despite good encoded motion
timing. No restitution, stopping coefficient, launch speed or true rest time is
accepted. There is no independent second annotation pass or production replay.

## Consequence for collection

Controlled matrix attempts: 0. Sealed heldouts: 0. Inspected diagnostics cannot
later be relabelled untouched validation. Keep `controlled-v1/plan.json`, its
allocation and all existing gates unchanged. Do not ask for a full repeat batch
until the reset limitation is resolved.

Before formal acquisition, establish a visible terminal-event observation
contract that separates dynamics from reset animation, with explicit uncertainty
and failure rules. If genuine rest remains hidden, mark it unavailable. Any
revised scope/metric requires a separately versioned protocol frozen before new
collection and fresh validation; the current shot must not be retroactively made
to pass by shortening its rest requirement. Effective equipment metadata, complete
input measurement rules and isolated-contact/pocket feasibility also remain open.
CB-39 stays incomplete; live recommendations and CB-40/41 remain blocked.

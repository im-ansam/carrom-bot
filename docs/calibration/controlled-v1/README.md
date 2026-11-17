# CB-39 precollection contract (work in progress)

This directory is acquisition preparation, **not a calibration profile**. No new
phone evidence has arrived. No fit, uncertainty estimate, independent validation,
production replay or usable recommendation assertion exists. CB-39 remains open;
CB-40/41 cannot advance from these files. The historical CB-37/38 artifacts remain
unchanged and cannot supply untouched validation.

`plan.json` uses `cb39-acquisition-v1`; `audit.json` uses
`cb39-acquisition-audit-v1`. The audit binds canonical UTF-8 JSON (sorted keys,
compact separators) with SHA-256. It never emits `usable=true`. The Python
validator is the executable acquisition schema; unknown keys are rejected. This
is not the eventual fitted-profile schema or a production import contract.

The matrix implements the [predeclared protocol](../controlled-phone-protocol.md).
Attempt IDs are allocated in interleaved repetition rounds. A cells 1–4 mean
nominal 20/40/60/80 percent of observed graphic range, never normalized game power.
B cells 1–6 enumerate nominal offsets 0/0.45/0.75, each with low/high distinguishable
input. C cells 1–6 enumerate centre/inner-rim/outside-mouth lanes, each low/high.
Actual achieved geometry/input and uncertainty still need separate annotations.
Repetitions 1/2 are development, 3/4 sealed validation. Start only Stage A (16
shots); do not collect B/C until its clock/proxy/measurement gates pass. Banks and
optional D are outside this manifest's initial direct/cut scope.

Before recording, confirm authorized private/local arrangement feasibility and
complete every metadata field, including the full input-sweep hash and declared
graphic measurement rule. JSON null means unknown; strings must be confirmed
sanitized values, not plausible defaults. Commit/freeze the completed manifest
before collection; the checked-in unknown template is not that freeze. Record
one original per attempt to keep sealed custody separate in this first version.
If the capture workflow cannot do this, revise/freeze the protocol before shooting.
Keep validation originals unopened by the fitting analyst. A custodian can hash
them without revealing trajectories. Opening for annotation repair loses freshness.

Retain every attempt, including technical exclusions with reasons. Never replace
IDs or reassign splits after seeing outcomes. Each recorded/excluded attempt needs
a distinct lowercase SHA-256 of its external unmodified original. This audit checks
digest syntax/reuse, not file bytes, clock integrity, exclusion correctness or
privacy. Original verification and the two independent annotation passes remain
required work. Do not put paths, media, screenshots or account data in these files.

```powershell
py tools/calibration/acquisition.py --new --output .validation/phone-plan.json
py tools/calibration/acquisition.py --plan .validation/phone-plan.json --output .validation/phone-audit.json
py -m unittest discover -s tools/calibration -v
```

The audit command exits successfully for a structurally valid but blocked plan;
consumers must inspect `usable`, `status` and `blockers`. Malformed contracts fail
with a nonzero exit. Deterministic output contains no wall-clock timestamps.

Next work requires confirmed metadata/feasibility, then the sweep and Stage A
acquisition with sealed custody. Numeric annotation, finite bounded identifiable
parameters, explicit uncertainty/unknowns, consistent BW/reference-unit conversion,
frozen model/code hashes and per-shot production-simulator replay must be designed
and validated before opening new holdouts. Missing/failed gates cannot become zero
errors. No speculative production dynamics change is justified yet.

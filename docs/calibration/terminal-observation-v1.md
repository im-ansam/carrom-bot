# Terminal observation v1: motion censored by reset

2026-11-21, CB-39. This is an offline observation contract, not a replacement
acceptance protocol or usable profile. The existing controlled-phone v1 matrix,
split, one-second rest requirement and validation gates remain unchanged.

The [phone diagnostic](phone-diagnostics-v1/README.md) has smooth pre-reset motion
but no confirmed one second of stationary rest. The short apparent plateau cannot
distinguish slow motion, repeated display positions and sprite animation. The
later return through the off-board area must never become a rebound, drag sample
or rest position. The inspected diagnostic remains development evidence only.

## Annotate the transition before evaluating a model

1. Verify the immutable original hash and original ordered PTS/time base. Work on
   the complete native frame, with body identity, rails, neighbouring discs and
   turn UI visible. A cropped colour marker is a review aid, not a body centre.
2. Prepare two separate annotation passes, each without seeing fitted residuals
   or the other pass's decision. Record method/reviewer independence and supporting
   frame evidence separately; different IDs alone do not establish independence.
3. For each pass, inspect consecutive frames around the first uncertain change.
   Label the last confidently physical frame and the immediately following first
   uncertain/nonphysical frame using exact original integer PTS. Use cause
   `reset`, `occlusion` or `ambiguous`. Unknowns stay ambiguous. Include obscured
   bodies or unexplained displacement; do not wait for the most obvious teleport.
4. A turn UI change, body fading/resizing, an off-board return or other independent
   visual evidence can support reset classification. Speed/direction change alone
   cannot: a genuine rail/body collision is physical motion and must be checked
   against actual geometry. No detector or UI clock is established here.
5. Union the two transition brackets: earliest lower endpoint through latest upper
   endpoint. Never average disagreements. Exclude both endpoints and the entire
   subsequent tail from motion fitting, even if a striker later reappears on-board.
   Keep excluded PTS/imagery in the original for audit; do not alter or splice it.

The conservative retained prefix ends **strictly before** the earliest lower
endpoint. A bracket spanning more than 0.05s fails event precision. Any encoded
gap >0.05s before that endpoint also blocks prefix scoring; the gap ending at the
excluded boundary is checked. The prefix is only a temporal candidate: missing
centres, uncertain identity, incorrect transforms and unlabelled contacts still
block fitting. Neither encoded timestamps nor a frame bracket prove an engine
transition instant. No interpolation, synthetic samples or extrapolated stopping
points are allowed.

## Executable contract

`tools/calibration/terminal_observation.py:censor_prefix(timing, annotations)`
uses the standard library. `timing` supplies an original capture report's `sha256`,
`pts` and `time_base`. The strict annotation object is:

```json
{
  "schema": "cb39-terminal-boundaries-v1",
  "source_sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "passes": [
    {"id": "pass-a", "last_physics_pts": 5, "first_nonphysics_pts": 6, "cause": "reset"},
    {"id": "pass-b", "last_physics_pts": 6, "first_nonphysics_pts": 7, "cause": "reset"}
  ]
}
```

This is an original synthetic example with PTS 0–9 and time base 1/100: retain
0–4, exclude 5–9 and report transition bounds [5,7] (0.02s). A real report needs
actual adjacent source PTS, source verification and separately stored annotation
evidence. The tool rejects missing/duplicate passes, source mismatch, invented or
nonadjacent boundaries and invalid clocks. It reports disagreement without hiding
it. It cannot verify human independence or classify pixels; outputs explicitly
include those limitations and always have `usable=false`, `rest_status=unobserved`
and `stop_time_s=null`. Even a long repeated-position prefix cannot change that.
The annotation hash binds the supplied record, not proof of its truth.

## Capabilities and next evidence

| Quantity | Treatment when reset truncates observation |
| --- | --- |
| Visible pre-reset trajectory | Candidate observations only, with original PTS, two body-centre passes, uncertainty and contact labels still required. |
| Launch mapping / drag / rail response | May be studied offline using qualified development prefixes; each needs sufficient independent excitation and identifiability. No parameters accepted by this tool. |
| Stopping time and resting position | Unobserved. Last visible position or reset onset is not a stopping event. No v1 rest pass. |
| Contact / pocket outcome | Needs separate visible identities/events; an outcome hidden by reset is unavailable, not a miss or success. |
| Game profile / live recommendations | Blocked until all applicable independently validated capabilities and production replay pass. |

Before requesting more shots, finish full-range power-graphic measurement and
effective equipment scope, and independently annotate the saved terminal frames.
If rest remains unobservable, the next feasibility question is whether a target
disc stays visible and stationary across turn changes after an isolated contact.
That is an untested possibility, not evidence for shared striker/disc drag or a
reason to call a rack break isolated. Check existing authorized development media
first; only then request a specifically defined manual setup if feasible.

Any future collection that accepts visible-motion-only evidence needs its own
frozen scope, IDs/splits, metrics and capability limits before new shooting.
It cannot populate a passing full v1 profile or retroactively validate these
diagnostics. A genuine rest capability still requires observable independent
evidence; no assumption about the reset threshold substitutes for it.

# ADR 0016: Direct and cut contact geometry

Status: Proposed for CB-26 review, 2026-11-15.

DirectShotGenerator and CutShotGenerator generate one caller-identified proposal
per request. Callers enumerate desired disc/pocket pairs, aim points, normalized
powers and discrete legal striker positions. Exact membership in the supplied
legal-position list is required. This boundary deliberately owns no player rules,
baseline dimensions, sampling policy or guessed calibration. Repeated requests
retain the caller ID; callers must allocate unique IDs within evaluation batches.
All processing is synchronous and stateless, without clocks, randomness or resources.

The scene must be stationary, with separated active discs outside capture apertures.
The original striker position is replaced in a new immutable BoardState; its radius
and identity are retained. The request selects a disc-centre aim within a full-fit
aperture eroded by the disc radius and explicit clearance margin. The ghost centre
is behind the disc by the sum of radii along that aim direction. ContactGeometry
exposes ghost centre, common surface contact, aim and first margin-aperture entry
in shared board millimetres. Positive approach projection onto the disc direction
is mandatory; grazing, back-facing and zero-length launches are rejected. Direct
means lateral approach offset <= caller alignmentTolerance (mm); cut is the
complement. This tolerance partitions families without changing contact geometry.

Both ends of the striker corridor must fit inside the rectangular board. The disc
must reach the margin-aperture entry while still fitting inside the board: rails
remain solid until capture under ADR 0008. Segment-to-circle distance rejects other
discs in either corridor, including exact tangency and the clearance margin.
Illegal/outside/overlapping striker placements, pre-impact striker captures and
wrong-pocket interceptions are rejected. These conservative guards can discard
shots that a less conservative or curved physical model would allow. The target
disc is excluded only from corridor obstacle checks: positive normal projection
ensures the ghost is the first contact on the launch ray. Existing striker position
does not act as an obstacle after repositioning. No Android or module edge changes.

PowerModel supplies launch speed from explicit normalized power. Geometry alone
does not establish sufficient energy, completion time, post-contact striker route,
capture or absence of fouls. Every proposal still needs ShotSimulator evaluation
with caller-calibrated masses, restitution, drag and budgets. Candidate constraints
allow exactly one body impulse and zero wall impulses over the entire outcome;
these intentionally also reject post-contact banks/secondary collisions. No power
search, optimizer, confidence or real-game accuracy claim is introduced.

Null explicitly denotes infeasible geometry or a different family. Malformed
requests, moving/overlapping scenes and nonrepresentable vector arithmetic throw;
exceptions and cancellation are never swallowed. Double precision and scale limits
remain those of shared models. The generators process one finite request with work
quadratic in disc count for scene validation and linear in discs/pockets for paths.
Input lists are read synchronously and never retained. Returned state is immutable.

Tests cover analytic straight contact, angled cuts, grazing/back-facing approaches,
blocked/tangent/clear corridors, placement legality and ownership, aperture margin,
rails before capture, wrong-pocket/foul interception, invalid/moving inputs and a
generated proposal through real simulation/evaluation. See validation-cb-26.md.

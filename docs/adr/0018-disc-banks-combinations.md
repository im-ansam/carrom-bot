# ADR 0018: Bounded disc banks and contact chains

Status: Proposed for CB-28 review, 2026-11-15.

CB-28 adds DiscBankGenerator and CombinationShotGenerator to core:planner. The
module graph remains planner -> physics/model. Both reuse ContactShotRequest and
ContactShotConfig, with caller-owned candidate IDs, exact discrete legal placements,
normalized power and explicit calibration. The synchronous APIs retain no caller
lists, resources, clocks, randomness, Android or native objects.

DiscBankGenerator enumerates four single rails, then twelve distinct ordered pairs
in BankWall enum order. ReflectionPathGenerator proposes target-centre paths on
rails inset by the target disc radius. Repeated rails, rail starts, wrong rail order
and simultaneous corners are rejected. Aim must fit strictly inside the inset
rectangle as well as the pocket aperture eroded by disc radius and clearance. This
conservative restriction requires callers to select an interior aim when pocket
centres lie on a board edge. Ghost construction uses the first outgoing disc leg;
the striker launches toward that ghost with a positive normal projection.

CombinationShotGenerator enumerates a deterministic prefix of distinct two-to-four
disc sequences ending at the requested target. Shorter routes precede longer routes;
within a length, board disc storage order defines traversal. maxSequences is an
explicit 1..256 returned-route budget. This prefix need not include every feasible
route. Enumeration returns immutable nested lists of intent identities, not accepted
shots. Callers allocate unique IDs and submit selected sequences to generate;
placements, aim and power search remain caller responsibilities.

Backward construction starts at the final pocket aim. For each disc, its desired
travel ends at the ghost behind the next disc along that next disc's outgoing
normal. The previous moving body's radius determines each sum-of-radii offset.
This permits cut transfers/caroms with residual motion. No independent impulse or
energy model predicts that residual; the actual simulator determines it.

Shared internal SequenceGeometry requires stationary, separated discs outside
capture apertures, nonoverlapping legal striker placement, contained endpoint
circles and clear swept-circle corridors. Tangency plus caller clearance is blocked.
Only the moving disc and its intended next contact are excluded from a chain leg's
obstacle check. Pocket interception is checked on every leg, permitting only the
requested pocket on the final leg. Corridor checks extend to the supplied aim,
which can conservatively reject an obstruction beyond actual capture. Original
striker position is replaced in a fresh immutable BoardState.

Every proposal runs through the existing ShotSimulator with explicit PhysicsConfig.
Accept only REST without striker capture, requested disc capture in the requested
pocket, and exactly the intended body/wall impulse identities and order over the
whole result. Body pairs match without assuming simulator pair orientation. Disc
banks require striker-target then ordered target-wall impulses; combinations require
the striker/disc chain and no walls. Extra collisions, including with a stopped
striker on a return leg or with residual carom motion, reject. Candidate constraints
also record exact inclusive counts for ordinary ShotEvaluation. Both one/two target
banks use DISC_BANK; chain transfers use COMBINATION. ContactSequenceShot owns its
ordered disc IDs and retains the actual immutable simulation and candidate.

Null denotes infeasible geometry or a rejected physical outcome. Invalid requests,
unknown/duplicate chain identities, nonrepresentable arithmetic and simulator work
failures throw. No exception or cancellation is caught. Work is bounded by the
route-length/sequence limits, finite scene sizes and explicit simulator budgets;
scene nonoverlap checks remain quadratic. No loss-compensated angle search,
optimization, rule inference, real-game accuracy or performance claim is introduced.

Synthetic tests cover analytic oblique disc-bank event times at coarse/fine clocks,
two rails, two/three/four-disc chains, cut-transfer residual stopping, exact corner
rejection, blockers/tangency, early capture, illegal placement, immutable ownership,
sequence bounds, invalid scenes, truncation, insufficient power and budget failure.
See [validation](../validation-cb-28.md) for actual commands and limitations.

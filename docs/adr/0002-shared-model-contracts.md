# ADR 0002: Shared geometry, snapshots and prediction data

Status: Proposed for CB-12 review, 2026-11-14.

## Coordinates and precision

All board positions, bounds and radii use millimetres. The origin is the top-left
inner playing surface; positive X points right, positive Y down. Bounds normally
start at (0, 0), but translated rectangles are allowed. Velocities use mm/s and
prediction times use elapsed seconds relative to a shot, never wall-clock time.
Values use finite IEEE 754 Double precision. Constructors and generated data-class
copies validate invariants with IllegalArgumentException. No implicit clamping,
rounding, epsilon or unit conversion is applied. Value equality is exact, including
Double's signed-zero distinction; numerical tests choose their own tolerance.
The transform round-trip fixture uses 1e-10 mm for its tested coordinate range,
not a universal error guarantee. Extreme magnitudes can lose precision or overflow;
nonfinite transform results fail through Vec2 validation.

CoordinateTransform maps an explicit board anchor to a screen-pixel anchor with a
positive uniform pixels-per-millimetre scale. Both axes keep their direction.
This supports cropped and translated views, without Android display types. An
adapter must rectify rotated or perspective views before using this mapping;
it must not pretend such a view is axis aligned. Pixel rounding belongs to rendering.

## Identity, invariants and ownership

Pocket, Disc and Striker IDs are nonblank, case-sensitive caller-assigned strings.
Tracking/fixture producers allocate IDs once and preserve them across snapshots;
the model never generates IDs from positions, list indices, clocks or randomness.
IDs are unique across all three entity types within a BoardState. DiscKind describes
appearance (black, white, queen), not player ownership. Game rules are deferred.

BoardState copies its ordered lists into JVM unmodifiable collections. Elements
are immutable values, so neither original lists nor mutable JVM views can change a
published snapshot. The model owns no bitmap, native resource, Android object,
lifecycle or coroutine scope. Ordered list equality is deliberate: producers must
choose and maintain stable iteration order. Snapshots with different list orders
are different values, even if they describe the same geometric arrangement.
Scalar contracts are data classes; collection-bearing contracts are ordinary final
classes with value equality, avoiding generated copy methods that bypass collection
ownership. java.util.Collections is available on the minimum Android API and JVM;
there are no added dependencies.

Bounds have finite positive width/height. Edges and tangent bodies are inclusive.
Active discs and the striker must fit fully within bounds; pocket centres must be
inside/on the rectangle, while aperture rims may cross an edge. No fixed board size,
pocket count, disc count, nonoverlap, player rules or confidence score is invented.
Removed discs are absent from subsequent snapshots. Vision must explicitly report
failed or invalid observations through its future adapter contract, not manufacture
a plausible BoardState.

## Shot and prediction boundary

ShotInput owns a reference to an immutable input BoardState with the striker already
positioned. Its finite nonzero vector is the requested post-impulse velocity in mm/s;
it is not device power or a gesture. Calibration, legal placement and impulse physics
are future responsibilities. PredictionFrame pairs elapsed seconds with a snapshot.
ShotPrediction is successful data with a nonempty copied frame list starting at the
exact pre-impulse input snapshot at zero seconds. Later times strictly increase.
Board/pocket geometry and surviving entity IDs, kinds and radii remain unchanged;
discs may disappear but cannot reappear. The striker stays present, including after
a simulated foul; foul/pocket events require a later explicit contract.

REST and TIME_LIMIT are producer-declared termination reasons; TIME_LIMIT preserves
the distinction between a completed and a truncated prediction. These contracts do
not verify trajectory accuracy or infer pocketing from disappearance. Failed producers
must report failure separately, not return an empty or fabricated prediction. No
physics, planning, vision, capture or overlay behavior is implemented in CB-12.

## Fixtures and integration

All consumers already depend on core:model in the module graph. New public contracts
live in io.github.carrombot.core.model under explicit API mode. The JVM build compiles
the model and checks its downstream dependency graph without the Android SDK.
Actual adapter consumption will be implemented in those modules' feature tickets.

board-v1.properties is a versioned, synthetic full-state test fixture. Tests reconstruct
public contracts from explicit fields and compare against an independent expected
snapshot, including IDs, geometry, classification and velocity. This is fixture
compatibility evidence, not a production serialization format. A wire/storage codec
and schema migration policy are deferred until a consumer needs persistence; Kotlin
or Java object serialization is not implicitly promised.

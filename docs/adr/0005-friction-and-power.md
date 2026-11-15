# ADR 0005: Constant drag and linear striker power

Status: Proposed for CB-15 review, 2026-11-15.

CB-15 extends the platform-free physics foundation from ADR 0004. It does not add
contacts, wall response, pocketing, planning or Android behavior. Shared model and
prediction invariants remain unchanged. Callers still need collision-free horizons;
leaving board bounds fails explicitly rather than producing a fabricated response.

## Drag approximation and integration

FrictionModel uses one finite positive constant deceleration a, in mm/s², as an
isotropic approximation for rolling/sliding losses. It does not distinguish sliding
and rolling phases, spin, material, mass or speed-dependent drag. The coefficient
requires calibration against measurements; it has no claimed game-specific accuracy.

For finite nonnegative initial speed v, stopping time is v/a seconds and stopping
distance is v²/(2a) mm. Over a step dt, the moving time is min(dt, v/a), final speed
is max(0, v-a*dt), and travel is the average initial/final speed times moving time.
Direction stays fixed until rest. This integrates the moving part of a step exactly
for the chosen approximation instead of moving for a full step after stopping.
Velocity never reverses due to drag. hypot avoids premature squaring overflow;
expressions use half-speeds to avoid unnecessary intermediate overflow. Results
that cannot be represented as finite Doubles throw IllegalArgumentException.
Ordinary IEEE 754 rounding and subnormal underflow still apply.

PhysicsConfig.deceleration is finite and nonnegative. Zero is an explicit no-drag
mode and remains the default to preserve the CB-14 free-motion fixtures and callers.
Positive values configure FrictionModel for both discs and striker. There is no
unmeasured production drag default. Simulator snapshots, ordering, input ownership,
fixed dt, integer-indexed clock and iteration cap remain as in ADR 0004.

Bodies stopping inside an interval hold position for its remainder. The recorded
clock still reaches the full fixed-step boundary. Rest requires exactly zero velocity;
no arbitrary epsilon silently discards slow movement. Repeated direction normalization
can leave a rounding residual at the analytic stop tick and delay REST by a following
sample. Tests use position tolerances and a small timestep-scaled stop-time envelope.
Terminal velocity is normalized to positive zero in both components. TIME_LIMIT
continues to distinguish truncated moving predictions; REST now occurs for launched
shots with drag once every active body has stopped.

## Power calibration boundary

PowerModel takes an explicit finite positive maximum speed in mm/s. The normalized
power p in [0,1] maps linearly to p*maximumSpeed, with inverse speed/maximumSpeed.
This is the smallest monotonic invertible calibration model; no speculative exponent
or device gesture conversion is introduced. Out-of-range and nonfinite values fail,
not clamp. Endpoints are exact; intermediate round trips have Double precision limits,
especially subnormal underflow. Changing maximumSpeed changes calibration without
changing shared ShotInput. Callers multiply speed by an aiming unit vector before
constructing a nonzero launch; tests exercise an axis-aligned launch.

Zero power returns zero speed and means no launch. ShotInput deliberately rejects
zero initial velocity; PowerModel does not weaken that model contract or manufacture
a nonzero shot. UI, planning and device gesture behavior belong to later tickets.

## Verification

Versioned drag-v1 stores a seed, drag coefficient and analytic tolerance. Tests cover
seeded analytic motion/stopping distance and repeatability, monotonic distance and
power mapping, inverse lookup across calibration scales, zero/maximum power, invalid
settings and nonrepresentable results, zero intervals/speeds, large/tiny values,
split integration and timestep sensitivity. Integration tests cover discs/striker,
REST vs TIME_LIMIT, original pre-impulse snapshot and PowerModel to ShotInput.
The unchanged CB-14 fixture remains a no-drag regression.

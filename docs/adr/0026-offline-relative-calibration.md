# ADR 0026: Relative offline measurement without a live profile

Accepted for CB-37.

User-authorized recordings remain external read-only inputs. SHA-256, presentation
timestamps, manually reviewed numeric geometry, exclusions and uncertainty are the
versioned evidence. Exact encoded frames and distinct game updates are different
observations; display timing cannot be inferred from nominal fps. A narrow optional
PyAV/Pillow reader measures the supplied striker appearance; it is not a live vision
adapter. Standard-library Python performs deterministic fitting/reporting, with
synthetic regression fixtures independent of private media.

One board width (BW) is the distance between inner left/right rails. The measured
square uses top-left origin, +x right/+y down, seconds and BW/s/BW/s². Physical
board mm, game version and equipment remain unknown. A test-only Kotlin replay
converts 1 BW to **1000 arbitrary reference units** in existing mm-labelled contracts,
scaling positions, radii, velocities and drag together. This is a dimensional
adapter for comparing the production simulator, not a physical dimension estimate.
Shared contracts and the production dependency graph do not change.

The first two wall repetitions per input label fit; the third is held out. Targets
and parameter bounds precede fitting. The linear input is visible arrow length/BW,
not normalized game power. Normal loss is fitted conditionally; tangential loss,
wall friction, mass ratio, body restitution and pocket parameters are unidentifiable
from these near-normal rails and occluded breaks. Replay explicitly assumes tangent
retention 1 and wall friction 0 to test the existing model; neither is calibrated.

Current held-out errors fail exploratory targets. A fitted bound is not evidence of
a physical coefficient. Deliver a rejected-for-use analysis artifact and precise
limitations rather than a plausible live profile. No live import, pixel processing,
aiming enablement or synthetic-default substitution is added. Any future live
profile needs a separate acquisition/integration design and independent validation.

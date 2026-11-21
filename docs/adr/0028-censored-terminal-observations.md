# ADR 0028: Exclude uncertain terminal transitions from offline motion evidence

Accepted for CB-39 observation tooling, 2026-11-21. No production module changes.

The physical-phone diagnostic records slowing followed by an automatic return to
the opposite baseline, without the protocol's required one second of visible
stationary rest. Treating the return as a rebound or its onset as an exact stop
would introduce observations that were never measured.

Use two separately prepared manual boundary annotations on immutable original
PTS. Retain only samples strictly before the earliest last-physical frame; exclude
the union transition bracket and all later samples. Report disagreement and clock
gaps. A small standard-library partitioner hashes the supplied annotations and
preserves the original input; it cannot verify independence or pixel labels.

The result is a censored motion prefix with unobserved stopping state, always
diagnostic-only and unusable as a game profile. Missing rest cannot be inferred
from a plateau, extrapolated model or reset threshold. Existing v1 acquisition
and acceptance requirements stay unchanged; future limited-scope protocols need
separate preregistration and fresh validation. No production drag, model units,
Android detector, live importer or synthetic defaults change.

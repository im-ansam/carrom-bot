"""Offline censoring of manually annotated reset/occlusion boundaries.

This partitions original encoded PTS only. It detects no pixels or game events,
verifies no annotation independence and estimates no stopping state or physics.
Two separately prepared passes are required; their earliest uncertain boundary
is excluded along with the entire tail, including any apparent return to play.
"""
from fractions import Fraction
import hashlib
import json
import re

if __package__:
    from .capture_timing import assess
else:  # unittest discovery imports tools as top-level modules.
    from capture_timing import assess


def censor_prefix(timing, annotations):
    """Return a diagnostic-only partition without changing either input.

    Each pass supplies adjacent original last-physics/first-nonphysics PTS and a
    cause (reset, occlusion, ambiguous). The union is the transition bracket;
    retain only samples strictly earlier than its lower endpoint. The endpoint
    itself is excluded because transition onset may lie in that encoded bracket.
    Human review must establish phase/body identity independently of model error.
    Frame timestamps bound observations, not an exact engine transition time.
    """
    if not isinstance(timing, dict) or not isinstance(annotations, dict):
        raise ValueError('Timing and annotations must be objects')
    expected = {'schema', 'source_sha256', 'passes'}
    if set(annotations) != expected or annotations['schema'] != 'cb39-terminal-boundaries-v1':
        raise ValueError('Unsupported terminal annotation contract')
    digest = timing.get('sha256')
    if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
        raise ValueError('Original source SHA-256 required')
    if annotations['source_sha256'] != digest:
        raise ValueError('Annotation source differs from timing source')
    pts, clock_text = timing.get('pts'), timing.get('time_base')
    if not isinstance(pts, list):
        raise ValueError('Original PTS list required')
    assess(pts, clock_text, [])  # Reject missing/repeated/reversed clock samples.
    clock = Fraction(clock_text)
    passes = annotations['passes']
    if not isinstance(passes, list) or len(passes) != 2:
        raise ValueError('Exactly two separate annotation passes required')
    indices = {p: i for i, p in enumerate(pts)}
    names, bounds, causes = set(), [], []
    for entry in passes:
        if not isinstance(entry, dict) or set(entry) != {
                'id', 'last_physics_pts', 'first_nonphysics_pts', 'cause'}:
            raise ValueError('Unexpected annotation fields')
        name = entry['id']
        if (not isinstance(name, str) or not re.fullmatch('[a-zA-Z0-9_-]{1,80}', name)
                or name in names):
            raise ValueError('Distinct sanitized pass IDs required')
        names.add(name)
        lo, hi = entry['last_physics_pts'], entry['first_nonphysics_pts']
        if (type(lo) is not int or type(hi) is not int or lo not in indices
                or hi not in indices or indices[hi] != indices[lo] + 1):
            raise ValueError('Boundary must name adjacent original integer PTS')
        if entry['cause'] not in ('reset', 'occlusion', 'ambiguous'):
            raise ValueError('Unsupported censoring cause')
        bounds.append((lo, hi))
        causes.append(entry['cause'])
    lower, upper = min(p[0] for p in bounds), max(p[1] for p in bounds)
    cutoff = indices[lower]
    width = (upper - lower) * clock
    prefix_gaps = [(b - a) * clock for a, b in zip(pts[:cutoff], pts[1:cutoff + 1])]
    blockers = ['rest_not_observed', 'annotation_independence_not_verified',
                'body_centres_not_verified', 'profile_validation_missing']
    if bounds[0] != bounds[1] or causes[0] != causes[1]:
        blockers.append('annotation_disagreement')
    if width > Fraction(1, 20):
        blockers.append('transition_bracket_over_50ms')
    if any(gap > Fraction(1, 20) for gap in prefix_gaps):
        blockers.append('prefix_gap_over_50ms')
    if cutoff < 2:
        blockers.append('insufficient_prefix')
    annotation_hash = hashlib.sha256(json.dumps(
        annotations, sort_keys=True, separators=(',', ':'), allow_nan=False
    ).encode('utf-8')).hexdigest()
    return dict(schema='cb39-censored-prefix-v1', status='diagnostic_only_no_profile',
                usable=False, source_sha256=digest, annotation_sha256=annotation_hash,
                time_base=str(clock), transition_bounds_pts=[lower, upper],
                transition_width_s=float(width), retained_pts=pts[:cutoff],
                excluded_pts=pts[cutoff:], rest_status='unobserved', stop_time_s=None,
                stop_position_bw=None,
                blockers=blockers)

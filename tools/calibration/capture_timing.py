"""Read-only encoded-PTS diagnostics; a timing pass is never a profile pass.

Scoring is standard library only. Media inventory additionally needs the pinned
PyAV dependency in requirements-media.txt. No frames or source paths enter JSON.
"""
import argparse
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import re


def assess(pts, time_base, intervals):
    """Audit every encoded gap intersecting each manually declared interval.

    Boundary-crossing gaps are retained. PTS are not interpolated, deduplicated,
    or equated with distinct game updates. All comparisons use rational seconds.
    Intervals describe source presentation time, not an inferred impulse clock.
    """
    if not isinstance(time_base, str):
        raise ValueError('Time base must be an explicit rational string')
    try:
        clock = Fraction(time_base)
    except (ValueError, ZeroDivisionError) as error:
        raise ValueError('Invalid time base') from error
    if clock <= 0:
        raise ValueError('Time base must be positive')
    if len(pts) < 2 or any(type(p) is not int for p in pts):
        raise ValueError('At least two integer, nonmissing PTS required')
    if any(b <= a for a, b in zip(pts, pts[1:])):
        raise ValueError('Presentation timestamps must strictly increase')
    times = [p * clock for p in pts]
    gaps = [(a, b) for a, b in zip(times, times[1:])]

    def summary(selected):
        widths = [b-a for a, b in selected]
        return dict(max_gap_s=float(max(widths)),
                    gaps_over_50ms=sum(x > Fraction(1, 20) for x in widths))

    scored = []
    names = set()
    for name, start, end in intervals:
        if not isinstance(name, str) or not re.fullmatch(r'[a-zA-Z0-9_-]{1,80}', name) or name in names:
            raise ValueError('Intervals need distinct sanitized IDs')
        names.add(name)
        if any(type(x) not in (int, float) or not math.isfinite(x) for x in (start, end)):
            raise ValueError('Interval bounds must be finite seconds')
        lo, hi = Fraction(str(start)), Fraction(str(end))
        if not times[0] <= lo < hi <= times[-1]:
            raise ValueError('Interval must be ordered and inside the actual capture')
        selected = [(a, b) for a, b in gaps if a < hi and b > lo]
        scored.append(dict(id=name, start_s=start, end_s=end, **summary(selected)))
    return dict(schema='cb39-capture-timing-v1', status='diagnostic_only_no_profile',
                game_update_clock='unknown', frames=len(pts), time_base=str(clock),
                first_s=float(times[0]), last_s=float(times[-1]),
                min_gap_s=float(min(b-a for a, b in gaps)), **summary(gaps),
                intervals=scored, pts=list(pts))


def inventory(source, label, intervals):
    """Hash an external original and decode PTS without writing private imagery."""
    import av
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,80}', label):
        raise ValueError('Sanitized clip label required')
    digest = hashlib.sha256()
    with source.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    with av.open(str(source)) as container:
        if len(container.streams.video) != 1:
            raise ValueError('Exactly one video stream required')
        video = container.streams.video[0]
        pts = []
        for frame in container.decode(video=0):
            if frame.time_base != video.time_base:
                raise ValueError('Changing or inconsistent frame time base')
            pts.append(frame.pts)
        result = assess(pts, str(video.time_base), intervals)
        result.update(clip=label, sha256=digest.hexdigest(),
                      resolution=[video.width, video.height], decoder='PyAV ' + av.__version__)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--label', required=True)
    parser.add_argument('--interval', nargs=3, action='append', default=[],
                        metavar=('ID', 'START_SECONDS', 'END_SECONDS'))
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if (args.source.resolve() == args.output.resolve()
            or (args.output.exists() and args.output.samefile(args.source))):
        parser.error('Output must not replace the read-only original')
    intervals = [(name, float(start), float(end)) for name, start, end in args.interval]
    result = inventory(args.source, args.label, intervals)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + '\n',
                           encoding='utf-8', newline='\n')
    print(json.dumps({k: v for k, v in result.items() if k != 'pts'}))


if __name__ == '__main__':
    main()

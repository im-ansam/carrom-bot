"""Private-media reader. PyAV 19.0.2 + Pillow 13.0.0; no media is written.

This narrow, reviewed colour measurement is specific to the supplied recordings.
It is not a live detector. Output contains only timestamps, hashes and geometry.
"""
import argparse
import hashlib
import json
from pathlib import Path


def marker(image, box):
    from PIL import ImageChops
    crop = image.crop(box)
    r, g, b = crop.split()
    masks = [r.point(lambda x: 255 if x > 130 else 0),
             g.point(lambda x: 255 if x < 105 else 0),
             b.point(lambda x: 255 if x > 45 else 0),
             ImageChops.subtract(r, g).point(lambda x: 255 if x > 65 else 0),
             ImageChops.subtract(r, b).point(lambda x: 255 if x > 45 else 0)]
    mask = masks[0]
    for other in masks[1:]:
        mask = ImageChops.multiply(mask, other)
    bounds = mask.getbbox()
    if bounds is None:
        return None
    x0, y0, x1, y1 = bounds
    # Reject multiple objects/edges and partial/weak marker observations.
    count = mask.histogram()[255]
    if not (6 <= x1-x0 <= 36 and 6 <= y1-y0 <= 42 and count >= 25):
        return None
    return [round(box[0]+(x0+x1-1)/2, 2),
            round(box[1]+(y0+y1-1)/2+5, 2), count]


def extract(source):
    import av
    sha = hashlib.sha256()
    with source.open('rb') as stream:
        for block in iter(lambda: stream.read(1024*1024), b''):
            sha.update(block)
    rows = []
    repeated = 0
    last_digest = None
    with av.open(str(source)) as container:
        video = container.streams.video[0]
        time_base = str(video.time_base)
        for index, frame in enumerate(container.decode(video=0)):
            if frame.pts is None:
                raise ValueError('Missing frame PTS')
            im = frame.to_image().convert('RGB')
            # Exact board crop repeat count is diagnostic, not a gameplay clock.
            digest = hashlib.sha256(im.crop((20, 490, 700, 1170)).tobytes()).digest()
            duplicate = digest == last_digest
            repeated += duplicate
            last_digest = digest
            rows.append({'frame': index, 'pts': frame.pts,
                         't': float(frame.pts * frame.time_base),
                         'board_repeat': duplicate,
                         'bottom': marker(im, (30, 1020, 690, 1100)),
                         'top': marker(im, (30, 565, 690, 645)),
                         'break_approach': marker(im, (315, 940, 410, 1100)) if source.stem.startswith('break') else None})
    gaps = [b['t']-a['t'] for a,b in zip(rows, rows[1:])]
    if any(dt <= 0 for dt in gaps):
        raise ValueError('Nonmonotonic presentation timestamps')
    return {'version': 1, 'clip': source.stem, 'sha256': sha.hexdigest(),
            'time_base': time_base, 'resolution': [video.width, video.height],
            'frames': len(rows), 'first_t': rows[0]['t'], 'last_t': rows[-1]['t'],
            'min_gap_s': min(gaps), 'max_gap_s': max(gaps),
            'exact_board_repeats': repeated, 'rows': rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = extract(args.source)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps({k:v for k,v in result.items() if k != 'rows'}))


if __name__ == '__main__':
    main()

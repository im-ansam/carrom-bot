"""Build reviewed v1 annotations from numeric extractor output (no media needed).

Windows/arrow lengths are manual annotations for this dataset, not detections or
ground truth. Change version and review frames again if any annotation changes.
"""
import argparse
import json
import math
from pathlib import Path


# release display PTS, last usable PTS, lane, horizontal launch sign,
# reviewed saturated arrow shaft+head extent in pixels, arrow review frame PTS
ANNOTATIONS = {
    'wall-low': [(8.651544, 10.42, 'bottom', -1, 80, 8.635222),
                 (17.671644, 19.30, 'top', 1, 76, 17.637844),
                 (25.740111, 27.30, 'bottom', 1, 70, 25.707400)],
    'wall-medium': [(5.263578, 7.55, 'bottom', -1, 108, 5.230267),
                    (15.600478, 18.20, 'top', 1, 118, 15.567700),
                    (27.787078, 30.23, 'bottom', -1, 112, 27.753989)],
    'wall-maximum': [(9.849944, 12.52, 'bottom', -1, 128, 9.817356),
                     (19.100256, 21.85, 'top', -1, 128, 19.066989),
                     (29.550500, 30.45, 'bottom', -1, 128, 29.500267)],
}

BREAK_ANNOTATIONS = {
    'break-min': (5.028411, 5.1451, 66, 4.996411, [5.211689,5.261444]),
    'break-medium': (14.583900, 14.6669, 98, 14.550067, [14.700200,14.733456]),
    'break-max': (5.861900, 5.9121, 128, 5.845289, [5.912044,5.945267]),
}


def build(directory):
    inventory, shots, breaks = [], [], []
    for name in ('wall-low', 'wall-medium', 'wall-maximum', 'break-min', 'break-medium', 'break-max'):
        raw = json.loads((directory/('cb37-'+name+'.json')).read_text(encoding='utf-8'))
        info = {k:v for k,v in raw.items() if k != 'rows'}
        info['disposition'] = 'three reviewed wall shots' if name.startswith('wall') else 'one fresh-rack break, exploratory only'
        inventory.append(info)
        if name in BREAK_ANNOTATIONS:
            release,end,arrow,arrow_t,contact_bracket = BREAK_ANNOTATIONS[name]
            samples = [[r['t'],(r['break_approach'][0]-20)/680,(r['break_approach'][1]-490)/680]
                       for r in raw['rows'] if release <= r['t'] <= end and r.get('break_approach') and r['break_approach'][2] >= 300]
            breaks.append({'id': name+'-1', 'split': 'exploratory_not_fit', 'release': release,
                           'release_uncertainty_s': .0334, 'usable_until': end,
                           'start': [(359.5-20)/680,(1062.5-490)/680], 'direction': [0,-1],
                           'pullback': arrow/680,
                           'pullback_annotation': {'frame_t':arrow_t,'arrow_length_px':arrow,'uncertainty_px':5},
                           'first_rack_contact_display_bracket_s': contact_bracket,
                           'samples': samples,
                           'exclusions': ['Before reviewed release: aiming/menu/reset animations',
                                          'After approach: marker occlusion by rack, no reliable disc identities or collision trajectories',
                                          'No mass/body restitution/pocket parameter fitting; no scored pocket inference']})
        for i, spec in enumerate(ANNOTATIONS.get(name, []), 1):
            release, end, lane, sign, arrow, arrow_t = spec
            points = [[r['t'], r[lane][0], r[lane][1]] for r in raw['rows']
                      if release-1e-6 <= r['t'] <= end and r[lane] is not None]
            candidates = [r for r in raw['rows'] if release-1e-6 <= r['t'] <= end]
            if not points:
                raise ValueError('No measured trajectory')
            # Start is the reviewed baseline centre; direction from first .15s
            # after release, using measured x/y displacement, not an exact-horizontal assumption.
            start = [359.5, 597 if lane == 'top' else 1062.5]
            early = [p for p in points if .05 < p[0]-release < .15]
            dx = sum(p[1]-start[0] for p in early)
            dy = sum(p[2]-start[1] for p in early)
            length = math.hypot(dx, dy)
            direction = [dx/length, dy/length]
            if direction[0]*sign <= 0:
                raise ValueError('Annotated direction disagrees with measurements')
            events = [{'type': 'release_proxy', 't': release, 'uncertainty_s': .0334}]
            # A reversal needs substantial displacement on both sides over ~0.1s.
            last_contact = -1
            for j in range(4, len(points)-4):
                before, current, after = points[j-4], points[j], points[j+4]
                d1, d2 = current[1]-before[1], after[1]-current[1]
                if d1*d2 < 0 and min(abs(d1), abs(d2)) > 8 and current[0]-last_contact > .15:
                    window = points[j-3:j+4]
                    contact = min(window, key=lambda p:p[1]) if d1 < 0 else max(window,key=lambda p:p[1])
                    # Keep actual sampled extrema, not idealised rail coordinates.
                    events.append({'type': 'wall', 't': contact[0],
                                   'position': [(contact[1]-20)/680, (contact[2]-490)/680],
                                   'uncertainty_s': .0334, 'position_uncertainty_bw': 5/680})
                    last_contact = contact[0]
            # Rest is a display-resolution proxy: first point within 2px of all
            # remaining centres, with >=0.08s corroboration before animation.
            if not (name == 'wall-maximum' and i == 3):
                for j, p in enumerate(points):
                    if points[-1][0]-p[0] >= .08 and all(math.dist(p[1:],q[1:]) <= 2 for q in points[j:]):
                        events.append({'type': 'rest', 't': p[0],
                                       'position': [(p[1]-20)/680, (p[2]-490)/680],
                                       'uncertainty_s': .1, 'position_uncertainty_bw': 5/680})
                        break
            repeated = sum(math.dist(a[1:],b[1:]) == 0 for a,b in zip(points, points[1:]))
            shots.append({'id': name+'-'+str(i), 'clip': name, 'split': 'holdout' if i==3 else 'fit',
                          'release': release, 'usable_until': end,
                          'start': [(start[0]-20)/680, (start[1]-490)/680],
                          'direction': direction, 'pullback': arrow/680,
                          'pullback_annotation': {'frame_t': arrow_t, 'arrow_length_px': arrow,
                                                  'uncertainty_px': 5, 'kind': 'manual visible shaft+head proxy; not normalized game power'},
                          'centre_uncertainty_bw': 3/680, 'repeated_centre_pairs': repeated,
                          'candidate_frame_count': len(candidates),
                          'missing_marker_frames': len(candidates)-len(points),
                          'maximum_usable_pts_gap_s': max(b[0]-a[0] for a,b in zip(points,points[1:])),
                          'events': events,
                          'samples': [[t,(x-20)/680,(y-490)/680] for t,x,y in points],
                          'exclusions': ['aiming and turn reset outside usable interval', 'marker absence retained as missing, never interpolated']+
                          (['at/after 30.45s conservatively excluded for possible rack interaction; rest unavailable'] if name=='wall-maximum' and i==3 else [])})
    return {'version': 1, 'dataset': 'miniclip-wall-v1', 'device': 'Nothing Phone (2a)',
            'package': 'com.miniclip.carrom', 'game_version': None, 'equipment': None,
            'units': 'board width and seconds', 'physical_board_mm': None,
            'geometry': {'rails_px': [20,490,700,1170], 'striker_radius_px': 23,
                         'disc_radius_px': 18, 'uncertainty_px': 3, 'review': 'manual visual estimate'},
            'inventory': inventory, 'shots': shots, 'breaks': breaks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('numeric_extractions', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(build(args.numeric_extractions), indent=2, allow_nan=False)+'\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()

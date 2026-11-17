"""CB-38 training-only offline comparison; Python standard library, no live profile.

Analytic isotropic dv/dt=-(a+b*v) between swept rectangular wall events.
All clocks are original presentation seconds and all distances are board widths.
"""
import argparse
import copy
import hashlib
import itertools
import json
import math
from fractions import Fraction
from pathlib import Path

from analyze import finite, quadratic, validate_samples


ROOT = Path(__file__).resolve().parents[2]
PARAMETERS = {'constant': ['a', 'gain', 'e'],
              'speed_drag': ['a', 'gain', 'e', 'b'],
              'power_curve': ['a', 'gain', 'e', 'exponent']}
RADIUS = 23/680


def load_plan():
    return json.loads((ROOT/'docs/calibration/comparison-v1/plan.json').read_text(encoding='utf-8'))


def training_only(data, plan):
    """Select by preregistered ID AND split before inspecting any sample values."""
    if data.get('version') != 1 or plan.get('version') != 1 or data['dataset'] != plan['dataset']:
        raise ValueError('Dataset/plan version mismatch')
    ids = [s['id'] for s in data['shots']]
    expected = plan['training_ids']
    if len(set(ids)) != len(ids) or len(set(expected)) != len(expected):
        raise ValueError('Duplicate shot ID')
    selected = [s for s in data['shots'] if s['id'] in expected]
    if set(s['id'] for s in selected) != set(expected) or any(s['split'] != 'fit' for s in selected):
        raise ValueError('Missing or mislabeled preregistered training shot')
    if any(s['split'] == 'fit' and s['id'] not in expected for s in data['shots']):
        raise ValueError('Unexpected training shot')
    return sorted(selected, key=lambda s: expected.index(s['id']))


def motion(speed, a, b, elapsed):
    """Exact speed/distance until rest; stable series in the small-b limit."""
    for x in (speed, a, b, elapsed):
        finite(x)
    if not (0 <= speed <= 100 and .05 <= a <= 3 and 0 <= b <= 3 and 0 <= elapsed <= 60):
        raise ValueError('Motion outside finite offline bounds')
    stop = math.log1p(b*speed/a)/b if b else speed/a
    dt = min(elapsed, stop)
    z = b*dt
    if z < .001:
        phi1 = 1-z/2+z*z/6-z**3/24+z**4/120
        phi2 = .5-z/6+z*z/24-z**3/120+z**4/720
    else:
        phi1 = -math.expm1(-z)/z
        phi2 = (z+math.expm1(-z))/(z*z)
    distance = speed*dt*phi1-a*dt*dt*phi2
    velocity = 0 if elapsed >= stop else max(0, speed*math.exp(-z)-a*dt*phi1)
    return velocity, max(0, distance), stop


def predict(shot, candidate, parameters, times):
    if candidate not in PARAMETERS or len(parameters) != len(PARAMETERS[candidate]):
        raise ValueError('Unknown candidate/parameter shape')
    p = dict(zip(PARAMETERS[candidate], map(finite, parameters)))
    a, gain, e = p['a'], p['gain'], p['e']
    b, exponent = p.get('b', 0), p.get('exponent', 1)
    if not (.05 <= a <= 3 and 2 <= gain <= 26 and .3 <= e <= 1 and 0 <= b <= 3 and .5 <= exponent <= 2):
        raise ValueError('Parameters outside comparison bounds')
    release = finite(shot['release'])
    q = finite(shot['pullback'])
    if not 0 < q <= 1:
        raise ValueError('Positive bounded arrow proxy required')
    speed = gain*q*(q/.2)**(exponent-1)
    pos, direction = list(map(finite, shot['start'])), list(map(finite, shot['direction']))
    if len(pos) != 2 or len(direction) != 2 or abs(math.hypot(*direction)-1) > 1e-8:
        raise ValueError('Invalid start/direction')
    if not all(RADIUS <= x <= 1-RADIUS for x in pos):
        raise ValueError('Start outside rails')
    if not times or list(times) != sorted(times):
        raise ValueError('Ordered nonempty times required')
    for target in times:
        if not 0 <= finite(target)-release+1 <= 61:
            raise ValueError('Prediction horizon outside bounded interval')
    # Build continuous segments once, then sample original PTS without resampling.
    segments, events, rest = [], [], None
    t = release
    horizon = max(times)
    for _ in range(256):
        if t >= horizon or speed == 0:
            break
        stop = motion(speed, a, b, 0)[2]
        duration = min(horizon-t, stop)
        reach = motion(speed, a, b, duration)[1]
        walls = []
        for axis, d in enumerate(direction):
            if abs(d) > 1e-14:
                rail = 1-RADIUS if d > 0 else RADIUS
                distance = max(0, (rail-pos[axis])/d)
                if distance <= reach+1e-14:
                    if b == 0:
                        hit = 2*distance/(speed+math.sqrt(max(0, speed*speed-2*a*distance))) if distance else 0
                    else:
                        lo, hi = 0, duration
                        for _ in range(44):
                            mid = (lo+hi)/2
                            if motion(speed, a, b, mid)[1] < distance:
                                lo = mid
                            else:
                                hi = mid
                        hit = (lo+hi)/2
                    walls.append((hit, axis, rail))
        wall = min(walls, default=(math.inf, 0, 0))
        dt = min(duration, wall[0])
        segments.append((t, t+dt, list(pos), list(direction), speed))
        next_speed, travel, _ = motion(speed, a, b, dt)
        pos = [x+d*travel for x,d in zip(pos,direction)]
        t += dt
        speed = next_speed
        if stop <= dt+1e-12:
            speed = 0
            rest = {'t': t, 'position': list(pos)}
        elif wall[0] <= dt+1e-12:
            _, axis, rail = wall
            pos[axis] = rail
            velocity = [speed*d for d in direction]
            velocity[axis] *= -e
            speed = math.hypot(*velocity)
            direction = [v/speed for v in velocity] if speed else [0, 0]
            events.append({'t': t, 'position': list(pos), 'axis': axis})
    else:
        raise ValueError('Collision budget exhausted')
    positions, index = [], 0
    for target in times:
        if target <= release:
            positions.append(list(shot['start']))
            continue
        while index < len(segments) and target > segments[index][1]+1e-12:
            index += 1
        if index == len(segments):
            positions.append(list(pos))
        else:
            begin, _, start, d, v = segments[index]
            travel = motion(v, a, b, max(0, target-begin))[1]
            positions.append([x+u*travel for x,u in zip(start,d)])
    return {'positions': positions, 'events': events, 'rest': rest}


def residuals(shots, candidate, parameters):
    # sqrt(n) gives equal shot weighting in squared norm.
    result = []
    for shot in shots:
        rows = shot['samples']
        prediction = predict(shot, candidate, parameters, [r[0] for r in rows])
        scale = math.sqrt(len(rows)*len(shots))
        result.extend((x-y)/scale for p,r in zip(prediction['positions'],rows) for x,y in zip(p,r[1:]))
    return result


def objective(shots, candidate, parameters):
    return sum(r*r for r in residuals(shots, candidate, parameters))


def simplex(function, start, iterations, step, convergence):
    """Normalized box-projected Nelder-Mead with fixed iteration and tie order."""
    n = len(start)
    def evaluate(x):
        x = [min(1, max(0, v)) for v in x]
        return finite(function(x)), x
    vertices = [evaluate(start)]
    for i in range(n):
        x = list(start)
        x[i] += step if x[i]+step <= 1 else -step
        vertices.append(evaluate(x))
    for _ in range(iterations):
        vertices.sort()
        if max(math.dist(vertices[0][1], v[1]) for v in vertices[1:]) < convergence:
            break
        centre = [sum(v[1][i] for v in vertices[:-1])/n for i in range(n)]
        worst = vertices[-1][1]
        reflected = evaluate([2*c-w for c,w in zip(centre,worst)])
        if reflected[0] < vertices[0][0]:
            vertices[-1] = min(reflected, evaluate([3*c-2*w for c,w in zip(centre,worst)]))
        elif reflected[0] < vertices[-2][0]:
            vertices[-1] = reflected
        else:
            anchor = reflected[1] if reflected[0] < vertices[-1][0] else worst
            contracted = evaluate([(c+w)/2 for c,w in zip(centre,anchor)])
            if contracted[0] < min(reflected[0], vertices[-1][0]):
                vertices[-1] = contracted
            else:
                best = vertices[0][1]
                vertices = [vertices[0]]+[evaluate([(x+y)/2 for x,y in zip(best,v[1])]) for v in vertices[1:]]
    return min(vertices)


def fit(shots, candidate, plan):
    if len(shots) < 2:
        raise ValueError('At least two fitting shots required')
    for shot in shots:
        validate_samples(shot['samples'], max_speed=30)
        if shot['split'] != 'fit' or len(shot['samples']) < 8 or any(len(r) != 3 for r in shot['samples']):
            raise ValueError('Only sufficiently sampled training shots may fit')
    names = PARAMETERS[candidate]
    bounds = [plan['bounds'][name] for name in names]
    def normalized(p):
        return [(x-lo)/(hi-lo) for x,(lo,hi) in zip(p,bounds)]
    def physical(p):
        return [lo+x*(hi-lo) for x,(lo,hi) in zip(p,bounds)]
    ranked = sorted((objective(shots,candidate,p), list(p)) for p in
                    itertools.product(*(plan['coarse_grid'][name] for name in names)))
    config = plan['optimizer']
    solutions = []
    for _, start in ranked[:config['starts']]:
        score, p = simplex(lambda x: objective(shots,candidate,physical(x)), normalized(start),
                           config['iterations'], config['normalized_initial_step'], config['normalized_convergence'])
        solutions.append((score, physical(p)))
    solutions.sort()
    return {'parameters': solutions[0][1], 'mse_bw2': solutions[0][0],
            'start_solutions': [{'mse_bw2': score, 'parameters': p} for score,p in solutions]}


def metrics(shot, candidate, parameters):
    rows = shot['samples']
    pred = predict(shot,candidate,parameters,[r[0] for r in rows])
    errors = [math.dist(p,r[1:]) for p,r in zip(pred['positions'],rows)]
    extended = predict(shot,candidate,parameters,[shot['release']+30])
    walls = [e for e in shot.get('events',[]) if e['type'] == 'wall']
    stop = next((e for e in shot.get('events',[]) if e['type'] == 'rest'), None)
    predicted_walls = [e for e in extended['events'] if e['t'] <= shot['usable_until']]
    predicted_stop = extended['rest']
    return {'id': shot['id'], 'n': len(rows), 'rmse_bw': math.sqrt(sum(e*e for e in errors)/len(errors)),
            'max_error_bw': max(errors), 'observed_bounces': len(walls), 'predicted_bounces': len(predicted_walls),
            'bounce_errors': [{'time_error_s': p['t']-o['t'], 'position_error_bw': math.dist(p['position'],o['position'])}
                              for o,p in zip(walls,predicted_walls)],
            'stop_time_error_s': predicted_stop['t']-stop['t'] if predicted_stop and stop else None,
            'stop_position_error_bw': math.dist(predicted_stop['position'],stop['position']) if predicted_stop and stop else None,
            'residuals': [[r[0], p[0]-r[1], p[1]-r[2]] for p,r in zip(pred['positions'],rows)]}


def identifiability(shots, candidate, parameters, plan):
    names = PARAMETERS[candidate]
    columns = []
    for i, name in enumerate(names):
        lo, hi = plan['bounds'][name]
        step = (hi-lo)*1e-5
        left, right = list(parameters), list(parameters)
        left[i], right[i] = max(lo, parameters[i]-step), min(hi, parameters[i]+step)
        width = (right[i]-left[i])/(hi-lo)
        columns.append([(y-x)/width for x,y in zip(residuals(shots,candidate,left),residuals(shots,candidate,right))])
    norms = [math.sqrt(sum(v*v for v in column)) for column in columns]
    correlations = [{'parameters': [names[i],names[j]],
                     'cosine': sum(x*y for x,y in zip(columns[i],columns[j]))/(norms[i]*norms[j])}
                    for i in range(len(names)) for j in range(i+1,len(names)) if norms[i]*norms[j] > 1e-20]
    return {'normalized_jacobian_norms': dict(zip(names,norms)),
            'zero_columns': [name for name,norm in zip(names,norms) if norm < 1e-10],
            'column_cosines': correlations,
            'near_search_bounds': [name for name,x in zip(names,parameters)
                                   if min(x-plan['bounds'][name][0],plan['bounds'][name][1]-x) < (plan['bounds'][name][1]-plan['bounds'][name][0])*1e-4],
            'interpretation': 'Local sensitivity only; nonzero columns do not prove global identifiability; no statistical CI.'}


def clock_audit(rows):
    validate_samples(rows)
    runs, start = [], 0
    for i in range(1,len(rows)+1):
        if i == len(rows) or rows[i][1:] != rows[start][1:]:
            if i-start > 1:
                runs.append({'first_t': rows[start][0], 'last_t': rows[i-1][0], 'samples': i-start})
            start = i
    return {'samples': len(rows), 'maximum_gap_s': max((b[0]-a[0] for a,b in zip(rows,rows[1:])),default=0),
            'repeated_runs': runs, 'stationary_pairs': sum(r['samples']-1 for r in runs)}


def local_fit(rows):
    return quadratic(rows)


def audit(data, shots, directory):
    """Full extractor audit includes all six clip hashes, but only training trajectories."""
    inventory = []
    raw_by_clip = {}
    for item in data['inventory']:
        raw = json.loads((directory/('cb37-'+item['clip']+'.json')).read_text(encoding='utf-8'))
        if raw['sha256'] != item['sha256']:
            raise ValueError('Extraction/source hash mismatch')
        times = [r['t'] for r in raw['rows']]
        validate_samples([[t,0] for t in times])
        if any(abs(r['pts']*float(Fraction(raw['time_base']))-r['t']) > 1e-10 for r in raw['rows']):
            raise ValueError('PTS/time disagreement')
        raw_by_clip[item['clip']] = raw
        inventory.append({'clip': item['clip'], 'sha256': item['sha256'], 'frames': len(times),
                          'min_gap_s': min(b-a for a,b in zip(times,times[1:])),
                          'max_gap_s': max(b-a for a,b in zip(times,times[1:])),
                          'pts_consistent': True})
    result = []
    for shot in shots:
        raw = raw_by_clip[shot['clip']]
        lane = 'top' if shot['start'][1] < .5 else 'bottom'
        candidates = [r for r in raw['rows'] if shot['release']-1e-6 <= r['t'] <= shot['usable_until']]
        expected = [[r['t'],(r[lane][0]-20)/680,(r[lane][1]-490)/680] for r in candidates if r[lane] is not None]
        if expected != shot['samples']:
            raise ValueError('Annotation/extraction mismatch')
        walls = [e['t'] for e in shot['events'] if e['type'] == 'wall']
        rest = next((e['t'] for e in shot['events'] if e['type'] == 'rest'),shot['usable_until'])
        windows = []
        for span in (.1,.2,.4):
            end = min(shot['release']+.05+span, walls[0]-.05) if walls else shot['release']+.05+span
            rows = [[t,shot['direction'][0]/abs(shot['direction'][0])*x] for t,x,y in shot['samples'] if shot['release']+.05 <= t <= end]
            if len(rows) >= 4 and rows[-1][0]-rows[0][0] >= .03:
                value = local_fit(rows)
                windows.append({'requested_span_s': span, 'actual_span_s': rows[-1][0]-rows[0][0], **{k:v for k,v in value.items() if k != 'residuals'}})
        sign = (1 if shot['direction'][0] > 0 else -1)*(-1)**len(walls)
        terminal = [[t,sign*x] for t,x,y in shot['samples'] if (walls[-1] if walls else shot['release'])+.05 < t < rest-.05]
        result.append({'id': shot['id'], **clock_audit(shot['samples']),
                       'missing_marker_pts': [r['t'] for r in candidates if r[lane] is None],
                       'matches_extraction': True, 'early_windows': windows,
                       'terminal_local': {k:v for k,v in local_fit(terminal).items() if k != 'residuals'} if len(terminal) >= 4 and terminal[-1][0]-terminal[0][0] >= .03 else None})
    return {'inventory': inventory, 'training_shots': result,
            'annotation_corrections': [], 'note': 'Original annotations retained. Repeat runs bracket display holds, not engine updates. Missing marker is not missing game motion.'}


def comparison(data, plan):
    shots = training_only(data,plan)
    results = []
    for candidate in plan['candidates']:
        full = fit(shots,candidate,plan)
        folds = []
        for omitted in shots:
            fitted = fit([s for s in shots if s['id'] != omitted['id']],candidate,plan)
            folds.append({'omitted_id': omitted['id'], **fitted, 'score': metrics(omitted,candidate,fitted['parameters'])})
        perturbations = []
        for field, values in (('release',plan['uncertainty']['release_shifts_s']),('pullback',plan['uncertainty']['arrow_shifts_px'])):
            for value in values:
                changed = copy.deepcopy(shots)
                for shot in changed:
                    shot[field] += value if field == 'release' else value/680
                perturbations.append({'field': field, 'shift_s_or_px': value, **fit(changed,candidate,plan)})
        ranges = {}
        for i,name in enumerate(PARAMETERS[candidate]):
            values = [f['parameters'][i] for f in folds+perturbations]+[full['parameters'][i]]
            ranges[name] = [min(values), max(values)]
        results.append({'candidate': candidate, 'parameter_names': PARAMETERS[candidate], 'full_fit': full,
                        'training_metrics': [metrics(s,candidate,full['parameters']) for s in shots],
                        'folds': folds, 'cv_rmse_bw': math.sqrt(sum(f['score']['rmse_bw']**2 for f in folds)/len(folds)),
                        'perturbations': perturbations, 'parameter_sensitivity_ranges': ranges,
                        'identifiability': identifiability(shots,candidate,full['parameters'],plan)})
        print(candidate, results[-1]['cv_rmse_bw'], flush=True)
    baseline = results[0]
    eligible = []
    for result in results[1:]:
        improvement = 1-result['cv_rmse_bw']/baseline['cv_rmse_bw'] if baseline['cv_rmse_bw'] else 0
        wins = sum(f['score']['rmse_bw'] < g['score']['rmse_bw'] for f,g in zip(result['folds'],baseline['folds']))
        result['selection_check'] = {'relative_cv_improvement': improvement, 'folds_improved': wins}
        if improvement >= plan['selection']['minimum_relative_cv_rmse_improvement'] and wins >= plan['selection']['minimum_folds_improved']:
            eligible.append(result)
    selected = min(eligible,key=lambda r:r['cv_rmse_bw']) if eligible else baseline
    return {'version': 1, 'dataset': data['dataset'], 'training_ids': [s['id'] for s in shots],
            'candidates': results, 'selected_followup_hypothesis': selected['candidate'],
            'profile_status': 'not_validated_fresh_controlled_data_required', 'historical_holdout_rescored': False,
            'physical_board_mm': None, 'game_build': None, 'equipment': None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--measurements', type=Path, default=ROOT/'docs/calibration/recordings-v1/measurements.json')
    parser.add_argument('--plan', type=Path, default=ROOT/'docs/calibration/comparison-v1/plan.json')
    parser.add_argument('--extractions', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.measurements.read_text(encoding='utf-8'))
    plan = json.loads(args.plan.read_text(encoding='utf-8'))
    audit_result = audit(data,training_only(data,plan),args.extractions)
    result = comparison(data,plan)
    result['audit'] = audit_result
    result['input_hashes'] = {name: hashlib.sha256(path.read_bytes()).hexdigest() for name,path in
                              [('measurements',args.measurements),('plan',args.plan)]}
    args.output.write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf-8',newline='\n')


if __name__ == '__main__':
    main()

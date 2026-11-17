"""Deterministic board-relative calibration. Python 3.10+ standard library only.

The analytic no-body-contact model mirrors constant drag and rectangular swept
specular walls from CB-15/17. It does not run the Kotlin simulator or fit spin.
"""
import argparse
import json
import math
import itertools
from pathlib import Path


def finite(value):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError('Expected finite number')
    return value


def validate_samples(rows, max_speed=None):
    if not rows:
        raise ValueError('Missing samples')
    stationary = 0
    gaps = []
    for row in rows:
        if len(row) < 2:
            raise ValueError('Missing timestamp/coordinate')
        for v in row:
            finite(v)
    for a, b in zip(rows, rows[1:]):
        dt = b[0]-a[0]
        if dt <= 0:
            raise ValueError('Duplicate or nonmonotonic timestamp')
        distance = math.dist(a[1:], b[1:])
        if max_speed is not None and distance/dt > max_speed:
            raise ValueError('Possible reset: segment must be excluded')
        stationary += distance == 0
        gaps.append(dt)
    return {'stationary_pairs': stationary, 'max_gap_s': max(gaps, default=None)}


def solve(matrix, rhs):
    n = len(rhs)
    a = [list(row)+[v] for row,v in zip(matrix, rhs)]
    for i in range(n):
        pivot = max(range(i, n), key=lambda j: abs(a[j][i]))
        a[i], a[pivot] = a[pivot], a[i]
        if abs(a[i][i]) < 1e-14:
            raise ValueError('Singular measurement design')
        scale = a[i][i]
        a[i] = [v/scale for v in a[i]]
        for j in range(n):
            if j != i:
                scale = a[j][i]
                a[j] = [u-scale*v for u,v in zip(a[j], a[i])]
    return [row[-1] for row in a]


def quadratic(rows):
    validate_samples(rows)
    if len(rows) < 3 or rows[-1][0]-rows[0][0] < .03:
        raise ValueError('Insufficient trajectory samples/span')
    t0 = rows[0][0]
    design = [[1, t-t0, (t-t0)**2] for t,x in rows]
    coeff = solve([[sum(r[i]*r[j] for r in design) for j in range(3)] for i in range(3)],
                  [sum(r[i]*row[1] for r,row in zip(design, rows)) for i in range(3)])
    residual = [sum(c*v for c,v in zip(coeff,r))-row[1] for r,row in zip(design, rows)]
    # Worst-case independent coordinate perturbations within +/-3px. This is
    # a deterministic sensitivity envelope, not a statistical confidence interval.
    normal = [[sum(r[i]*r[j] for r in design) for j in range(3)] for i in range(3)]
    acceleration_dual = solve(normal, [0,0,-2])
    acceleration_sensitivity = (3/680)*sum(abs(sum(c*v for c,v in zip(acceleration_dual,r))) for r in design)
    return {'t0': t0, 'position': coeff[0], 'speed': coeff[1],
            'deceleration': -2*coeff[2], 'rmse': math.sqrt(sum(x*x for x in residual)/len(rows)),
            'deceleration_coordinate_sensitivity_bw_s2': acceleration_sensitivity,
            'n': len(rows), 'residuals': residual}


def reference_units(value, reference_length):
    """Arbitrary mm-labelled simulator reference, never a physical measurement."""
    finite(value)
    if finite(reference_length) <= 0:
        raise ValueError('Positive explicit arbitrary reference length required')
    return value*reference_length


def predict(shot, params, times, radius=23/680):
    a, gain, restitution = map(finite, params)
    if not (0 < a <= 10 and 0 < gain <= 30 and 0 <= restitution <= 1):
        raise ValueError('Parameters outside declared bounds')
    if not 0 <= finite(radius) < .5:
        raise ValueError('Invalid body radius')
    for t in times:
        finite(t)
    if times != sorted(times) or any(t < shot['release'] for t in times):
        raise ValueError('Prediction times must be ordered after release')
    pos = list(map(finite, shot['start']))
    direction = list(map(finite, shot['direction']))
    if abs(math.hypot(*direction)-1) > 1e-8 or not all(radius <= x <= 1-radius for x in pos):
        raise ValueError('Invalid start/direction')
    speed = finite(shot['pullback'])*gain
    if speed <= 0:
        raise ValueError('Positive measured pullback required')
    t = finite(shot['release'])
    events, positions = [], []
    rest = None
    for target in times:
        budget = 0
        while t < target and speed > 0:
            budget += 1
            if budget > 100:
                raise ValueError('Collision budget exhausted')
            candidates = []
            for axis in range(2):
                d = direction[axis]
                if abs(d) > 1e-14:
                    rail = 1-radius if d > 0 else radius
                    distance = max(0, (rail-pos[axis])/d)
                    if distance <= speed*speed/(2*a):
                        # Stable quadratic root for travelled distance.
                        dt = 2*distance/(speed+math.sqrt(max(0, speed*speed-2*a*distance))) if distance else 0
                        candidates.append((dt, axis, rail))
            stop_dt = speed/a
            hit = min(candidates, default=(math.inf, 0, 0))
            dt = min(target-t, stop_dt, hit[0])
            travel = speed*dt-.5*a*dt*dt
            pos = [p+d*travel for p,d in zip(pos, direction)]
            speed = max(0, speed-a*dt)
            t += dt
            if stop_dt <= dt+1e-12:
                speed = 0
                rest = {'t': t, 'position': list(pos)}
            elif hit[0] <= dt+1e-12:
                _, axis, rail = hit
                pos[axis] = rail
                velocity = [speed*d for d in direction]
                velocity[axis] *= -restitution
                speed = math.hypot(*velocity)
                direction = [v/speed for v in velocity] if speed else [0, 0]
                events.append({'t': t, 'position': list(pos), 'axis': axis})
                if speed == 0:
                    rest = {'t': t, 'position': list(pos)}
        t = target
        positions.append(list(pos))
    return {'positions': positions, 'events': events, 'rest': rest}


BOUNDS = [(0.001, 10), (.01, 30), (0, 1)]


def bounded_simplex(function, start):
    """Fixed-budget Nelder-Mead refinement with explicit box projection."""
    def bounded(values):
        return [min(hi,max(lo,x)) for x,(lo,hi) in zip(values,BOUNDS)]
    def evaluate(values):
        values = bounded(values)
        return function(values), values
    simplex = [evaluate(start)]
    for i, step in enumerate([.05,.3,.03]):
        vertex = list(start)
        vertex[i] += -step if start[i]+step > BOUNDS[i][1] else step
        simplex.append(evaluate(vertex))
    for _ in range(500):
        simplex.sort()
        if max(math.dist(simplex[0][1],s[1]) for s in simplex[1:]) < 1e-8:
            break
        centre = [sum(s[1][i] for s in simplex[:-1])/3 for i in range(3)]
        worst = simplex[-1][1]
        reflected = evaluate([2*c-w for c,w in zip(centre,worst)])
        if reflected[0] < simplex[0][0]:
            expanded = evaluate([3*c-2*w for c,w in zip(centre,worst)])
            simplex[-1] = min(reflected,expanded)
        elif reflected[0] < simplex[-2][0]:
            simplex[-1] = reflected
        else:
            anchor = reflected[1] if reflected[0] < simplex[-1][0] else worst
            contracted = evaluate([(c+w)/2 for c,w in zip(centre,anchor)])
            if contracted[0] < min(reflected[0],simplex[-1][0]):
                simplex[-1] = contracted
            else:
                best = simplex[0][1]
                simplex = [simplex[0]]+[evaluate([(b+w)/2 for b,w in zip(best,s[1])]) for s in simplex[1:]]
    return min(simplex)


def objective(shots, params):
    total, count = 0, 0
    for shot in shots:
        prediction = predict(shot, params, [s[0] for s in shot['samples']])
        for p, row in zip(prediction['positions'], shot['samples']):
            total += math.dist(p, row[1:])**2
            count += 1
    return total/count


def fit(shots):
    # Select before accessing samples: held-out data never enters the optimizer.
    training = [s for s in shots if s['split'] == 'fit']
    if len(training) < 2:
        raise ValueError('Insufficient fitting shots')
    for shot in training:
        validate_samples(shot['samples'], max_speed=30)
        if len(shot['samples']) < 8:
            raise ValueError('Insufficient fitting samples')
    # Broad fixed coarse grid avoids the no-bounce flat basin of high drag/low
    # gain. Select starts using training error only; deterministic tie policy.
    best = None
    grid = itertools.product([.1, .3, .6, 1, 2], range(2, 27, 2), [.4, .55, .7, .85, 1])
    ranked = sorted((objective(training, p), p) for p in grid)
    for _, start in ranked[:5]:
        score, params = bounded_simplex(lambda p:objective(training,p),start)
        if best is None or score < best[0]:
            best = score, params
    return {'parameters': best[1], 'fit_mse_bw2': best[0],
            'tangential_restitution': None, 'wall_friction': None,
            'optimizer': 'bounded deterministic coarse-grid/five-start simplex v1; local optimum, no statistical CI'}


def synthetic_shots():
    result = []
    for i, q in enumerate([.15, .2, .25]):
        shot = {'id': str(i), 'split': 'holdout' if i == 2 else 'fit',
                'start': [.5, .6], 'direction': [1, 0], 'release': 0, 'pullback': q}
        times = [j*.04 for j in range(101)]
        shot['samples'] = [[t]+p for t,p in zip(times, predict(shot, [.3, 4, .8], times)['positions'])]
        result.append(shot)
    return result


def report(data, plan):
    if data.get('version') != 1:
        raise ValueError('Unsupported measurement version')
    shots = data['shots']
    if len({s['id'] for s in shots}) != len(shots):
        raise ValueError('Duplicate shot ID')
    if any(s['split'] not in ('fit', 'holdout') for s in shots):
        raise ValueError('Unknown split')
    if plan.get('version') != 1 or plan['dataset'] != data['dataset']:
        raise ValueError('Plan/dataset version mismatch')
    for split in ('fit','holdout'):
        if sorted(s['id'] for s in shots if s['split']==split) != sorted(plan['split'][split]):
            raise ValueError('Measurement split disagrees with preregistered plan')
    result = fit(shots)
    params = result['parameters']
    metrics = []
    for shot in shots:
        validate_samples(shot['samples'], max_speed=30)
        pred = predict(shot, params, [s[0] for s in shot['samples']])
        errors = [math.dist(p,s[1:]) for p,s in zip(pred['positions'], shot['samples'])]
        observed = shot['events']
        bounce = next((e for e in observed if e['type'] == 'wall'), None)
        stop = next((e for e in observed if e['type'] == 'rest'), None)
        # Continue prediction for stop diagnostics without adding error samples.
        extended = predict(shot, params, [shot['release']+30])
        predicted_bounce = extended['events'][0] if extended['events'] else None
        predicted_stop = extended['rest']
        observed_walls = [e for e in observed if e['type'] == 'wall']
        predicted_walls = [e for e in extended['events'] if e['t'] <= shot['usable_until']]
        bounce_errors = [{'index': i+1, 'time_error_s': p['t']-o['t'],
                          'position_error_bw': math.dist(p['position'],o['position'])}
                         for i,(o,p) in enumerate(zip(observed_walls,predicted_walls))]
        boundaries = [shot['release']]+[e['t'] for e in observed if e['type'] == 'wall']
        boundaries += [stop['t'] if stop else shot['usable_until']]
        local_segments = []
        for index,(left,right) in enumerate(zip(boundaries,boundaries[1:])):
            sign = (1 if shot['direction'][0] > 0 else -1)*(-1)**index
            rows = [[t,sign*x] for t,x,y in shot['samples'] if left+.05 < t < right-.05]
            if len(rows) < 4 or rows[-1][0]-rows[0][0] < .05:
                local_segments.append({'status': 'insufficient isolated samples', 'n': len(rows)})
                continue
            local = quadratic(rows)
            local['left_t'], local['right_t'] = left,right
            local['speed_at_left_bw_s'] = local['speed']+local['deceleration']*(local['t0']-left)
            local['speed_at_right_bw_s'] = local['speed']-local['deceleration']*(right-local['t0'])
            local['speed_sensitivity_bw_s'] = (6/680)/(rows[-1][0]-rows[0][0])
            local['status'] = 'unconstrained local quadratic diagnostic, not profile fit'
            local_segments.append(local)
        losses = []
        for pre,post in zip(local_segments,local_segments[1:]):
            if 'speed_at_right_bw_s' in pre and 'speed_at_left_bw_s' in post and pre['speed_at_right_bw_s'] > 0:
                losses.append(post['speed_at_left_bw_s']/pre['speed_at_right_bw_s'])
        metrics.append({'id': shot['id'], 'split': shot['split'], 'n': len(errors),
                        'rmse_bw': math.sqrt(sum(e*e for e in errors)/len(errors)),
                        'max_error_bw': max(errors),
                        'bounce_time_error_s': predicted_bounce['t']-bounce['t'] if bounce and predicted_bounce else None,
                        'bounce_position_error_bw': math.dist(predicted_bounce['position'], bounce['position']) if bounce and predicted_bounce else None,
                        'stop_time_error_s': predicted_stop['t']-stop['t'] if stop and predicted_stop else None,
                        'stop_position_error_bw': math.dist(predicted_stop['position'], stop['position']) if stop and predicted_stop else None,
                        'observed_bounces': sum(e['type'] == 'wall' for e in observed),
                        'predicted_bounces_30s': len(extended['events']),
                        'predicted_bounces_in_observed_interval': len(predicted_walls),
                        'bounce_errors_order_matched': bounce_errors,
                        'local_segments': local_segments, 'local_normal_speed_ratios': losses,
                        'residuals': [[s[0], p[0]-s[1], p[1]-s[2]] for s,p in zip(shot['samples'],pred['positions'])],
                        'predicted_positions': [[s[0]]+p for s,p in zip(shot['samples'],pred['positions'])]})
    declared = plan['targets_exploratory_not_release_gates']
    targets = dict(zip(('rmse_bw','bounce_time_error_s','bounce_position_error_bw','stop_position_error_bw','stop_time_error_s'),
                       (declared[k] for k in ('trajectory_rmse_board_width','bounce_time_error_seconds','bounce_position_error_board_width','stop_position_error_board_width','stop_time_error_seconds'))))
    checks = []
    for metric in metrics:
        if metric['split'] != 'holdout':
            continue
        checks.append({'id': metric['id'],
                       'targets': {k: None if metric[k] is None else abs(metric[k]) <= v for k,v in targets.items()},
                       'all_matched_bounces_within_targets': all(abs(e['time_error_s']) <= targets['bounce_time_error_s'] and e['position_error_bw'] <= targets['bounce_position_error_bw'] for e in metric['bounce_errors_order_matched']),
                       'bounce_count_agrees': metric['observed_bounces'] == metric['predicted_bounces_in_observed_interval']})
    passed = all(all(v is True for v in c['targets'].values()) and c['bounce_count_agrees'] and c['all_matched_bounces_within_targets'] for c in checks) and bool(checks)
    break_metrics = []
    for shot in data.get('breaks',[]):
        rows = shot['samples']
        validate_samples(rows,max_speed=30)
        pred = predict(shot,params,[r[0] for r in rows])
        errors = [math.dist(p,r[1:]) for p,r in zip(pred['positions'],rows)]
        mean_speed = math.dist(rows[0][1:],rows[-1][1:])/(rows[-1][0]-rows[0][0]) if len(rows)>1 else None
        break_metrics.append({'id':shot['id'],'n':len(rows), 'usable_span_s':rows[-1][0]-rows[0][0],
                              'mean_measured_approach_speed_bw_s':mean_speed,
                              'predicted_launch_speed_bw_s':shot['pullback']*params[1],
                              'approach_rmse_bw':math.sqrt(sum(e*e for e in errors)/len(errors)),
                              'collision_validation':'unavailable: occluded identities; no full-rack initial geometry',
                              'model_parameters_fitted_from_break':False})
    result.update({'version': 1, 'dataset': data['dataset'], 'metrics': metrics,
                   'profile_status': 'conditional_offline_only' if passed else 'rejected_for_use_model_or_measurement_mismatch',
                   'heldout_target_checks': checks,
                   'breaks': break_metrics,
                   'target_note': 'Exploratory comparisons declared in plan.json before fitting; missing results cannot pass.',
                   'physical_scale_mm': None, 'normalized_game_power_mapping': None})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('measurements', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--plan', type=Path, help='Defaults to plan.json beside measurements')
    args = parser.parse_args()
    data = json.loads(args.measurements.read_text(encoding='utf-8'))
    plan = json.loads((args.plan or args.measurements.parent/'plan.json').read_text(encoding='utf-8'))
    args.output.write_text(json.dumps(report(data,plan), indent=2, allow_nan=False)+'\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()

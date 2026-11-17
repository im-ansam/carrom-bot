"""Independent analytic regressions for offline candidates and split isolation."""
import copy
import math
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import compare


class ComparisonTest(unittest.TestCase):
    def test_linear_speed_drag_matches_independent_closed_form(self):
        # dv/dt=-.4-.7v, v0=2; integrate before a wall on a large virtual path.
        v, distance, stop = compare.motion(2, .4, .7, .2)
        self.assertAlmostEqual(v, (2+.4/.7)*math.exp(-.14)-.4/.7, places=12)
        self.assertAlmostEqual(distance, (2+.4/.7)*(1-math.exp(-.14))/.7-.4*.2/.7, places=12)
        self.assertAlmostEqual(stop, math.log1p(.7*2/.4)/.7, places=12)

    def test_small_b_limit_and_rest_do_not_reverse(self):
        for b in (0, 1e-12, 1e-7):
            v, distance, _ = compare.motion(1, .5, b, .3)
            self.assertAlmostEqual(v, .85, delta=1e-7)
            self.assertAlmostEqual(distance, .2775, delta=1e-8)
        v, distance, stop = compare.motion(1, .5, 0, 10)
        self.assertEqual(v, 0)
        self.assertEqual(distance, 1)
        self.assertEqual(stop, 2)

    def test_normal_bank_and_rest_have_analytic_solution(self):
        shot = fixture_shots()[0]
        shot.update(start=[.5, .5], pullback=.2)
        hit = 2-math.sqrt(4-4*(.5-23/680))
        prediction = compare.predict(shot, 'constant', [.5, 5, .5], [hit, 10])
        self.assertAlmostEqual(prediction['events'][0]['t'], hit, places=9)
        speed_at_hit = 1-.5*hit
        self.assertAlmostEqual(prediction['rest']['t'], hit+speed_at_hit, places=9)
        self.assertAlmostEqual(prediction['positions'][-1][0], 1-23/680-(.5*speed_at_hit)**2, places=9)

    def test_release_uncertainty_keeps_all_pts(self):
        shot = fixture_shots()[0]
        shot['release'] = .0334
        result = compare.predict(shot, 'constant', [.5, 5, .8], [0, .02, .1])
        self.assertEqual(result['positions'][:2], [shot['start'], shot['start']])
        self.assertNotEqual(result['positions'][2], shot['start'])

    def test_bad_parameters_and_unbounded_input_fail(self):
        shot = fixture_shots()[0]
        for p in ([0, 5, .8], [.5, math.nan, .8], [.5, 5, 1.1]):
            with self.assertRaises(ValueError):
                compare.predict(shot, 'constant', p, [.1])
        for times in ([.2, .1], [math.inf], [61]):
            with self.assertRaises(ValueError):
                compare.predict(shot, 'constant', [.5, 5, .8], times)

    def test_audit_retains_repeats_gaps_and_missing_marker(self):
        rows = [[0, .5, .5], [.02, .5, .5], [.08, .6, .5]]
        result = compare.clock_audit(rows)
        self.assertEqual(result['repeated_runs'], [{'first_t': 0, 'last_t': .02, 'samples': 2}])
        self.assertEqual(result['maximum_gap_s'], .06)
        with self.assertRaises(ValueError):
            compare.clock_audit([[0, 1, 1], [0, 2, 1]])

    def test_extraction_audit_verifies_pts_centres_and_missing_markers(self):
        raw = {'sha256': 'synthetic-fixture', 'time_base': '1/1000', 'rows': [
            {'pts': 0, 't': 0, 'bottom': [360, 830]},
            {'pts': 20, 't': .02, 'bottom': [360, 830]},
            {'pts': 40, 't': .04, 'bottom': None},
            {'pts': 80, 't': .08, 'bottom': [428, 830]}]}
        shot = {'id': 'test', 'clip': 'synthetic', 'release': 0, 'usable_until': .08,
                'start': [.5, .5], 'direction': [1, 0], 'events': [],
                'samples': [[0, .5, .5], [.02, .5, .5], [.08, .6, .5]]}
        data = {'inventory': [{'clip': 'synthetic', 'sha256': 'synthetic-fixture'}]}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'cb37-synthetic.json'
            path.write_text(json.dumps(raw), encoding='utf-8')
            result = compare.audit(data, [shot], Path(directory))
            self.assertEqual(result['training_shots'][0]['missing_marker_pts'], [.04])
            raw['rows'][1]['t'] = .021
            path.write_text(json.dumps(raw), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'PTS'):
                compare.audit(data, [shot], Path(directory))
            raw['rows'][1]['t'] = .02
            raw['rows'][1]['bottom'][0] = 370
            path.write_text(json.dumps(raw), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Annotation'):
                compare.audit(data, [shot], Path(directory))

    def test_split_rejects_mislabel_and_ignores_poisoned_holdout(self):
        shots = fixture_shots()
        plan = {'training_ids': [s['id'] for s in shots], 'version': 1, 'dataset': 'test'}
        data = {'version': 1, 'dataset': 'test', 'shots': shots+[{'id': 'historical', 'split': 'holdout', 'samples': None}]}
        self.assertEqual(compare.training_only(data, plan), shots)
        changed = copy.deepcopy(data)
        changed['shots'][0]['split'] = 'holdout'
        with self.assertRaises(ValueError):
            compare.training_only(changed, plan)

    def test_internal_fold_never_fits_its_scored_shot_or_historical_samples(self):
        plan = compare.load_plan()
        plan.update(dataset='synthetic', training_ids=['0', '1', '2', '3'], candidates=['constant'])
        shots = fixture_shots()
        for shot in shots:
            times = [i*.05 for i in range(12)]
            shot.update(usable_until=times[-1], events=[])
            shot['samples'] = [[t]+p for t,p in zip(times,independent_axis(shot,[.5,5,.8,0],times))]
        data = {'version': 1, 'dataset': 'synthetic',
                'shots': shots+[{'id': 'poison', 'split': 'holdout', 'samples': None}]}
        fitted_ids = []
        def recorded_fit(training, candidate, config):
            fitted_ids.append([s['id'] for s in training])
            return {'parameters': [.5, 5, .8], 'mse_bw2': 0, 'start_solutions': []}
        with patch.object(compare, 'fit', side_effect=recorded_fit):
            result = compare.comparison(data,plan)
        self.assertEqual(fitted_ids[0], plan['training_ids'])
        for omitted, ids in zip(plan['training_ids'],fitted_ids[1:5]):
            self.assertNotIn(omitted,ids)
            self.assertEqual(len(ids),3)
        self.assertTrue(all('poison' not in ids for ids in fitted_ids))
        self.assertFalse(result['historical_holdout_rescored'])

    def test_short_windows_are_more_coordinate_sensitive(self):
        def rows(span):
            return [[i*span/10, .2+i*span/10-.3*(i*span/10)**2] for i in range(11)]
        self.assertGreater(compare.local_fit(rows(.1))['deceleration_coordinate_sensitivity_bw_s2'],
                           compare.local_fit(rows(.4))['deceleration_coordinate_sensitivity_bw_s2']*10)

    def test_normalized_optimizer_recovers_high_gain_and_speed_loss(self):
        plan = compare.load_plan()
        shots = fixture_shots()
        expected = [.6, 14, .85, .7]
        for shot in shots:
            times = [i*.07 for i in range(51)]
            shot['samples'] = [[t]+p for t,p in zip(times, independent_axis(shot, expected, times))]
        fitted = compare.fit(shots, 'speed_drag', plan)
        self.assertLess(fitted['mse_bw2'], 1e-10)
        for actual, target in zip(fitted['parameters'], expected):
            self.assertAlmostEqual(actual, target, delta=.001)

    def test_single_input_cannot_identify_power_curvature(self):
        shots = fixture_shots()[:1]
        shot = shots[0]
        shot['pullback'] = .2
        shot['samples'] = [[t]+p for t,p in zip([0, .1, .2], independent_axis(shot, [.5, 5, .8, 0], [0, .1, .2]))]
        result = compare.identifiability(shots, 'power_curve', [.5, 5, .8, 1], compare.load_plan())
        # This short pre-rail window also cannot observe restitution.
        self.assertEqual(result['zero_columns'], ['e', 'exponent'])


def fixture_shots():
    return [{'id': str(i), 'split': 'fit', 'release': 0, 'start': [.5, .5],
             'direction': [1, 0], 'pullback': q} for i,q in enumerate([.1, .15, .2, .24])]


def independent_axis(shot, parameters, times):
    """Tiny-step midpoint ODE oracle, independent of candidate analytic integration."""
    a, gain, e, b = parameters
    x, v, now = shot['start'][0], gain*shot['pullback'], 0
    result = []
    for target in times:
        while now < target-1e-14:
            dt = min(.0001, target-now)
            sign = 1 if v >= 0 else -1
            mid = max(0, abs(v)-(.5*dt)*(a+b*abs(v)))
            x += sign*mid*dt
            v = sign*max(0, abs(v)-dt*(a+b*mid))
            lo, hi = 23/680, 1-23/680
            if x > hi:
                x, v = 2*hi-x, -abs(v)*e
            if x < lo:
                x, v = 2*lo-x, abs(v)*e
            now += dt
        result.append([x, shot['start'][1]])
    return result


if __name__ == '__main__':
    unittest.main()

"""Synthetic numerical tests; no private media or optional packages."""
import copy
import math
import json
import random
from pathlib import Path
import unittest

import analyze


class CalibrationTest(unittest.TestCase):
    def test_quadratic_recovers_drag_and_speed_with_irregular_pts(self):
        times = [0, .017, .035, .052, .1, .15, .22, .31]
        rows = [[t, .2+1.4*t-.5*.3*t*t] for t in times]
        fit = analyze.quadratic(rows)
        self.assertAlmostEqual(fit['speed'], 1.4, places=10)
        self.assertAlmostEqual(fit['deceleration'], .3, places=10)
        self.assertLess(fit['rmse'], 1e-12)

    def test_timestamp_failures_and_duplicate_images_are_distinct(self):
        self.assertEqual(analyze.validate_samples([[0, 1], [.02, 1], [.1, 2]])['stationary_pairs'], 1)
        for rows in ([[0, 1], [0, 2]], [[.1, 1], [0, 2]], [[None, 1]], [[0, math.nan]]):
            with self.assertRaises(ValueError):
                analyze.validate_samples(rows)

    def test_insufficient_or_singular_fit_fails(self):
        for rows in ([[0, 1], [.1, 2]], [[0, 1], [0, 2], [0, 3]]):
            with self.assertRaises(ValueError):
                analyze.quadratic(rows)

    def test_predict_drag_bounce_and_rest(self):
        # At v=1,a=.5, travel .5 to right rail at t=2-sqrt(2).
        shot = {'start': [.5, .4], 'direction': [1, 0], 'pullback': 1, 'release': 0}
        hit = 2-math.sqrt(2)
        result = analyze.predict(shot, [.5, 1, .5], [hit, 4], radius=0)
        self.assertAlmostEqual(result['events'][0]['t'], hit, places=9)
        self.assertAlmostEqual(result['positions'][0][0], 1, places=9)
        self.assertAlmostEqual(result['positions'][1][0], .875, places=9)
        self.assertAlmostEqual(result['rest']['t'], hit+math.sqrt(.5), places=9)

    def test_scale_is_explicit_and_arbitrary(self):
        self.assertEqual(analyze.reference_units(.2, 1000), 200)
        for scale in (0, -1, math.inf):
            with self.assertRaises(ValueError):
                analyze.reference_units(.2, scale)

    def test_reset_is_rejected_not_fitted(self):
        with self.assertRaisesRegex(ValueError, 'reset'):
            analyze.validate_samples([[0, .2], [.02, .8]], max_speed=10)

    def test_holdout_cannot_influence_fitting(self):
        shots = analyze.synthetic_shots()
        first = analyze.fit(shots)
        changed = copy.deepcopy(shots)
        for shot in changed:
            if shot['split'] == 'holdout':
                shot['samples'] = [[t, x+100, y] for t,x,y in shot['samples']]
        self.assertEqual(first, analyze.fit(changed))
        for actual, expected in zip(first['parameters'], [.3, 4, .8]):
            self.assertAlmostEqual(actual, expected, delta=.002)

    def test_near_normal_does_not_fit_tangential_parameters(self):
        self.assertIsNone(analyze.fit(analyze.synthetic_shots())['tangential_restitution'])

    def test_invalid_profile_and_empty_fit_fail(self):
        with self.assertRaises(ValueError):
            analyze.fit([])
        shot = {'start': [.5, .5], 'direction': [1, 0], 'pullback': 1, 'release': 0}
        for params in ([0, 1, .8], [.3, 1, 1.1], [math.nan, 1, .8]):
            with self.assertRaises(ValueError):
                analyze.predict(shot, params, [1])

    def test_versioned_perturbed_fixture_recovers_bounded_parameters(self):
        fixture = json.loads((Path(__file__).resolve().parents[2]/'test-fixtures/calibration/analytic-v1.json').read_text())
        shots = analyze.synthetic_shots()
        rng = random.Random(fixture['seed'])
        for shot in shots:
            for row in shot['samples']:
                row[1] += rng.uniform(-1,1)*fixture['coordinate_perturbation_bw']
        actual = analyze.fit(shots)['parameters']
        for a,b in zip(actual,fixture['parameters']):
            self.assertAlmostEqual(a,b,delta=fixture['parameter_tolerance'])

    def test_high_gain_fixture_avoids_no_bounce_local_basin(self):
        shots = analyze.synthetic_shots()
        for shot in shots:
            times = [r[0] for r in shot['samples']]
            shot['samples'] = [[t]+p for t,p in zip(times,analyze.predict(shot,[.7,14,.85],times)['positions'])]
        params = analyze.fit(shots)['parameters']
        self.assertLess(analyze.objective([s for s in shots if s['split']=='fit'],params),1e-7)

    def test_report_rejects_changed_split_or_unknown_version(self):
        data={'version':1,'dataset':'synthetic-v1','shots':analyze.synthetic_shots()}
        plan={'version':1,'dataset':'synthetic-v1','split':{'fit':['0','1'],'holdout':['2']}}
        data['shots'][-1]['split']='fit'
        with self.assertRaisesRegex(ValueError,'preregistered'):
            analyze.report(data,plan)
        data['version']=2
        with self.assertRaisesRegex(ValueError,'version'):
            analyze.report(data,plan)


if __name__ == '__main__':
    unittest.main()

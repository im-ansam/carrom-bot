"""Original synthetic reset/censoring fixtures; no device or media dependencies."""
import copy
import unittest

import terminal_observation


def timing():
    return dict(sha256='a' * 64, pts=list(range(10)), time_base='1/100')


def annotations():
    return dict(schema='cb39-terminal-boundaries-v1', source_sha256='a' * 64,
                passes=[dict(id='pass-a', last_physics_pts=5, first_nonphysics_pts=6,
                             cause='reset'),
                        dict(id='pass-b', last_physics_pts=6, first_nonphysics_pts=7,
                             cause='reset')])


class TerminalObservationTest(unittest.TestCase):
    def test_earliest_boundary_frame_and_entire_reset_tail_are_excluded(self):
        clock, labels = timing(), annotations()
        before = copy.deepcopy((clock, labels))
        result = terminal_observation.censor_prefix(clock, labels)
        self.assertEqual(result['retained_pts'], [0, 1, 2, 3, 4])
        self.assertEqual(result['excluded_pts'], [5, 6, 7, 8, 9])
        self.assertEqual(result['transition_bounds_pts'], [5, 7])
        self.assertAlmostEqual(result['transition_width_s'], .02)
        self.assertEqual((clock, labels), before)

    def test_reset_never_becomes_rest_even_with_long_timed_prefix(self):
        clock = timing()
        clock['time_base'] = '1'
        result = terminal_observation.censor_prefix(clock, annotations())
        self.assertFalse(result['usable'])
        self.assertEqual(result['rest_status'], 'unobserved')
        self.assertIsNone(result['stop_time_s'])
        self.assertIsNone(result['stop_position_bw'])
        self.assertIn('rest_not_observed', result['blockers'])
        self.assertIn('transition_bracket_over_50ms', result['blockers'])

    def test_annotation_disagreement_is_reported_and_not_averaged(self):
        labels = annotations()
        labels['passes'][1].update(last_physics_pts=8, first_nonphysics_pts=9,
                                   cause='ambiguous')
        result = terminal_observation.censor_prefix(timing(), labels)
        self.assertEqual(result['transition_bounds_pts'], [5, 9])
        self.assertIn('annotation_disagreement', result['blockers'])
        self.assertEqual(result['retained_pts'][-1], 4)

    def test_missing_or_duplicate_annotation_passes_fail(self):
        for count in (0, 1, 3):
            labels = annotations()
            labels['passes'] = (labels['passes'] * 2)[:count]
            with self.subTest(count=count), self.assertRaises(ValueError):
                terminal_observation.censor_prefix(timing(), labels)
        labels = annotations()
        labels['passes'][1]['id'] = 'pass-a'
        with self.assertRaises(ValueError):
            terminal_observation.censor_prefix(timing(), labels)

    def test_hash_scope_clock_and_non_adjacent_bounds_fail(self):
        cases = []
        clock, labels = timing(), annotations()
        labels['source_sha256'] = 'b' * 64
        cases.append((clock, labels))
        for key, value in [('last_physics_pts', 5.0), ('last_physics_pts', True),
                           ('first_nonphysics_pts', 8), ('cause', 'bounce'),
                           ('last_physics_pts', 99)]:
            labels = annotations()
            labels['passes'][0][key] = value
            cases.append((timing(), labels))
        for pts in ([0, 0, 1], [0, None, 2], [2, 1, 0]):
            clock = timing()
            clock['pts'] = pts
            cases.append((clock, annotations()))
        for clock, labels in cases:
            with self.subTest(clock=clock, labels=labels), self.assertRaises(ValueError):
                terminal_observation.censor_prefix(clock, labels)

    def test_boundary_at_first_frame_does_not_invent_a_prefix(self):
        labels = annotations()
        for entry in labels['passes']:
            entry.update(last_physics_pts=0, first_nonphysics_pts=1)
        result = terminal_observation.censor_prefix(timing(), labels)
        self.assertEqual(result['retained_pts'], [])
        self.assertIn('insufficient_prefix', result['blockers'])

    def test_exact_fifty_ms_bracket_and_prefix_gap_are_separate(self):
        clock = timing()
        clock['pts'] = [0, 1, 10, 11, 12, 13, 18, 19, 20, 21]
        labels = annotations()
        for entry in labels['passes']:
            entry.update(last_physics_pts=13, first_nonphysics_pts=18)
        result = terminal_observation.censor_prefix(clock, labels)
        self.assertNotIn('transition_bracket_over_50ms', result['blockers'])
        self.assertIn('prefix_gap_over_50ms', result['blockers'])

    def test_changed_annotations_change_hash_and_order_does_not_change_cutoff(self):
        labels = annotations()
        result = terminal_observation.censor_prefix(timing(), labels)
        labels['passes'].reverse()
        reversed_result = terminal_observation.censor_prefix(timing(), labels)
        self.assertEqual(result['retained_pts'], reversed_result['retained_pts'])
        labels['passes'][0]['cause'] = 'occlusion'
        self.assertNotEqual(result['annotation_sha256'],
                            terminal_observation.censor_prefix(timing(), labels)['annotation_sha256'])


if __name__ == '__main__':
    unittest.main()

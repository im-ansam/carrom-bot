"""Original synthetic PTS fixtures, independent of devices and private media."""
import contextlib
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import capture_timing


class CaptureTimingTest(unittest.TestCase):
    def test_exact_fifty_millisecond_gap_passes_timing_gate_only(self):
        result = capture_timing.assess([0, 4500, 9000], '1/90000', [('shot', 0, .1)])
        self.assertEqual(result['intervals'][0]['gaps_over_50ms'], 0)
        self.assertEqual(result['status'], 'diagnostic_only_no_profile')

    def test_gap_crossing_interval_boundary_is_retained(self):
        result = capture_timing.assess([0, 9000, 18000], '1/90000', [('event', .09, .11)])
        self.assertEqual(result['intervals'][0]['gaps_over_50ms'], 2)
        self.assertAlmostEqual(result['intervals'][0]['max_gap_s'], .1)

    def test_idle_gap_does_not_fail_a_separate_motion_interval(self):
        result = capture_timing.assess([0, 90, 100, 110], '1/100', [('motion', .9, 1.1)])
        self.assertEqual(result['gaps_over_50ms'], 3)
        self.assertEqual(result['intervals'][0]['gaps_over_50ms'], 2)
        result = capture_timing.assess([0, 90, 91, 92], '1/100', [('motion', .9, .92)])
        self.assertEqual(result['gaps_over_50ms'], 1)
        self.assertEqual(result['intervals'][0]['gaps_over_50ms'], 0)

    def test_missing_repeated_or_nonmonotonic_pts_fail(self):
        for pts in ([], [0], [0, None], [0, 0], [1, 0], [0, True], [0, 1.0]):
            with self.subTest(pts=pts), self.assertRaises(ValueError):
                capture_timing.assess(pts, '1/90000', [])

    def test_clock_and_interval_contracts_fail_closed(self):
        for clock in ('0', '-1/90', 'nan', 'invalid', 90):
            with self.subTest(clock=clock), self.assertRaises(ValueError):
                capture_timing.assess([0, 90], clock, [])
        for intervals in ([('a', 0, 0)], [('a', -.1, .5)], [('a', 0, 2)],
                          [('a', 0, float('nan'))], [('a', 0, 1), ('a', 0, 1)]):
            with self.subTest(intervals=intervals), self.assertRaises(ValueError):
                capture_timing.assess([0, 90], '1/90', intervals)

    def test_frame_count_never_claims_distinct_game_updates(self):
        result = capture_timing.assess([0, 1, 3, 4], '1/60', [])
        self.assertEqual(result['frames'], 4)
        self.assertEqual(result['pts'], [0, 1, 3, 4])
        self.assertEqual(result['game_update_clock'], 'unknown')

    def test_cli_cannot_overwrite_original_even_before_decoder_import(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'original.mp4'
            source.write_bytes(b'original sentinel')
            with patch('sys.argv', ['capture_timing', str(source), '--label', 'shot',
                                    '--output', str(source)]), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as result:
                    capture_timing.main()
            self.assertEqual(result.exception.code, 2)
            self.assertEqual(source.read_bytes(), b'original sentinel')

    def test_cli_cannot_overwrite_original_through_a_hard_link(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'original.mp4'
            alias = Path(directory) / 'alias.json'
            source.write_bytes(b'original sentinel')
            os.link(source, alias)
            with patch('sys.argv', ['capture_timing', str(source), '--label', 'shot',
                                    '--output', str(alias)]), contextlib.redirect_stderr(io.StringIO()):
                with patch('capture_timing.inventory', side_effect=AssertionError('Decoder must not run')):
                    with self.assertRaises(SystemExit) as result:
                        capture_timing.main()
            self.assertEqual(result.exception.code, 2)
            self.assertEqual(source.read_bytes(), b'original sentinel')

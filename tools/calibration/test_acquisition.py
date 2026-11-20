"""Synthetic acquisition custody regressions; no recordings or services."""
import copy
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import acquisition


class AcquisitionTest(unittest.TestCase):
    def test_initial_plan_has_interleaved_preassigned_split_and_no_readiness(self):
        plan = acquisition.new_plan()
        self.assertEqual(len(plan['attempts']), 64)
        self.assertEqual([x['id'] for x in plan['attempts'][:5]],
                         ['A-1-r1', 'A-2-r1', 'A-3-r1', 'A-4-r1', 'A-1-r2'])
        report = acquisition.audit(plan)
        self.assertFalse(report['usable'])
        self.assertEqual(report['recorded'], 0)
        self.assertIn('production_replay_not_implemented', report['blockers'])

    def test_split_removal_and_identity_mutation_are_rejected(self):
        for key, value in [('split', 'development'), ('id', 'historical-1')]:
            plan = acquisition.new_plan()
            plan['attempts'][-1][key] = value
            with self.assertRaises(ValueError):
                acquisition.audit(plan)
        plan['attempts'].pop()
        with self.assertRaises(ValueError):
            acquisition.audit(plan)

    def test_hash_reuse_and_malformed_hash_are_rejected(self):
        for digest in ['bad', 'a' * 64]:
            plan = acquisition.new_plan()
            for row in plan['attempts'][:2]:
                row.update(disposition='recorded', source_sha256=digest)
            with self.assertRaises(ValueError):
                acquisition.audit(plan)

    def test_inspected_validation_is_explicitly_not_fresh(self):
        plan = acquisition.new_plan()
        row = next(x for x in plan['attempts'] if x['split'] == 'sealed_validation')
        row.update(disposition='recorded', source_sha256='b' * 64, analyst_opened=True)
        self.assertIn('validation_freshness_lost:' + row['id'], acquisition.audit(plan)['blockers'])

    def test_unknown_keys_nonfinite_and_wrong_environment_fail_closed(self):
        for key, value in [('usable', True), ('scale', float('nan'))]:
            plan = acquisition.new_plan()
            plan[key] = value
            with self.assertRaises(ValueError):
                acquisition.audit(plan)
        plan = acquisition.new_plan()
        plan['device'] = 'emulator'
        with self.assertRaises(ValueError):
            acquisition.audit(plan)

    def test_report_is_deterministic_and_binds_entire_plan(self):
        plan = acquisition.new_plan()
        self.assertEqual(acquisition.audit(plan), acquisition.audit(copy.deepcopy(plan)))
        changed = copy.deepcopy(plan)
        changed['metadata']['game_build'] = 'confirmed-example'
        self.assertNotEqual(acquisition.audit(plan)['plan_sha256'],
                            acquisition.audit(changed)['plan_sha256'])

    def test_audit_cli_preserves_plan_when_output_aliases_it(self):
        for alias_kind in ('same_path', 'hard_link'):
            with self.subTest(alias=alias_kind), tempfile.TemporaryDirectory() as directory:
                plan = acquisition.new_plan()
                plan['metadata']['game_build'] = 'confirmed-example'
                plan['attempts'][0].update(disposition='recorded', source_sha256='a' * 64)
                source = Path(directory) / 'plan.json'
                source.write_text(json.dumps(plan), encoding='utf-8')
                original = source.read_bytes()
                output = source
                if alias_kind == 'hard_link':
                    output = Path(directory) / 'audit-alias.json'
                    os.link(source, output)
                with patch('sys.argv', ['acquisition', '--plan', str(source),
                                        '--output', str(output)]), contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit) as result:
                        acquisition.main()
                self.assertEqual(result.exception.code, 2)
                self.assertEqual(source.read_bytes(), original)

    def test_audit_cli_writes_separate_report_without_modifying_plan(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'plan.json'
            output = Path(directory) / 'audit.json'
            plan = acquisition.new_plan()
            plan['metadata']['game_build'] = 'confirmed-example'
            plan['attempts'][0].update(disposition='recorded', source_sha256='a' * 64)
            source.write_text(json.dumps(plan), encoding='utf-8')
            original = source.read_bytes()
            with patch('sys.argv', ['acquisition', '--plan', str(source), '--output', str(output)]):
                acquisition.main()
            self.assertEqual(source.read_bytes(), original)
            self.assertEqual(json.loads(output.read_text())['schema'], 'cb39-acquisition-audit-v1')
            self.assertFalse(json.loads(output.read_text())['usable'])
            self.assertEqual(json.loads(output.read_text())['recorded'], 1)

    def test_audit_cli_preserves_plan_through_symbolic_link_output(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'plan.json'
            output = Path(directory) / 'audit-link.json'
            source.write_text(json.dumps(acquisition.new_plan()), encoding='utf-8')
            original = source.read_bytes()
            try:
                output.symlink_to(source)
            except OSError as error:
                if getattr(error, 'winerror', None) == 1314:
                    self.skipTest('Windows account lacks symbolic-link creation privilege')
                raise
            with patch('sys.argv', ['acquisition', '--plan', str(source),
                                    '--output', str(output)]), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as result:
                    acquisition.main()
            self.assertEqual(result.exception.code, 2)
            self.assertEqual(source.read_bytes(), original)

"""Synthetic acquisition custody regressions; no recordings or services."""
import copy
import unittest

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

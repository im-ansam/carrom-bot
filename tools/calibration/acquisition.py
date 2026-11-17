"""CB-39 acquisition-plan v1 and fail-closed custody audit, standard library only.

This is a precollection contract, not a fitted profile or validation scorer.
One original per attempt is required in v1 to keep sealed custody independent.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path


METADATA = ('game_build', 'android_version', 'display_resolution', 'refresh_rate',
            'recording_settings', 'board_theme', 'striker_upgrade', 'equipped_power',
            'effective_force', 'effective_aim', 'effective_time', 'private_mode',
            'input_graphic_rule', 'input_sweep_sha256')


def new_plan():
    """Preassign all attempts before collection; no outcome-dependent allocation."""
    attempts = []
    for stage, cells in [('A', 4), ('B', 6), ('C', 6)]:
        for repetition in range(1, 5):
            for cell in range(1, cells + 1):
                attempts.append(dict(id=f'{stage}-{cell}-r{repetition}', stage=stage,
                                     cell=cell, repetition=repetition,
                                     split='development' if repetition <= 2 else 'sealed_validation',
                                     disposition='not_recorded', source_sha256=None,
                                     analyst_opened=False, exclusion_reason=None))
    return dict(schema='cb39-acquisition-v1', device='Nothing Phone (2a)',
                package='com.miniclip.carrom', units='BW_seconds',
                metadata=dict.fromkeys(METADATA), attempts=attempts)


def _keys(value, expected):
    if not isinstance(value, dict) or set(value) != set(expected):
        raise ValueError('Unexpected or missing contract fields')


def _hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'),
                                     allow_nan=False).encode('utf-8')).hexdigest()


def audit(plan):
    """Validate identity/split/custody and list blockers. Never asserts usability.

    Unknown metadata uses JSON null. Source digests identify external immutable
    originals; this audit does not claim to have verified their bytes or contents.
    """
    template = new_plan()
    _keys(plan, template)
    for key in ('schema', 'device', 'package', 'units'):
        if plan[key] != template[key]:
            raise ValueError('Unsupported scope or schema: ' + key)
    _keys(plan['metadata'], METADATA)
    blockers = ['production_replay_not_implemented', 'numeric_annotations_missing',
                'model_and_parameter_freeze_missing', 'independent_validation_missing',
                'source_bytes_not_verified']
    for key, value in plan['metadata'].items():
        if value is None:
            blockers.append('metadata_unknown:' + key)
        elif not isinstance(value, str) or not value.strip() or len(value) > 500:
            raise ValueError('Metadata must be null or a bounded nonempty string')
    sweep = plan['metadata']['input_sweep_sha256']
    if sweep is not None and not re.fullmatch('[0-9a-f]{64}', sweep):
        raise ValueError('Invalid input sweep SHA-256')
    if not isinstance(plan['attempts'], list) or len(plan['attempts']) != 64:
        raise ValueError('Expected entire preassigned 64-attempt matrix')
    hashes = {sweep} if sweep else set()
    recorded = 0
    for row, expected in zip(plan['attempts'], template['attempts']):
        _keys(row, expected)
        for key in ('id', 'stage', 'cell', 'repetition', 'split'):
            if type(row[key]) is not type(expected[key]) or row[key] != expected[key]:
                raise ValueError('Changed preassigned identity, order or split')
        if type(row['analyst_opened']) is not bool:
            raise ValueError('Invalid custody flag')
        disposition = row['disposition']
        if disposition not in ('not_recorded', 'recorded', 'excluded'):
            raise ValueError('Invalid disposition')
        digest = row['source_sha256']
        reason = row['exclusion_reason']
        if disposition == 'not_recorded':
            if digest is not None or reason is not None or row['analyst_opened']:
                raise ValueError('Unrecorded attempt cannot carry evidence')
            blockers.append('not_recorded:' + row['id'])
        else:
            if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
                raise ValueError('Missing or malformed original SHA-256')
            if digest in hashes:
                raise ValueError('Reused original across attempts or sweep')
            hashes.add(digest)
            recorded += 1
            if disposition == 'excluded':
                if not isinstance(reason, str) or not reason.strip() or len(reason) > 500:
                    raise ValueError('Exclusion needs bounded reason')
                blockers.append('excluded:' + row['id'])
            elif reason is not None:
                raise ValueError('Recorded attempt cannot hide exclusion')
        if row['split'] == 'sealed_validation' and row['analyst_opened']:
            blockers.append('validation_freshness_lost:' + row['id'])
    return dict(schema='cb39-acquisition-audit-v1', plan_sha256=_hash(plan),
                usable=False, status='blocked_acquisition_or_validation',
                recorded=recorded, expected=64, blockers=blockers)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--new', action='store_true')
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.new == (args.plan is not None):
        parser.error('Choose exactly one of --new or --plan')
    value = new_plan() if args.new else audit(json.loads(args.plan.read_text(encoding='utf-8')))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(value, indent=2, allow_nan=False) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()

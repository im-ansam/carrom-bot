"""Training-only coherent annotation perturbations; no statistical confidence claim."""
import argparse
import copy
import json
from pathlib import Path

from analyze import fit


def sensitivity(data):
    cases = []
    for kind, amount in [('pullback_px',-5),('pullback_px',5),('release_s',-.0334),('release_s',.0334)]:
        shots = copy.deepcopy([s for s in data['shots'] if s['split']=='fit'])
        for shot in shots:
            if kind == 'pullback_px':
                shot['pullback'] += amount/680
            else:
                shot['release'] += amount
                shot['samples'] = [r for r in shot['samples'] if r[0] >= shot['release']]
        cases.append({'perturbation':kind, 'amount':amount, 'fit':fit(shots)})
    return {'version':1, 'interpretation':'Coherent annotation sensitivity, not confidence intervals; held-out data never refitted', 'cases':cases}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('measurements',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    args.output.write_text(json.dumps(sensitivity(json.loads(args.measurements.read_text(encoding='utf-8'))),indent=2,allow_nan=False)+'\n',encoding='utf-8',newline='\n')


if __name__=='__main__':
    main()

"""Render sanitized internal-fold metrics and signed residuals from a CB-38 report."""
import argparse
import html
import json
from pathlib import Path


COLORS = ['#2563eb', '#dc2626', '#15803d']


def render(directory):
    report = json.loads((directory/'report.json').read_text(encoding='utf-8'))
    candidates = report['candidates']
    lines = ['# Internal shot cross-validation, comparison v1', '',
             'Development shots only; these are not fresh independent validation. '
             'Equal shot weighting. Errors in BW; times in seconds.', '',
             '| Candidate | CV RMSE | Full training RMSE | Follow-up |',
             '| --- | ---: | ---: | --- |']
    for c in candidates:
        lines.append(f"| {c['candidate']} | {c['cv_rmse_bw']:.8f} | {c['full_fit']['mse_bw2']**.5:.8f} | "
                     f"{'selected hypothesis only' if c['candidate'] == report['selected_followup_hypothesis'] else 'not selected'} |")
    lines += ['', '| Candidate | Omitted shot | RMSE | Max error | Bounces observed/predicted | Max matched bounce time/position error | Rest time/position error |',
              '| --- | --- | ---: | ---: | ---: | ---: | ---: |']
    def number(value):
        return 'unavailable' if value is None else f'{value:.5f}'
    for c in candidates:
        for f in c['folds']:
            m = f['score']
            bt = max((abs(e['time_error_s']) for e in m['bounce_errors']),default=None)
            bp = max((e['position_error_bw'] for e in m['bounce_errors']),default=None)
            lines.append(f"| {c['candidate']} | {m['id']} | {m['rmse_bw']:.5f} | {m['max_error_bw']:.5f} | "
                         f"{m['observed_bounces']}/{m['predicted_bounces']} | {number(bt)}/{number(bp)} | "
                         f"{number(m['stop_time_error_s'])}/{number(m['stop_position_error_bw'])} |")
    lines += ['', 'Bounce errors are absolute maxima over order-matched observed/predicted events. '
              'Unmatched events and missing rest cannot pass. Signed per-event errors and '
              'time-stamped 2D residuals remain in report.json.', '',
              '![Internal-fold signed horizontal residuals](residuals.svg)', '']
    (directory/'metrics.md').write_text('\n'.join(lines),encoding='utf-8',newline='\n')
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="960" height="810" viewBox="0 0 960 810">',
             '<rect width="960" height="810" fill="white"/>',
             '<g font-family="sans-serif" font-size="13" fill="#172033">',
             '<text x="24" y="26" font-size="18">Internal folds: predicted minus observed horizontal position</text>',
             '<text x="24" y="48">Six development shots only; seconds since first sample; BW error. No fresh validation.</text>']
    for i,c in enumerate(candidates):
        parts.append(f'<text x="{24+i*290}" y="72" fill="{COLORS[i]}">{html.escape(c["candidate"])}</text>')
    for index in range(6):
        ox, oy = 65+(index%2)*475, 125+(index//2)*225
        width, height = 365, 160
        rows = [c['folds'][index]['score']['residuals'] for c in candidates]
        t0 = rows[0][0][0]
        span = max(r[-1][0]-t0 for r in rows)
        limit = max(.05, max(abs(row[1]) for r in rows for row in r))*1.1
        parts += [f'<text x="{ox}" y="{oy-12}">{html.escape(report["training_ids"][index])}</text>',
                  f'<rect x="{ox}" y="{oy}" width="{width}" height="{height}" fill="none" stroke="#94a3b8"/>',
                  f'<path d="M{ox},{oy+height/2}h{width}" stroke="#cbd5e1"/>',
                  f'<text x="{ox-50}" y="{oy+8}">+{limit:.2f}</text>',
                  f'<text x="{ox-50}" y="{oy+height}">−{limit:.2f}</text>',
                  f'<text x="{ox}" y="{oy+height+20}">0s</text>',
                  f'<text x="{ox+width-40}" y="{oy+height+20}">{span:.2f}s</text>']
        for color,r in zip(COLORS,rows):
            points = ' '.join(f'{ox+(t-t0)/span*width:.3f},{oy+height/2-x/limit*height/2:.3f}' for t,x,y in r)
            parts.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="1.4"/>')
    parts += ['</g></svg>', '']
    (directory/'residuals.svg').write_text('\n'.join(parts),encoding='utf-8',newline='\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    render(parser.parse_args().directory)

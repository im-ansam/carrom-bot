"""Render numeric-only plots/tables and production replay input; no media dependencies."""
import argparse
import json
from pathlib import Path


def render(data, report, output):
    params = report['parameters']
    csv = ['# replay-v1: id,elapsed,sx,sy,dx,dy,proxy,a,gain,e,observed_x,observed_y,analytic_x,analytic_y']
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1020" viewBox="0 0 1080 1020">',
           '<rect width="1080" height="1020" fill="white"/>',
           '<style>text{font-family: sans-serif;fill:#172235;font-size:13px}.obs{fill:none;stroke:#1261a0;stroke-width:2}.pred{fill:none;stroke:#c54a23;stroke-width:2;stroke-dasharray:5 3}</style>',
           '<text x="20" y="24">Board x versus elapsed seconds: blue measured, dashed orange model; y displacement included in RMSE</text>']
    table = ['| Shot | Split | n | RMSE BW | Max BW | First bounce dt s | Bounce position BW | Rest dt s | Rest position BW |',
             '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
    for i,(shot,metric) in enumerate(zip(data['shots'], report['metrics'])):
        for sample,pred in zip(shot['samples'], metric['predicted_positions']):
            csv.append(','.join(map(str,[shot['id'],sample[0]-shot['release']]+shot['start']+shot['direction']+
                                    [shot['pullback'],params[0],params[1],params[2]]+sample[1:]+pred[1:])))
        def fmt(key):
            v = metric[key]
            return 'unavailable' if v is None else f'{v:.5f}'
        table.append('| '+ ' | '.join([shot['id'],shot['split'],str(metric['n'])]+[fmt(k) for k in ('rmse_bw','max_error_bw','bounce_time_error_s','bounce_position_error_bw','stop_time_error_s','stop_position_error_bw')])+' |')
        x,y = 20+(i%3)*355, 65+(i//3)*310
        duration = shot['usable_until']-shot['release']
        def points(rows):
            return ' '.join(f'{x+30+(t-shot["release"])/duration*290:.2f},{y+240-xx*200:.2f}' for t,xx,yy in rows)
        svg += [f'<text x="{x}" y="{y}">{shot["id"]} ({shot["split"]}), RMSE {metric["rmse_bw"]:.4f} BW</text>',
                f'<path d="M{x+30},{y+40} V{y+240} H{x+320}" fill="none" stroke="#777"/>',
                f'<text x="{x}" y="{y+45}">1</text><text x="{x}" y="{y+240}">0</text>',
                f'<text x="{x+30}" y="{y+260}">0 s</text><text x="{x+270}" y="{y+260}">{duration:.2f} s</text>',
                f'<polyline class="obs" points="{points(shot["samples"])}"/>',
                f'<polyline class="pred" points="{points(metric["predicted_positions"])}"/>']
    svg.append('</svg>')
    (output/'trajectories.svg').write_text('\n'.join(svg)+'\n',encoding='utf-8',newline='\n')
    (output/'metrics.md').write_text('\n'.join(table)+'\n',encoding='utf-8',newline='\n')
    (output/'replay.csv').write_text('\n'.join(csv)+'\n',encoding='utf-8',newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path)
    args = parser.parse_args()
    data = json.loads((args.directory/'measurements.json').read_text(encoding='utf-8'))
    report = json.loads((args.directory/'report.json').read_text(encoding='utf-8'))
    render(data,report,args.directory)


if __name__ == '__main__':
    main()

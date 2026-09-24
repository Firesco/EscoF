"""Aggregate all raw runs; require exact per-tick checksums for deterministic scenarios."""
import csv,json,statistics
from pathlib import Path

def aggregate(work):
    records = []
    for directory in sorted(work.glob('*/*')):
        result_path = directory / 'benchmark-result.json'
        if not result_path.is_file():
            continue
        result = json.loads(result_path.read_text())
        manifest = json.loads((directory / 'run-manifest.json').read_text())
        if result.get('status') != 'passed':
            raise RuntimeError(f'Failed result: {directory}')
        variant, repeat = directory.name.rsplit('-', 1)
        records.append(dict(case=directory.parent.name, variant=variant, repeat=int(repeat),
                            result=result, manifest=manifest, directory=str(directory.relative_to(work))))
    summary = {}
    for case in sorted({record['case'] for record in records}):
        selected = [record for record in records if record['case'] == case]
        # Compare every measured deterministic output, not just the last tick.
        if case != 'natural_ai':
            checksums = {tuple(record['result']['sample_checksums']) for record in selected}
            if len(checksums) != 1:
                raise RuntimeError(f'Differential result mismatch in {case}')
        groups = {}
        for variant in sorted({record['variant'] for record in selected}):
            rows = [record for record in selected if record['variant'] == variant]
            tick_means = [row['result']['tick_ms']['mean'] for row in rows]
            call_means = [row['result']['call_ms']['mean'] for row in rows]
            groups[variant] = dict(
                runs=len(rows), mean_tick_ms=statistics.mean(tick_means),
                run_mean_tick_min_ms=min(tick_means), run_mean_tick_max_ms=max(tick_means),
                run_mean_tick_stdev_ms=statistics.stdev(tick_means) if len(rows)>1 else 0,
                mean_call_ms=statistics.mean(call_means),
                mean_run_p95_tick_ms=statistics.mean(row['result']['tick_ms']['p95'] for row in rows),
                main_cpu_ms=statistics.mean(row['result']['thread_cpu_nanos'].get('Server thread',0)/1e6 for row in rows),
                worker_cpu_ms=statistics.mean(sum(v for k,v in row['result']['thread_cpu_nanos'].items() if k.startswith('Esco-Compute-'))/1e6 for row in rows),
                mean_main_allocated_bytes=statistics.mean(row['result'].get('main_thread_allocated_bytes',-1) for row in rows),
                peak_parallel_workers=max(row['result'].get('esco_metrics',{}).get('peak_parallel_workers',0) for row in rows),
                peak_live_workers=max((row['result']['esco_metrics']['peak_live_workers'] for row in rows if 'peak_live_workers' in row['result'].get('esco_metrics',{})), default=None),
                mean_gc_millis=statistics.mean(row['result']['gc_collection_millis'] for row in rows),
                server_jar_sha256=sorted({row['manifest']['jar_sha256'] for row in rows}),
            )
        comparison = {}
        if 'paper' in groups:
            for variant in groups:
                if variant == 'paper':
                    continue
                comparison[variant] = dict(
                    tick_time_reduction_percent=100*(1-groups[variant]['mean_tick_ms']/groups['paper']['mean_tick_ms']),
                    call_time_reduction_percent=100*(1-groups[variant]['mean_call_ms']/groups['paper']['mean_call_ms']),
                )
        summary[case] = dict(variants=groups, versus_paper=comparison,
                             deterministic_outputs_equal=None if case=='natural_ai' else True)
    (work/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    with (work/'runs.csv').open('w',newline='') as output:
        writer=csv.writer(output)
        writer.writerow(['case','variant','repeat','mean_tick_ms','p50_tick_ms','p95_tick_ms','max_tick_ms',
                         'mean_call_ms','observed_tps','main_cpu_ms','worker_cpu_ms','gc_millis','gc_collections',
                         'checksum','jar_sha256','harness_sha256','evidence_directory'])
        for row in records:
            r,m=row['result'],row['manifest']
            writer.writerow([row['case'],row['variant'],row['repeat'],r['tick_ms']['mean'],r['tick_ms']['p50'],
                             r['tick_ms']['p95'],r['tick_ms']['max'],r['call_ms']['mean'],r['observed_tps'],
                             r['thread_cpu_nanos'].get('Server thread',0)/1e6,
                             sum(v for k,v in r['thread_cpu_nanos'].items() if k.startswith('Esco-Compute-'))/1e6,
                             r['gc_collection_millis'],r['gc_collections'],r['logical_checksum'],
                             m['jar_sha256'],m['plugin_sha256'],row['directory']])
    return summary


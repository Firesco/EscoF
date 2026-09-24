"""Sequential Leaf/Esco fresh-JVM benchmark. Synthetic results are not player-capacity claims."""
import argparse, hashlib, json, os, platform, shutil, subprocess, sys, time
from pathlib import Path
from aggregate_results import aggregate

CASES = {
    'activation_dense': dict(mode='activation', entities=16384, players=32, repeats=4),
    'sensor_dense': dict(mode='sensor', entities=8192, players=0, repeats=4),
    'sensor_large': dict(mode='sensor', entities=32768, players=0, repeats=4),
    'potential_dense': dict(mode='potential', entities=8192, players=0, repeats=32),
    'potential_large': dict(mode='potential', entities=65536, players=0, repeats=32),
    'potential_xlarge': dict(mode='potential', entities=262144, players=0, repeats=32),
    'natural_ai': dict(mode='natural-ai', entities=256, players=0, repeats=1),
}
VARIANTS = {'leaf': '0', 'esco0': '0', 'esco4': '4', 'esco_all': 'all', 'ablation8': '8'}
ABLATION = ['compute.caller-participates=false', 'sensors.pipeline=false',
            'sensors.parallel-threshold=8192', 'spawning.separate-arrays=false']
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def summarize(work):
    summary = aggregate(work)
    for data in summary.values():
        groups = data['variants']
        if 'leaf' in groups:
            base = groups['leaf']
            data['versus_leaf'] = {k:dict(tick_reduction_percent=100*(1-v['mean_tick_ms']/base['mean_tick_ms']),
                call_reduction_percent=100*(1-v['mean_call_ms']/base['mean_call_ms'])) for k,v in groups.items() if k != 'leaf'}
    (work/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    return summary

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home',required=True,type=Path)
    p.add_argument('--leaf',required=True,type=Path)
    p.add_argument('--esco',required=True,type=Path)
    p.add_argument('--runtime-cache',required=True,type=Path)
    p.add_argument('--work',required=True,type=Path)
    p.add_argument('--case',action='append',choices=CASES)
    p.add_argument('--variants',default='leaf,esco4,esco_all')
    p.add_argument('--serial-case',action='append',default=[],choices=CASES,
                   help='Include Esco workers=0 in selected cases to isolate multicore contribution')
    p.add_argument('--rounds',type=int,default=2)
    p.add_argument('--warmup',type=int,default=200)
    p.add_argument('--samples',type=int,default=200)
    p.add_argument('--processors',type=int,default=8)
    p.add_argument('--accept-minecraft-eula',action='store_true')
    a=p.parse_args()
    if not a.accept_minecraft_eula: raise SystemExit('Read https://aka.ms/MinecraftEULA and pass --accept-minecraft-eula to accept.')
    variants=a.variants.split(',')
    if any(v not in VARIANTS for v in variants): raise SystemExit('Unknown variant')
    work=a.work.resolve();work.mkdir(parents=True,exist_ok=False)
    runner=Path(__file__).with_name('run_case.py')
    schedule=[]
    for case in a.case or CASES:
        case_variants=list(variants)
        if case in a.serial_case and 'esco0' not in case_variants:case_variants.append('esco0')
        for repeat in range(1,a.rounds+1):
            for variant in case_variants if repeat%2 else list(reversed(case_variants)):
                schedule.append(dict(case=case,variant=variant,repeat=repeat))
    cpu=next((s.split(':',1)[1].strip() for s in Path('/proc/cpuinfo').read_text().splitlines() if s.startswith('model name')),'unknown')
    manifest=dict(started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),host_cpu=cpu,
        os=platform.platform(),actual_os_cpu_count=os.cpu_count(),jvm_active_processor_count=a.processors,
        cpu_affinity_pinned=False,heap='2 GiB fixed',collector='G1',spark_enabled=False,
        leaf_sha256=sha(a.leaf),esco_sha256=sha(a.esco),harness_sha256=sha(runner.parent/'plugins/build/benchmark.jar'),
        java_version=subprocess.check_output([str(a.java_home.resolve()/'bin/java'),'-version'],stderr=subprocess.STDOUT,text=True),
        warmup_ticks=a.warmup,sample_ticks=a.samples,cases=CASES,schedule=schedule,
        leaf_profile_for_all_variants='async4: async mob spawning, pathfinding=4, tracker=4; parallel-world-ticking=false',
        ablation_properties=ABLATION,
        limitation='Fresh flat worlds, fake player positions, forced repeated sensor/density calls; no connected clients. Natural AI is stochastic. Ablation is this JAR with old-style dispatch settings, not the unavailable 0.6 binary.')
    (work/'suite-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    for index,row in enumerate(schedule,1):
        directory=work/row['case']/f"{row['variant']}-{row['repeat']}"
        print(f"COMPARISON {index}/{len(schedule)} {row['case']} {directory.name}",flush=True)
        command=[sys.executable,'-u',str(runner),'--java-home',str(a.java_home.resolve()),
            '--jar',str((a.leaf if row['variant']=='leaf' else a.esco).resolve()),'--work',str(directory),
            '--runtime-cache',str(a.runtime_cache.resolve()),'--workers',VARIANTS[row['variant']],
            '--processors',str(a.processors),'--warmup',str(a.warmup),'--samples',str(a.samples),
            '--disable-spark','--leaf-profile','async4','--accept-minecraft-eula']
        for key,value in CASES[row['case']].items():command += ['--'+key,str(value)]
        if row['variant']=='ablation8':
            for prop in ABLATION:command += ['--property',prop]
        subprocess.run(command,check=True)
        summarize(work)
        for name in ('libraries','cache','versions'):
            if (directory/name).is_dir():shutil.rmtree(directory/name)
        (directory/'server.jar').unlink()
    print('COMPARISON_COMPLETE '+str(work/'summary.json'),flush=True)
if __name__=='__main__':main()

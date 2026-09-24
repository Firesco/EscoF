"""Launch an isolated localhost-only Paper/Esco benchmark. Never point --work at a real server."""
import argparse, hashlib, json, os, platform, shutil, subprocess, sys, threading, time, urllib.parse
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
p.add_argument('--jar', type=Path, required=True)
p.add_argument('--work', type=Path, required=True)
p.add_argument('--runtime-cache', type=Path)
p.add_argument('--mode', choices=['idle','activation','sensor','natural-ai','potential'], default='activation')
p.add_argument('--entities', type=int, default=6000)
p.add_argument('--players', type=int, default=32)
p.add_argument('--repeats', type=int, default=12)
p.add_argument('--warmup', type=int, default=100)
p.add_argument('--samples', type=int, default=200)
p.add_argument('--layout', choices=['spread','cluster'], default='spread')
p.add_argument('--workers', default='4')
p.add_argument('--processors', type=int, default=8)
p.add_argument('--property', action='append', default=[], help='Esco configuration key=value override')
p.add_argument('--verify-activation', action='store_true')
p.add_argument('--parity-fixture', action='store_true')
p.add_argument('--compat', action='store_true')
p.add_argument('--community-dir', type=Path)
p.add_argument('--disable-spark', action='store_true', help='Diagnostic control; applies equally to Paper and Esco')
p.add_argument('--leaf-profile', choices=['default','async4'], default='default', help='Leaf and Leaf-derived Esco: async4 enables 4 pathfinding and 4 tracker threads; world ticking remains synchronous')
p.add_argument('--jfr', action='store_true', help='Record a diagnostic Java Flight Recorder trace; not a performance result')
p.add_argument('--accept-minecraft-eula', action='store_true')
p.add_argument('--timeout', type=int, default=360)
a=p.parse_args()
if not a.accept_minecraft_eula: raise SystemExit('Read https://aka.ms/MinecraftEULA, then pass --accept-minecraft-eula if you agree.')
a.java_home=str(Path(a.java_home).resolve())
root=Path(__file__).resolve().parent
work=a.work.resolve()
work.mkdir(parents=True,exist_ok=False)
(work/'plugins').mkdir()
(work/'tmp').mkdir()
shutil.copy2(a.jar,work/'server.jar')
shutil.copy2(root/'plugins/build/benchmark.jar',work/'plugins/benchmark.jar')
if a.compat:
    for name in ('bukkit-probe','paper-probe'):
        shutil.copy2(root/f'plugins/build/{name}.jar',work/f'plugins/{name}.jar')
if a.community_dir:
    for plugin in a.community_dir.glob('*.jar'): shutil.copy2(plugin,work/'plugins'/plugin.name)
    shutil.copy2(root/'plugins/build/community-probe.jar',work/'plugins/community-probe.jar')
if a.runtime_cache:
    cached=a.runtime_cache/'cache/mojang_26.2.jar'
    if cached.exists():
        (work/'cache').mkdir()
        shutil.copy2(cached,work/'cache/mojang_26.2.jar')
    for source in (a.runtime_cache/'libraries').glob('**/*.jar'):
        target=work/source.relative_to(a.runtime_cache)
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source,target)
(work/'eula.txt').write_text('eula=true\n')
(work/'server.properties').write_text('''online-mode=false
server-ip=127.0.0.1
server-port=0
level-name=world
level-seed=26022026
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structure_overrides":[]}
generate-structures=false
allow-nether=false
view-distance=6
simulation-distance=6
spawn-protection=0
pause-when-empty-seconds=-1
enable-rcon=false
enable-query=false
enable-command-block=false
sync-chunk-writes=true
max-tick-time=60000
''')
(work/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
if a.disable_spark:
    (work/'config').mkdir(exist_ok=True)
    (work/'config/paper-global.yml').write_text('_version: 31\nspark:\n  enabled: false\n')
if a.leaf_profile == 'async4':
    (work/'config').mkdir(exist_ok=True)
    (work/'config/leaf-global.yml').write_text('''config-version: '3.0'
async:
  async-mob-spawning:
    enabled: true
  async-pathfinding:
    enabled: true
    max-threads: 4
  async-entity-tracker:
    enabled: true
    threads: 4
  parallel-world-ticking:
    enabled: false
''')
options=['-Xms2G','-Xmx2G','-XX:+UseG1GC',f'-XX:ActiveProcessorCount={a.processors}',
         '-Djava.io.tmpdir='+str(work/'tmp'),'-Dterminal.jline=false','-Dterminal.ansi=false',
         '-Dhttp.agent=EscoForkBuild/0.7', f'-Descos.workers={a.workers}',
         f'-Descos.activation.verify={str(a.verify_activation).lower()}']
if a.jfr:
    options.append('-XX:StartFlightRecording=filename='+str(work/'diagnostic.jfr')+',settings=profile,dumponexit=true')
options += ['-Duser.timezone=UTC']
for prop in a.property:
    if '=' not in prop or prop.startswith('-'): raise SystemExit('Expected Esco key=value')
    options.append('-Descos.'+prop)
options.append(f'-Dbench.parityFixture={str(a.parity_fixture).lower()}')
for key in ['mode','entities','players','repeats','warmup','samples','layout']:
    options.append(f'-Dbench.{key}={getattr(a,key)}')
for scheme in ('http','https'):
    proxy=urllib.parse.urlparse(os.environ.get(scheme.upper()+'_PROXY',''))
    if proxy.username or proxy.password: raise SystemExit('Authenticated proxy requires explicit configuration')
    if proxy.hostname:
        options += [f'-D{scheme}.proxyHost={proxy.hostname}',f'-D{scheme}.proxyPort={proxy.port or 80}']
if Path('/etc/ssl/certs/java/cacerts').exists(): options.append('-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
manifest={key:str(value) if isinstance(value,Path) else value for key,value in vars(a).items()}
manifest.update(jar_sha256=hashlib.sha256((work/'server.jar').read_bytes()).hexdigest(),
                plugin_sha256=hashlib.sha256((work/'plugins/benchmark.jar').read_bytes()).hexdigest(),
                os=platform.platform(), wall_started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()))
(work/'run-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
command=[str(Path(a.java_home)/'bin/java'),*options,'-jar','server.jar','--nogui']
process=subprocess.Popen(command,cwd=work,text=True,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
expired=threading.Event()
hard_stop=threading.Timer(20,process.kill)
def stop_expired():
    expired.set()
    process.terminate()
    hard_stop.start()
timer=threading.Timer(a.timeout,stop_expired)
timer.start()
try:
    reported_ignored_seed=False
    with (work/'console.log').open('w') as log:
        for line in process.stdout:
            log.write(line); log.flush()
            if 'Ignoring setSeed on Entity.SHARED_RANDOM' in line:
                if not reported_ignored_seed:
                    print('NOTE: Paper ignores entity RNG reseeding; natural-ai is a stochastic control. Full messages retained in console.log.',flush=True)
                    reported_ignored_seed=True
                continue
            if any(key in line for key in ['BENCH_', 'ERROR', 'Exception', 'Done (', 'Starting minecraft server version', 'Running Java']):
                print(line,end='',flush=True)
        code=process.wait(timeout=30)
finally:
    timer.cancel()
    hard_stop.cancel()
    if process.poll() is None: process.kill()
result_file=work/'benchmark-result.json'
if expired.is_set() or code != 0 or not result_file.exists():
    raise SystemExit(f'Run failed: exit={code}, timeout={expired.is_set()}, log={work}/console.log')
result=json.loads(result_file.read_text())
if result['status'] != 'passed': raise SystemExit(json.dumps(result))
metrics=result.get('esco_metrics',{})
if a.verify_activation and metrics:
    if metrics.get('activation_verifications',0) == 0 or metrics.get('activation_mismatches',0) != 0:
        raise SystemExit('Activation differential verification did not pass: '+json.dumps(metrics))
limit=metrics.get('configured_workers', 0)
if max(metrics.get('peak_parallel_workers',0), metrics.get('peak_live_workers',0)) > limit:
    raise SystemExit('Configured compute worker limit was exceeded')
if metrics.get('active_workers',0) != 0:
    raise SystemExit('Workers remained active after the synchronous benchmark phase')
if a.compat:
    for name in ('bukkit','paper'):
        probe=json.loads((work/f'compat-{name}.json').read_text())
        if probe.get('status') != 'passed': raise SystemExit(f'{name} compatibility probe failed')
        print(name, probe, flush=True)
if a.community_dir:
    community=json.loads((work/'compat-community.json').read_text())
    if community.get('status')!='passed': raise SystemExit('Community plugin probe failed: '+json.dumps(community))
    print('community',community,flush=True)
print(json.dumps({'work':str(work),'tick_ms':result['tick_ms'],'call_ms':result['call_ms'],
                  'checksum':result['logical_checksum'],'metrics':result['esco_metrics']},indent=2),flush=True)

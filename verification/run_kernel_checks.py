import argparse, os, subprocess, tempfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
a = p.parse_args()
if not a.java_home: raise SystemExit('Set JAVA_HOME to JDK 25')
a.java_home = str(Path(a.java_home).resolve())
output = root / 'verification/build/kernel'
output.mkdir(parents=True, exist_ok=True)
work = Path(tempfile.mkdtemp(prefix='config-', dir=output))
source = root / 'leaf-server/src/main/java/dev/escos/fork'
files = [source/'EscoConfig.java', source/'EscoMetrics.java', source/'concurrent/ParallelWork.java',
         source/'ai/DistanceOrder.java', source/'entity/ActivationSnapshot.java', source/'entity/PotentialWork.java', root/'verification/KernelChecks.java']
subprocess.run([str(Path(a.java_home)/'bin/javac'), '-d', str(output), *map(str,files)], check=True)
for workers in (0, 1, 2, 4, 8, 16, 32, 64, -1, "all"):
    subprocess.run([str(Path(a.java_home)/'bin/java'), f'-Descos.workers={workers}', '-cp', str(output), 'KernelChecks'], cwd=work,check=True)

for invalid in ('-2', '32768', '4.5', 'abc'):
    result = subprocess.run([str(Path(a.java_home)/'bin/java'), f'-Descos.workers={invalid}',
                             '-cp', str(output), 'KernelChecks', '--config-only'],
                            cwd=work, capture_output=True, text=True)
    if result.returncode == 0: raise SystemExit('Invalid worker value was accepted: '+invalid)
    print('PASS rejected workers='+invalid)
for processors, expected in ((1, 0), (2, 0), (8, 6), (16, 14), (64, 62)):
    result = subprocess.run([str(Path(a.java_home)/'bin/java'), f'-XX:ActiveProcessorCount={processors}',
                             '-Descos.workers=-1', '-cp', str(output), 'KernelChecks', '--config-only'],
                            cwd=work, capture_output=True, text=True, check=True)
    if result.stdout.strip() != 'workers='+str(expected): raise SystemExit('Unexpected auto selection: '+result.stdout)
    print(f'PASS auto processors={processors} -> workers={expected}')

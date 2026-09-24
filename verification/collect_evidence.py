"""Copy reviewable benchmark evidence without server binaries, caches, worlds or JFR traces."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--runs',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--name',action='append',required=True,help='Explicit run or suite directory names to include')
a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True)
allowed={'benchmark-result.json','run-manifest.json','suite-manifest.json','summary.json','runs.csv',
         'console.log','compat-bukkit.json','compat-paper.json','compat-community.json',
         'server.properties','bukkit.yml','spigot.yml','paper-global.yml','paper-world-defaults.yml',
         'escos.properties','diagnostic-summary.json','audit-result.json','leaf-global.yml','gale-global.yml','gale-world-defaults.yml'}
files=[]
for name in a.name:
    source=(a.runs/name).resolve()
    if source.parent != a.runs.resolve() or not source.is_dir():
        raise SystemExit('Use an existing direct child of --runs')
    directories=[source]
    if (source/'suite-manifest.json').exists():
        directories += [path for path in source.glob('*/*') if path.is_dir() and (path/'run-manifest.json').exists()]
    else:
        directories += [path for path in source.iterdir() if path.is_dir() and (path/'run-manifest.json').exists()]
    candidates=[]
    for directory in directories:
        candidates += [directory/file for file in allowed]
        candidates += [directory/'config'/file for file in ('paper-global.yml','paper-world-defaults.yml','escos.properties','leaf-global.yml','gale-global.yml','gale-world-defaults.yml')]
    for path in candidates:
        if not path.is_file():continue
        relative=path.relative_to(source)
        target=a.output/name/relative
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(path,target)
        if target.suffix=='.csv':
            # Match the source repository's LF policy before recording integrity hashes.
            target.write_text(target.read_text())
        if target.name == 'server.properties':
            # The disabled management service still generates an ephemeral credential.
            # Preserve its presence, but do not publish credentials in evidence archives.
            target.write_text('\n'.join('management-server-secret=<redacted-local-test-secret>'
                if line.startswith('management-server-secret=') else line
                for line in target.read_text().splitlines()) + '\n')
        files.append(dict(path=str(target.relative_to(a.output)),sha256=hashlib.sha256(target.read_bytes()).hexdigest(),bytes=target.stat().st_size))
(a.output/'files.sha256.json').write_text(json.dumps(sorted(files,key=lambda value:value['path']),indent=2)+'\n')
print(f'Collected {len(files)} evidence files into {a.output}')

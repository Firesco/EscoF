"""Download only the exact community plugin versions used in the compatibility report."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
lock = json.loads(Path(__file__).with_name('community-plugins.lock.json').read_text())
for plugin in lock:
    target = a.output / plugin['file']
    if target.exists():
        data = target.read_bytes()
    else:
        request = urllib.request.Request(plugin['url'], headers={'User-Agent': 'Esco-Fork-Verification/0.2'})
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()
    if len(data) != plugin['size'] or hashlib.sha256(data).hexdigest() != plugin['sha256']:
        raise SystemExit(f'Pinned size or SHA-256 mismatch: {plugin["file"]}')
    target.write_bytes(data)
    print(f'Verified {target}')

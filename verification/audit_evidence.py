"""Audit a completed comparison's identities, outputs, worker bounds and shared configs.

Requires PyYAML. Timing regressions are reported, never excluded by this audit.
"""
import argparse
import json
import math
from pathlib import Path

import yaml

CONFIGS = (
    'bukkit.yml', 'spigot.yml', 'config/paper-global.yml',
    'config/paper-world-defaults.yml', 'config/gale-global.yml',
    'config/gale-world-defaults.yml', 'config/leaf-global.yml',
)
ALLOWED_DIFFERENCES = {
    ('config/leaf-global.yml', '.misc.rebrand.server-gui-name'),
    ('config/leaf-global.yml', '.misc.rebrand.server-mod-name'),
    ('config/leaf-global.yml', '.network.protocol-support.xaero-map-server-id'),
}


def differences(left, right, path=''):
    if isinstance(left, dict) and isinstance(right, dict):
        for key in sorted(left.keys() | right.keys()):
            yield from differences(left.get(key), right.get(key), path + '.' + str(key))
    elif left != right:
        yield path, left, right


def properties(path):
    values = dict(line.split('=', 1) for line in path.read_text().splitlines()
                  if line and not line.startswith(('#', '!')) and '=' in line)
    # Each isolated server generates a fresh credential even with management off.
    assert values.get('management-server-enabled') == 'false', path
    values.pop('management-server-secret', None)
    return values


def audit(work):
    suite = json.loads((work / 'suite-manifest.json').read_text())
    rows, outputs, config_differences = [], {}, []
    reference = work / suite['schedule'][0]['case'] / 'leaf-1'
    for entry in suite['schedule']:
        case, variant, repeat = entry['case'], entry['variant'], entry['repeat']
        directory = work / case / f'{variant}-{repeat}'
        result = json.loads((directory / 'benchmark-result.json').read_text())
        manifest = json.loads((directory / 'run-manifest.json').read_text())
        assert result['status'] == 'passed', directory
        expected_hash = suite['leaf_sha256'] if variant == 'leaf' else suite['esco_sha256']
        assert manifest['jar_sha256'] == expected_hash, directory
        assert manifest['plugin_sha256'] == suite['harness_sha256'], directory
        assert result['warmup_ticks'] == suite['warmup_ticks'], directory
        assert result['sample_ticks'] == suite['sample_ticks'], directory
        assert result['available_processors'] == suite['jvm_active_processor_count'], directory
        for key in ('raw_tick_ms', 'raw_call_ms', 'sample_checksums'):
            assert len(result[key]) == suite['sample_ticks'], (directory, key)
        assert all(math.isfinite(x) and x >= 0 for x in result['raw_tick_ms']), directory
        assert all(math.isfinite(x) and x >= 0 for x in result['raw_call_ms']), directory
        if case != 'natural_ai':
            expected = outputs.setdefault(case, result['sample_checksums'])
            assert result['sample_checksums'] == expected, directory
        metrics = result.get('esco_metrics', {})
        if variant != 'leaf':
            count = manifest['workers']
            expected_workers = suite['jvm_active_processor_count'] if count == 'all' else int(count)
            assert metrics['configured_workers'] == expected_workers, directory
            assert metrics['active_workers'] == 0, directory
            assert metrics['peak_live_workers'] <= expected_workers, directory
            assert metrics['peak_parallel_workers'] <= expected_workers, directory
        assert manifest['leaf_profile'] == 'async4', directory
        assert manifest['disable_spark'] is True and manifest['jfr'] is False, directory
        assert properties(directory / 'server.properties') == properties(reference / 'server.properties'), directory
        for name in CONFIGS:
            left = yaml.safe_load((reference / name).read_text())
            right = yaml.safe_load((directory / name).read_text())
            for key, before, after in differences(left, right):
                assert (name, key) in ALLOWED_DIFFERENCES, (directory, name, key, before, after)
                config_differences.append(dict(run=str(directory.relative_to(work)), file=name,
                                               key=key, reference=before, actual=after))
        rows.append(dict(run=str(directory.relative_to(work)), status='passed',
                         jar_sha256=manifest['jar_sha256'],
                         maximum_tick_ms=max(result['raw_tick_ms']),
                         gc_collections=result['gc_collections'],
                         configured_workers=metrics.get('configured_workers'),
                         peak_parallel_workers=metrics.get('peak_parallel_workers'),
                         peak_live_workers=metrics.get('peak_live_workers')))
    return dict(status='passed', expected_runs=len(suite['schedule']), audited_runs=len(rows),
                deterministic_cases_with_identical_all_sample_checksums=sorted(outputs),
                configurations_equal_except_brand_map_nonce_and_disabled_management_secret=True,
                ignored_timing_samples=0, runs=rows, allowed_config_differences=config_differences)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.work)
    (args.work / 'audit-result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(f"PASS {result['audited_runs']} runs; identical deterministic outputs and shared configs; all samples retained")

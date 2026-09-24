# EscoF verification

The bundled evidence is historical data from the pre-rebrand 0.7.0 release.
It does not certify this renamed source distribution. Original variant names,
measurements and binary identities are retained. Local machine paths in the
public evidence are anonymized; see `evidence-0.7.0/PUBLICATION-NOTE.md`.

Use JDK 25. Performance runs must run sequentially on an otherwise idle host.
The release report and evidence directory contain the recorded environment,
exact JAR hashes, raw samples, per-tick checksums, configs and rejected candidates.

## Correctness

```sh
python3 verification/run_kernel_checks.py --java-home /path/to/jdk25
```

Each worker configuration uses caller-owned snapshots. Independent oracles check
stable IEEE double ordering, exact density sums, activation and Leaf priority
rules, nested calls, retained references and failure barriers. A forced 64-worker
barrier proves thread participation; it is not a 64-core speed measurement.

## Runtime probes

Run the server once to populate `libraries/` and `versions/`. Download the pinned
community plugins with `verification/fetch_community_plugins.py --output /path/to/plugins`.
Then build test plugins:

```sh
python3 verification/plugins/build_plugins.py --java-home /path/to/jdk25 --runtime /path/to/server --community-dir /path/to/plugins
```

`run_case.py --compat --community-dir ... --parity-fixture --verify-activation`
checks both Paper/Bukkit APIs and actual LuckPerms/Vault/WorldEdit operations.
Use at least 180 warmup and 100 sample ticks so all delayed callbacks finish.
POI tests compare against the renamed pinned upstream implementation, including
loaded empty areas, newly added records, occupancy, predicate order, exceptions
and removal. Sensor tests check publication ownership and visibility-cache parity.

## Leaf comparison

```sh
python3 verification/run_comparison.py --java-home /path/to/jdk25 --leaf /path/to/leaf-26.2-118.jar --esco /path/to/escof-26.2-0.7.0.jar --runtime-cache /path/to/server --work /path/to/new-run --variants leaf,esco4,esco_all --serial-case activation_dense --serial-case potential_xlarge --rounds 2 --warmup 200 --samples 200 --accept-minecraft-eula
```

Read the Minecraft EULA before using the acceptance flag. Work directories must
be new; runners create localhost-only temporary test servers. Never use a real
server directory as `--work`.

Every variant receives the same async4 Leaf profile: pathfinding/tracker 4,
async mob spawning enabled, parallel world ticking disabled. Heap, collector,
visible JVM processors and profiler state are also identical. Order reverses in
the second round. Every deterministic per-tick checksum must match before results
are aggregated. All runs remain in the report, including regressions.

The test uses forced sensor/density calls and fake player positions, with no
connected clients. The natural-AI case is a stochastic flat-world villager control.
These are workload-specific MSPT measurements, not maximum-player or universal
Folia-replacement claims. `ablation8` changes calculation settings in the same
JAR; it is not an old release binary. KernelTiming is diagnostic only.

After the 46-run suite finishes, `python3 verification/audit_evidence.py --work
/path/to/new-run` checks all server/harness identities, every deterministic sample
checksum, worker limits and effective shared YAML/properties. This audit requires
PyYAML. The only permitted shared-config differences are brand labels and Leaf's
random Xaero map identifier, plus the independently generated credential for the
disabled management service. That credential is redacted when evidence is copied.
No slow timing samples are discarded.

The separate sensor diagnostic uses `run_case.py --mode sensor --entities 8192
--players 0 --repeats 4 --warmup 200 --samples 400 --processors 8 --disable-spark
--leaf-profile async4 --jfr`, plus the same required JAR, Java, cache, new work
directory and EULA arguments. Run Leaf with workers 0, then Esco with workers 4
and all. These profiled controls are kept separate from the performance suite.
Their run manifests record all arguments; JFR summaries are supplied without the
large recordings. The exact benchmark binary is archived in
`evidence-0.7.0/harness/benchmark.jar`.

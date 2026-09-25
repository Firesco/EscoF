# ⚡ EscoF

**A multi-core focused Minecraft server fork built for modern CPUs.**

EscoF is an experimental Minecraft server fork built on top of Leaf and Paper.

It aims to make better use of modern multi-core processors by distributing selected computational workloads across worker threads while preserving the synchronous plugin model expected by Paper and Spigot plugins.

> [!WARNING]
> EscoF is currently **beta software**.
> Performance and compatibility depend on your plugins, configuration, hardware, and workload.
> EscoF is not claimed to outperform Paper or Leaf in every scenario.

## 🚀 Current Version

- **Minecraft:** 26.2
- **EscoF:** 0.7.0 Beta
- **Java:** 25
- **Plugin target:** Paper / Spigot
- **Status:** Experimental / Beta

## 🧠 What Makes EscoF Different?

Minecraft server workloads traditionally rely heavily on the main server thread.

EscoF experiments with moving selected computational workloads to a worker pool so that additional CPU cores can participate when the workload is large enough.

EscoF does **not** attempt to run all world operations or plugin events asynchronously.

The synchronous Paper/Spigot plugin model is intentionally preserved.

Current areas include optimizations related to:

- Entity activation calculations
- Sensor processing
- Spawning-related calculations
- POI checks
- Path reconstruction
- Selected CPU-heavy workloads

Parallel execution is controlled by workload thresholds to avoid unnecessary threading overhead for small tasks.

## ⚙️ Requirements

| Component | Requirement |
|---|---|
| Minecraft | 26.2 |
| Java | Java 25 |
| Server JAR | `escof-26.2-0.7.0.jar` |
| OS | Any system capable of running Java 25 |

## 📦 Running EscoF

### Linux / macOS

```bash
java -Xms2G -Xmx4G -Descof.workers=auto -jar escof-26.2-0.7.0.jar --nogui
```

### Windows

```bat
start-escof.bat --workers auto
```

On the first launch, accept the Minecraft EULA by changing:

```text
eula=false
```

to:

```text
eula=true
```

after reading and accepting the Minecraft EULA.

## 🧵 Worker Configuration

EscoF allows you to control the maximum worker pool size.

```text
auto
all
0
1-32767
```

### `auto`

Uses the number of logical processors visible to the JVM minus 2.

Example:

```text
8 logical processors → 6 workers
```

### `all`

Allows EscoF to use up to all logical processors visible to the JVM.

### `0`

Runs EscoF computations on the calling thread without using EscoF worker threads.

### Manual configuration

Example:

```bash
java -Descof.workers=6 -jar escof-26.2-0.7.0.jar --nogui
```

More workers do **not** automatically mean better performance.

The Minecraft main thread, networking, chunk processing, plugins, the JVM and the operating system still require CPU resources.

Always benchmark different configurations under comparable workloads.

## 🔧 Configuration

EscoF creates:

```text
config/escof.properties
```

Default configuration:

```properties
workers=auto
compute.caller-participates=true

activation.enabled=true
activation.min-players=8
activation.min-entities=1024
activation.verify=false

sensors.enabled=true
sensors.pipeline=true
sensors.parallel-threshold=65536

spawning.parallel-potential=true
spawning.separate-arrays=true
spawning.items-per-task=16384
spawning.parallel-threshold=32768

poi.loaded-empty-check=true
paths.linear-reconstruction=true
```

Restart the server after changing these settings.

## 📊 Runtime Statistics

Run:

```text
/escof
```

or from the console:

```text
escof
```

EscoF reports statistics including:

- `configured_workers`
- `live_workers`
- `active_workers`
- `peak_parallel_workers`
- `worker_tasks`
- `caller_compute_tasks`

Seeing:

```text
active_workers=0
```

while the server is idle is normal.

These counters show whether EscoF's compute system is being used. They are **not**, by themselves, proof of a performance improvement.

## 🔌 Plugin Compatibility

Compatibility with Paper and Spigot plugins is a design goal.

However, compatibility with every plugin is **not guaranteed**, especially during beta.

Plugins that depend heavily on:

- NMS
- implementation-specific behavior
- specific Minecraft versions

should be tested before using EscoF in production.

Fabric and Forge mods are not Bukkit/Paper plugins and cannot simply be placed in the `plugins/` directory.

## 🧪 We Need Testers

EscoF is currently looking for server owners and developers willing to test it under real-world workloads.

Useful test results include:

- CPU model
- Core/thread count
- Java version
- RAM allocation
- Player count
- Entity count
- Plugin list
- Worker configuration
- MSPT
- CPU utilization
- Comparison against your current server software

Bug reports, crashes, plugin compatibility reports and performance measurements are extremely valuable.

Please include reproduction steps when reporting a problem.

## 🏗️ Building From Source

Requirements:

- Git
- Python 3
- JDK 25

Build with:

```bash
python3 build-escof.py --java-home /path/to/jdk25
```

The resulting JAR will be located at:

```text
dist/escof-26.2-0.7.0.jar
```

## ⚠️ Beta Notice

EscoF is experimental software.

Before migrating an existing server:

1. Back up your worlds.
2. Back up your plugins and configuration.
3. Test EscoF on a separate copy first.
4. Check startup logs and plugin compatibility.
5. Compare performance using the same world and similar workloads.

Do not evaluate performance based only on worker count.

Measure **MSPT, latency and CPU usage** under comparable conditions.

## 🐛 Reporting Issues

When reporting an issue, please include:

- EscoF version
- Java version
- CPU
- Startup command
- Worker configuration
- Plugin list
- Relevant configuration
- Relevant logs
- Steps to reproduce

Remove passwords, tokens, IPs or other sensitive information before sharing logs.

## 📜 Credits & License

EscoF builds upon the work of **Leaf** and **Paper**.

Upstream licenses and author attribution are preserved.

See:

- `LICENSE.md`
- `NOTICE.md`
- `README.LEAF.md`

for licensing and upstream attribution.

---

**EscoF — Developed by Firesco ⚡**

⭐ If you're interested in the project, consider starring the repository.

🧪 If you run a Minecraft server, real-world benchmarks and compatibility reports are greatly appreciated.

package dev.escos.verify;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.papermc.paper.entity.activation.ActivationRange;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PotentialCalculator;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Identical harness binary runs on both Paper and Esco. It has no Esco compile dependency. */
public final class BenchPlugin extends JavaPlugin implements Listener {
    private final String mode = System.getProperty("bench.mode", "activation");
    private final int count = Integer.getInteger("bench.entities", 6000);
    private final int playerCount = Integer.getInteger("bench.players", 32);
    private final int repeats = Integer.getInteger("bench.repeats", 12);
    private final int warmup = Integer.getInteger("bench.warmup", 100);
    private final int samples = Integer.getInteger("bench.samples", 200);
    private final boolean parityFixture = Boolean.getBoolean("bench.parityFixture");
    private final List<Entity> entities = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Double> tickTimes = new ArrayList<>(), callTimes = new ArrayList<>();
    private final List<Long> sampledChecksums = new ArrayList<>();
    private final Map<Entity, Integer> indices = new IdentityHashMap<>();
    private final ExposedSensor sensor = new ExposedSensor();
    private final PotentialCalculator potential = new PotentialCalculator();
    private ServerLevel level;
    private LivingEntity body;
    private int tick;
    private long sampleStart, checksum;
    private double lastCallMs;
    private boolean sampledTick, done;
    private Map<String, Long> startCpu = Map.of();
    private long startGcMillis, startGcCollections, startAllocatedBytes;

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { setup(); }
            catch (Throwable error) { fail(error); }
        }, 10L);
    }

    private void setup() {
        require(Bukkit.isPrimaryThread(), "setup thread");
        final World world = Bukkit.getWorlds().getFirst();
        this.level = ((CraftWorld)world).getHandle();
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setTime(6000);
        for (int x = -6; x <= 6; ++x) for (int z = -6; z <= 6; ++z) world.setChunkForceLoaded(x, z, true);
        world.getEntities().stream().filter(e -> !(e instanceof org.bukkit.entity.Player)).forEach(org.bukkit.entity.Entity::remove);
        final Random random = new Random(26022026L);
        if (this.mode.equals("natural-ai")) this.level.spigotConfig.villagerActivationRange = 0;
        if (this.parityFixture) {
            this.level.paperConfig().entities.markers.tick = false;
            this.level.spigotConfig.ignoreSpectatorActivation = true;
            this.level.purpurConfig.idleTimeoutTickNearbyEntities = false;
        }
        for (int i = 0; i < this.count; ++i) {
            if (this.mode.equals("potential")) {
                this.potential.addCharge(new BlockPos(random.nextInt(2048) - 1024, random.nextInt(256) - 64,
                    random.nextInt(2048) - 1024), random.nextDouble() * 4 + 0.01);
                continue;
            }
            final Location location = new Location(world, random.nextDouble() * 160 - 80, 80 + random.nextDouble() * 4, random.nextDouble() * 160 - 80);
            org.bukkit.entity.Entity spawned;
            if (this.parityFixture) {
                List<Class<? extends org.bukkit.entity.Entity>> types = List.of(ArmorStand.class, org.bukkit.entity.Cow.class,
                    org.bukkit.entity.Zombie.class, Villager.class, org.bukkit.entity.Squid.class, org.bukkit.entity.Phantom.class,
                    org.bukkit.entity.Pillager.class, org.bukkit.entity.Marker.class, org.bukkit.entity.TNTPrimed.class);
                spawned = world.spawn(location, types.get(i % types.size()), entity -> {
                    entity.setGravity(false); entity.setInvulnerable(true); entity.setPersistent(false);
                    if (entity instanceof org.bukkit.entity.LivingEntity living) { living.setAI(false); living.setCollidable(false); }
                    if (entity instanceof org.bukkit.entity.TNTPrimed tnt) tnt.setFuseTicks(Integer.MAX_VALUE);
                });
            } else if (this.mode.equals("natural-ai")) {
                spawned = world.spawn(location, Villager.class, entity -> {
                    entity.setGravity(false); entity.setInvulnerable(true); entity.setPersistent(true);
                    entity.setRemoveWhenFarAway(false); entity.setCollidable(false);
                });
            } else {
                spawned = world.spawn(location, ArmorStand.class, entity -> {
                    entity.setGravity(false); entity.setInvulnerable(true); entity.setVisible(false);
                    entity.setPersistent(false); entity.setCollidable(false);
                });
            }
            final Entity handle = ((CraftEntity)spawned).getHandle();
            if (this.parityFixture && i % 37 == 0) handle.setBoundingBox(handle.getBoundingBox().inflate(32,4,32));
            if (this.mode.equals("natural-ai")) {
                ((net.minecraft.world.entity.Mob)handle).getRandom().setSeed(2602L + i);
            }
            this.indices.put(handle, i + 1);
            this.entities.add(handle);
        }
        if (this.mode.equals("sensor")) {
            Villager villager = world.spawn(new Location(world, 0, 82, 0), Villager.class, entity -> {
                entity.setAI(false); entity.setGravity(false); entity.setInvulnerable(true);
            });
            this.body = (LivingEntity)((CraftEntity)villager).getHandle();
            this.body.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(192);
        }
        final MinecraftServer server = ((CraftServer)Bukkit.getServer()).getServer();
        for (int i = 0; i < this.playerCount; ++i) {
            final ServerPlayer player = new FixturePlayer(server, this.level, new GameProfile(new UUID(2602, i + 1), "Fixture" + i), this.parityFixture && i % 3 == 0, this.parityFixture && i % 7 == 0);
            double spread = System.getProperty("bench.layout", "spread").equals("cluster") ? 20 : 140;
            player.setPos(random.nextDouble() * spread - spread / 2, 82, random.nextDouble() * spread - spread / 2);
            this.players.add(player);
        }
        getLogger().info("BENCH_READY mode=" + this.mode + " entities=" + this.count + " synthetic_player_positions=" + this.playerCount);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (this.done) return;
            try { runTick(); } catch (Throwable error) { fail(error); }
        }, 20, 1);
    }

    private void runTick() {
        require(Bukkit.isPrimaryThread(), "benchmark task thread");
        ++this.tick;
        if (this.tick == this.warmup + 1) {
            this.sampleStart = System.nanoTime();
            this.startCpu = cpuTimes();
            this.startGcMillis = gcMillis();
            this.startGcCollections = gcCollections();
            this.startAllocatedBytes = allocatedBytes();
        }
        final long started = System.nanoTime();
        if (this.mode.equals("activation")) {
            // Temporary positions only: no network clients, packets, player ticking, or client simulation.
            // Removed before the normal server tick resumes, on the same main-thread call stack.
            this.level.players().addAll(this.players);
            try {
                for (int repeat = 0; repeat < this.repeats; ++repeat) {
                    for (int i = 0; i < this.entities.size(); ++i) {
                        this.entities.get(i).activatedTick = MinecraftServer.currentTick + (this.parityFixture && i % 13 == 0 ? 10 : -1);
                        if (this.parityFixture) this.entities.get(i).activatedPriority = 2 + i % 7;
                    }
                    ActivationRange.activateEntities(this.level);
                }
            } finally { this.level.players().removeAll(this.players); }
            this.checksum = 0;
            for (int i = 0; i < this.entities.size(); ++i) {
                if (this.entities.get(i).activatedTick == MinecraftServer.currentTick) this.checksum += i + 1;
            }
        } else if (this.mode.equals("sensor")) {
            for (int repeat = 0; repeat < this.repeats; ++repeat) this.sensor.scan(this.level, this.body);
            this.checksum = 1;
            final List<LivingEntity> nearest = this.body.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow();
            for (LivingEntity entity : nearest) this.checksum = this.checksum * 31 + this.indices.getOrDefault(entity, 0);
            require(nearest.size() == this.count, "sensor candidate count " + nearest.size() + " != " + this.count);
        } else if (this.mode.equals("potential")) {
            this.checksum = 1;
            for (int repeat = 0; repeat < this.repeats; ++repeat) {
                final BlockPos pos = new BlockPos((this.tick + repeat * 7) % 127, 73, (this.tick * 3 + repeat) % 131);
                final double result = this.potential.getPotentialEnergyChange(pos, 1.25);
                this.checksum = this.checksum * 31 + Double.doubleToLongBits(result);
            }
        }
        this.lastCallMs = (System.nanoTime() - started) / 1_000_000.0 / this.repeats;
        this.sampledTick = this.tick > this.warmup;
    }

    @EventHandler public void onTick(final ServerTickEndEvent event) {
        if (!this.sampledTick || this.done) return;
        require(Bukkit.isPrimaryThread(), "tick-end event thread");
        this.tickTimes.add(event.getTickDuration());
        this.callTimes.add(this.lastCallMs);
        this.sampledChecksums.add(this.checksum);
        this.sampledTick = false;
        if (this.tickTimes.size() == this.samples) finish();
    }

    private void finish() {
        this.done = true;
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "passed");
        result.put("server", Bukkit.getVersion());
        result.put("java", System.getProperty("java.runtime.version"));
        result.put("available_processors", Runtime.getRuntime().availableProcessors());
        result.put("mode", this.mode);
        result.put("parity_fixture", this.parityFixture);
        result.put("entities", this.entities.size());
        result.put("potential_charge_points", this.mode.equals("potential") ? this.count : 0);
        result.put("synthetic_player_positions", this.playerCount);
        result.put("layout", System.getProperty("bench.layout", "spread"));
        result.put("repetitions_per_tick", this.repeats);
        result.put("warmup_ticks", this.warmup);
        result.put("sample_ticks", this.samples);
        result.put("logical_checksum", this.checksum);
        result.put("sample_checksums", this.sampledChecksums);
        result.put("tick_ms", stats(this.tickTimes));
        result.put("call_ms", stats(this.callTimes));
        result.put("raw_tick_ms", this.tickTimes);
        result.put("raw_call_ms", this.callTimes);
        final long endAllocatedBytes = allocatedBytes();
        result.put("main_thread_allocated_bytes", this.startAllocatedBytes < 0 || endAllocatedBytes < 0 ? -1 : endAllocatedBytes - this.startAllocatedBytes);
        result.put("gc_collection_millis", gcMillis() - this.startGcMillis);
        result.put("gc_collections", gcCollections() - this.startGcCollections);
        result.put("observed_tps", Math.min(20.0, this.samples * 1_000_000_000.0 / (System.nanoTime() - this.sampleStart)));
        final Map<String, Long> cpu = cpuTimes();
        cpu.replaceAll((name, value) -> value - this.startCpu.getOrDefault(name, 0L));
        result.put("thread_cpu_nanos", cpu);
        try {
            result.put("esco_metrics", Class.forName("dev.escos.fork.EscoMetrics").getMethod("snapshot").invoke(null));
        } catch (ClassNotFoundException expectedOnPaper) { result.put("esco_metrics", Map.of()); }
        catch (ReflectiveOperationException error) { fail(error); return; }
        write(result);
        getLogger().info("BENCH_COMPLETE " + this.mode + " mean_mspt=" + stats(this.tickTimes).get("mean"));
        Bukkit.shutdown();
    }

    private Map<String, Long> cpuTimes() {
        final var bean = ManagementFactory.getThreadMXBean();
        final Map<String, Long> result = new TreeMap<>();
        if (!bean.isThreadCpuTimeSupported()) return result;
        if (!bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
        for (long id : bean.getAllThreadIds()) {
            final var info = bean.getThreadInfo(id);
            if (info != null) {
                result.merge(info.getThreadName(), Math.max(0, bean.getThreadCpuTime(id)), Long::sum);
            }
        }
        return result;
    }

    private static long allocatedBytes() {
        final var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean allocation) || !allocation.isThreadAllocatedMemorySupported()) return -1;
        if (!allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
        return allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }

    private static long gcCollections() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }

    private static Map<String, Double> stats(List<Double> values) {
        final double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return Map.of("mean", Arrays.stream(sorted).average().orElse(0), "p50", sorted[sorted.length / 2],
            "p95", sorted[Math.min(sorted.length - 1, (int)Math.ceil(sorted.length * .95) - 1)], "max", sorted[sorted.length - 1]);
    }
    private void fail(Throwable error) {
        this.done = true;
        error.printStackTrace();
        write(Map.of("status", "failed", "error", error.toString()));
        Bukkit.shutdown();
    }
    private void write(Map<String, ?> result) {
        try { Files.writeString(Path.of("benchmark-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static final class ExposedSensor extends NearestLivingEntitySensor<LivingEntity> {
        void scan(ServerLevel level, LivingEntity body) { super.doTick(level, body); }
    }
    private static final class FixturePlayer extends ServerPlayer {
        private final boolean spectator, afk;
        FixturePlayer(MinecraftServer server, ServerLevel level, GameProfile profile, boolean spectator, boolean afk) {
            super(server,level,profile,ClientInformation.createDefault()); this.spectator=spectator; this.afk=afk;
        }
        @Override public boolean isSpectator() { return this.spectator; }
        @Override public boolean isAfk() { return this.afk; }
    }
}

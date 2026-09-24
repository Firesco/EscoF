package dev.escos.fork.entity;

import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import dev.escos.fork.EscoConfig;
import dev.escos.fork.EscoMetrics;
import dev.escos.fork.concurrent.ParallelWork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** One synchronous capture/parallel-compute/synchronous-apply phase per world tick. */
public final class ParallelActivation {
    private static volatile boolean disabled;
    private static final ThreadLocal<Workspace> LOCAL = ThreadLocal.withInitial(Workspace::new);
    private ParallelActivation() {}

    private static final class Workspace {
        final List<Player> players = new ArrayList<>();
        final ActivationSnapshot snapshot = new ActivationSnapshot(0, 0);
        final int[] ranges = new int[8];
        Entity[] entities = new Entity[0];
        boolean inUse;

        void growEntities(final int required) {
            if (required > this.entities.length) {
                this.entities = new Entity[Math.max(required, this.entities.length + (this.entities.length >> 1))];
                EscoMetrics.ACTIVATION_BUFFER_GROWS.increment();
            }
        }
    }

    public static boolean tryActivate(final Level level, final int maxRange) {
        if (!EscoConfig.ACTIVATION || disabled || !(level instanceof ServerLevel world)
            || world.players().size() < EscoConfig.ACTIVATION_MIN_PLAYERS
            || !world.dragonParts().isEmpty()
            || org.dreeam.leaf.config.modules.opt.DynamicActivationofBrain.enabled) return false;
        TickThread.ensureTickThread(world, "Esco activation capture must run on the tick thread");
        final EntityLookup lookup = world.moonrise$getEntityLookup();
        if (lookup.getEntityCount() < EscoConfig.ACTIVATION_MIN_ENTITIES) return false;
        final Workspace workspace = LOCAL.get();
        if (workspace.inUse) return false;
        workspace.inUse = true;
        int count = 0;
        try {
            final long started = System.nanoTime();
            final List<Player> players = workspace.players;
            for (final Player player : world.players()) {
                if (world.spigotConfig.ignoreSpectatorActivation && player.isSpectator()) continue;
                if (!world.purpurConfig.idleTimeoutTickNearbyEntities && player.isAfk()) continue;
                players.add(player);
            }
            if (players.size() < EscoConfig.ACTIVATION_MIN_PLAYERS) return false;
            workspace.growEntities(lookup.getEntityCount());
            int copied;
            while ((copied = lookup.esco$copyAllInto(workspace.entities)) < 0) workspace.growEntities(-copied);
            count = copied;
            final Entity[] entities = workspace.entities;
            final ActivationSnapshot snapshot = workspace.snapshot;
            if (snapshot.ensureCapacity(count, players.size())) EscoMetrics.ACTIVATION_BUFFER_GROWS.increment();
            // The expensive independent Paper oracle is allocated only when verification is requested.
            final AABB[][] boxes = EscoConfig.VERIFY_ACTIVATION ? new AABB[players.size()][8] : null;
            final int[] ranges = workspace.ranges;
            ranges[0] = world.spigotConfig.waterActivationRange;
            ranges[1] = world.spigotConfig.flyingMonsterActivationRange;
            ranges[2] = world.spigotConfig.villagerActivationRange;
            ranges[3] = world.spigotConfig.monsterActivationRange;
            ranges[4] = world.spigotConfig.animalActivationRange;
            ranges[5] = world.spigotConfig.raiderActivationRange;
            ranges[6] = world.spigotConfig.miscActivationRange;
            ranges[7] = maxRange;
            final int minSection = world.getMinSectionY(), maxSection = world.getMaxSectionY();
            for (int p = 0; p < players.size(); ++p) {
                final AABB box = players.get(p).getBoundingBox();
                for (int type = 0; type < 8; ++type) {
                    inflateBox(box, ranges[type], world.getHeight(), snapshot.players, p * 48 + type * 6);
                    if (boxes != null) boxes[p][type] = box.inflate(ranges[type], world.getHeight(), ranges[type]);
                }
                final int b = p * 48 + 42, q = p * 6;
                snapshot.querySections[q] = (Mth.floor(snapshot.players[b]) - 2) >> 4;
                snapshot.querySections[q + 1] = (Mth.floor(snapshot.players[b + 3]) + 2) >> 4;
                snapshot.querySections[q + 2] = (Mth.floor(snapshot.players[b + 2]) - 2) >> 4;
                snapshot.querySections[q + 3] = (Mth.floor(snapshot.players[b + 5]) + 2) >> 4;
                snapshot.querySections[q + 4] = Mth.clamp(Mth.floor(snapshot.players[b + 1] - 4.0) >> 4, minSection, maxSection);
                snapshot.querySections[q + 5] = Mth.clamp(Mth.floor(snapshot.players[b + 4]) >> 4, minSection, maxSection);
            }
            snapshot.clipToBroadPhase();
            final int tick = MinecraftServer.currentTick;
            for (int i = 0; i < count; ++i) {
                final Entity entity = entities[i];
                // Plugin-provided Player/NPC subclasses can be visible without membership
                // in the world player list. Preserve their complete upstream activation path.
                if (entity instanceof Player && !world.players().contains(entity)) return false;
                final FullChunkStatus status = entity.moonrise$getChunkStatus();
                snapshot.needsPriorityReset[i] = false;
                if ((!world.paperConfig().entities.markers.tick && entity instanceof Marker)
                    || status == null || !status.isOrAfter(FullChunkStatus.FULL)) {
                    snapshot.types[i] = -1;
                    continue;
                }
                snapshot.needsPriorityReset[i] = entity.activatedPriority != 1;
                snapshot.types[i] = entity.activatedTick >= tick || entity instanceof Player ? -1
                    : (byte) (entity.defaultActivationState ? 7 : entity.activationType.ordinal());
                if (snapshot.types[i] < 0 && !snapshot.needsPriorityReset[i]) continue;
                copyBox(entity.getBoundingBox(), snapshot.entities, i * 6);
                snapshot.sections[i * 3] = entity.moonrise$getSectionX();
                snapshot.sections[i * 3 + 1] = Mth.clamp(entity.moonrise$getSectionY(), minSection, maxSection);
                snapshot.sections[i * 3 + 2] = entity.moonrise$getSectionZ();
            }
            // Workers receive only primitive data. All workers join before the caller mutates a world.
            try {
                ParallelWork.forRange(count, 512, snapshot::evaluate);
                if (boxes != null && !verify(world, entities, count, snapshot, boxes, tick)) {
                    disabled = true;
                    System.getLogger("Esco").log(System.Logger.Level.ERROR, "Activation parity mismatch; using Paper activation for the rest of this process");
                    return false;
                }
            } catch (RuntimeException exception) {
                disabled = true;
                System.getLogger("Esco").log(System.Logger.Level.ERROR, "Activation calculation failed; falling back to Paper", exception);
                return false;
            }
            for (final Player player : world.players()) player.activatedTick = tick;
            for (int i = 0; i < count; ++i) {
                if (snapshot.active[i] != 0) entities[i].activatedTick = tick;
                if (snapshot.priorityReset[i] != 0) entities[i].activatedPriority = 1;
            }
            EscoMetrics.ACTIVATION_BATCHES.increment();
            EscoMetrics.ACTIVATION_ENTITIES.add(count);
            EscoMetrics.ACTIVATION_NANOS.add(System.nanoTime() - started);
            return true;
        } finally {
            // Never keep entities, players or their worlds alive through thread-local scratch storage.
            Arrays.fill(workspace.entities, 0, count, null);
            workspace.players.clear();
            workspace.inUse = false;
        }
    }

    private static void inflateBox(final AABB box, final double horizontal, final double vertical,
                                   final double[] target, final int offset) {
        // Match the AABB constructor's normalization even for negative configured ranges.
        target[offset] = Math.min(box.minX - horizontal, box.maxX + horizontal);
        target[offset + 1] = Math.min(box.minY - vertical, box.maxY + vertical);
        target[offset + 2] = Math.min(box.minZ - horizontal, box.maxZ + horizontal);
        target[offset + 3] = Math.max(box.minX - horizontal, box.maxX + horizontal);
        target[offset + 4] = Math.max(box.minY - vertical, box.maxY + vertical);
        target[offset + 5] = Math.max(box.minZ - horizontal, box.maxZ + horizontal);
    }

    private static boolean verify(final ServerLevel world, final Entity[] entities, final int count, final ActivationSnapshot snapshot,
                                  final AABB[][] boxes, final int tick) {
        final Set<Entity> expected = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Entity> expectedPriority = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final AABB[] player : boxes) {
            for (final Entity entity : world.getEntities((Entity) null, player[7], e -> true)) {
                if (!world.paperConfig().entities.markers.tick && entity instanceof Marker) continue;
                if (entity.activatedPriority != 1) expectedPriority.add(entity);
                if (entity instanceof Player || entity.activatedTick >= tick) continue;
                if (entity.defaultActivationState || player[entity.activationType.ordinal()].intersects(entity.getBoundingBox())) {
                    expected.add(entity);
                }
            }
        }
        boolean matches = true;
        for (int i = 0; i < count; ++i) {
            if (expected.remove(entities[i]) != (snapshot.active[i] != 0)) matches = false;
            if (expectedPriority.remove(entities[i]) != (snapshot.priorityReset[i] != 0)) matches = false;
        }
        matches &= expected.isEmpty() && expectedPriority.isEmpty();
        EscoMetrics.ACTIVATION_VERIFICATIONS.increment();
        if (!matches) EscoMetrics.ACTIVATION_MISMATCHES.increment();
        return matches;
    }

    private static void copyBox(final AABB box, final double[] target, final int offset) {
        target[offset] = box.minX; target[offset + 1] = box.minY; target[offset + 2] = box.minZ;
        target[offset + 3] = box.maxX; target[offset + 4] = box.maxY; target[offset + 5] = box.maxZ;
    }
}

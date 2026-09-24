package dev.escos.fork.entity;

/** Primitive-only activation input. A worker cannot reach an Entity, Level, or plugin. */
public final class ActivationSnapshot {
    public double[] entities = new double[0];
    public int[] sections = new int[0];
    public byte[] types = new byte[0];
    public byte[] active = new byte[0];
    public boolean[] needsPriorityReset = new boolean[0];
    public byte[] priorityReset = new byte[0];
    public double[] players = new double[0];
    public int[] querySections = new int[0];
    public int playerCount;

    public ActivationSnapshot(final int entityCount, final int playerCount) {
        ensureCapacity(entityCount, playerCount);
    }

    /** Called by the owning thread only, after the previous batch has fully joined. */
    public boolean ensureCapacity(final int entityCount, final int playerCount) {
        boolean grew = false;
        if (entityCount > this.active.length) {
            final int capacity = Math.max(entityCount, this.active.length + (this.active.length >> 1));
            this.entities = new double[Math.multiplyExact(capacity, 6)];
            this.sections = new int[Math.multiplyExact(capacity, 3)];
            this.types = new byte[capacity];
            this.active = new byte[capacity];
            this.needsPriorityReset = new boolean[capacity];
            this.priorityReset = new byte[capacity];
            grew = true;
        }
        if (playerCount > this.querySections.length / 6) {
            final int capacity = Math.max(playerCount, this.querySections.length / 4);
            this.players = new double[Math.multiplyExact(capacity, 48)];
            this.querySections = new int[Math.multiplyExact(capacity, 6)];
            grew = true;
        }
        this.playerCount = playerCount;
        return grew;
    }

    /** Fold both AABB predicates into their exact per-axis bounds; do not normalize the result. */
    public void clipToBroadPhase() {
        for (int player = 0; player < this.playerCount; ++player) {
            final int broad = player * 48 + 42;
            for (int type = 0; type < 7; ++type) {
                final int box = player * 48 + type * 6;
                for (int axis = 0; axis < 3; ++axis) {
                    this.players[box + axis] = Math.max(this.players[box + axis], this.players[broad + axis]);
                    this.players[box + axis + 3] = Math.min(this.players[box + axis + 3], this.players[broad + axis + 3]);
                }
            }
        }
    }

    /** Types 0..6 are Paper activation types; 7 means default-active; -1 means skip. */
    public void evaluate(final int from, final int to) {
        for (int entity = from; entity < to; ++entity) {
            // Buffers are reused. Every visited result must be reset, including skipped entities.
            this.active[entity] = 0;
            this.priorityReset[entity] = 0;
            final int type = this.types[entity];
            if (type < 0 && !this.needsPriorityReset[entity]) continue;
            final int e = entity * 6;
            final int s = entity * 3;
            final int sx = this.sections[s], sy = this.sections[s + 1], sz = this.sections[s + 2];
            for (int player = 0; player < this.playerCount; ++player) {
                final int q = player * 6;
                if (sx < this.querySections[q] || sx > this.querySections[q + 1]
                    || sz < this.querySections[q + 2] || sz > this.querySections[q + 3]
                    || sy < this.querySections[q + 4] || sy > this.querySections[q + 5]) continue;
                if (this.needsPriorityReset[entity] && intersects(this.entities, e, this.players, player * 48 + 42)) {
                    this.priorityReset[entity] = 1;
                    if (type < 0) break;
                }
                if (type >= 0 && intersects(this.entities, e, this.players, player * 48 + type * 6)) {
                    this.active[entity] = 1;
                    break;
                }
            }
        }
    }

    private static boolean intersects(final double[] a, final int x, final double[] b, final int y) {
        return a[x] < b[y + 3] && a[x + 3] > b[y]
            && a[x + 1] < b[y + 4] && a[x + 4] > b[y + 1]
            && a[x + 2] < b[y + 5] && a[x + 5] > b[y + 2];
    }
}

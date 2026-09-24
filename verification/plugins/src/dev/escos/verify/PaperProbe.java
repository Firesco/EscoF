package dev.escos.verify;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads through paper-plugin.yml and exercises native schedulers, chunk futures and events. */
public final class PaperProbe extends JavaPlugin implements Listener {
    private final AtomicInteger checks = new AtomicInteger();
    private final AtomicInteger callbacks = new AtomicInteger();
    private boolean tickEvent, failed;
    @Override public void onEnable() {
        check(Bukkit.isPrimaryThread(), "Paper plugin enable thread");
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { probe(); } catch (Throwable error) { failed(error); }
        }, 65);
    }
    private void probe() throws Exception {
        var world = Bukkit.getWorlds().getFirst();
        Bukkit.getGlobalRegionScheduler().execute(this, () -> {
            check(Bukkit.isPrimaryThread(), "global scheduler"); this.callbacks.incrementAndGet();
        });
        Bukkit.getRegionScheduler().execute(this, world, 0, 0, () -> {
            check(Bukkit.isPrimaryThread(), "region scheduler on Paper"); this.callbacks.incrementAndGet();
        });
        Bukkit.getAsyncScheduler().runNow(this, task -> {
            check(!Bukkit.isPrimaryThread(), "native async scheduler"); this.callbacks.incrementAndGet();
        });
        world.getChunkAtAsync(7,7).thenAccept(chunk -> Bukkit.getScheduler().runTask(this, () -> {
            check(chunk.isLoaded(), "async chunk completion"); this.callbacks.incrementAndGet();
        }));
        Pig entity = world.spawn(new Location(world,2,90,2), Pig.class, e -> {e.setAI(false);e.setGravity(false);});
        entity.getScheduler().run(this, task -> {
            check(Bukkit.isPrimaryThread(), "entity scheduler on Paper"); entity.remove(); this.callbacks.incrementAndGet();
        }, () -> failed(new AssertionError("entity retired before scheduled task")));
        final var reconstruct = PathFinder.class.getDeclaredMethod("reconstructPath",Node.class,BlockPos.class,boolean.class);
        reconstruct.setAccessible(true);
        for (int size : new int[] {1,2,32,1024}) {
            Node[] nodes = new Node[size];
            for (int i=0;i<size;++i) { nodes[i]=new Node(i,70,0); if(i>0) nodes[i].cameFrom=nodes[i-1]; }
            var result = (net.minecraft.world.level.pathfinder.Path) reconstruct.invoke(new PathFinder(new net.minecraft.world.level.pathfinder.WalkNodeEvaluator(),1024),nodes[size-1],new BlockPos(size-1,70,0),true);
            check(result.getNodeCount()==size && result.canReach(),"path length/reached flag");
            for(int i=0;i<size;++i) check(result.getNode(i)==nodes[i],"path node identity/order");
        }
        probePotential();
        PaperProbeOptimizations.run(world, this::check);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                check(this.callbacks.get()==5,"all native scheduler/chunk callbacks completed");
                check(this.tickEvent,"Paper tick-end event");
                if (!this.failed) {
                    Files.writeString(Path.of("compat-paper.json"),"{\"status\":\"passed\",\"checks\":"+this.checks.get()+"}");
                    getLogger().info("PAPER_PROBE_PASS checks="+this.checks.get());
                }
            } catch(Throwable error) { failed(error); }
        },60);
    }
    private void probePotential() {
        final var calculator = new net.minecraft.world.level.PotentialCalculator();
        final var positions = new java.util.ArrayList<BlockPos>();
        final var charges = new java.util.ArrayList<Double>();
        final var random = new java.util.Random(260204);
        for (int n : new int[] {16, 4095, 4096, 8193, 32767, 32768, 65537}) {
            while (positions.size() < n) {
                final BlockPos pos = new BlockPos(random.nextInt(), random.nextInt(), random.nextInt());
                final double charge = random.nextDouble() + 0.01;
                positions.add(pos); charges.add(charge); calculator.addCharge(pos, charge);
            }
            for (int q = 0; q < 5; ++q) {
                final BlockPos target = q == 0 ? positions.getFirst() : new BlockPos(q, Integer.MIN_VALUE, Integer.MAX_VALUE);
                checkPotential(calculator, positions, charges, target);
            }
        }
        final var mutable = new BlockPos.MutableBlockPos(12, 73, -21);
        calculator.addCharge(mutable, 3.25); positions.add(mutable); charges.add(3.25);
        checkPotential(calculator, positions, charges, new BlockPos(0, 80, 0));
        mutable.set(Integer.MIN_VALUE, Integer.MAX_VALUE, 17);
        checkPotential(calculator, positions, charges, new BlockPos(0, 80, 0));
        final BlockPos custom = new BlockPos(1, 2, 3) {
            @Override public double distSqr(net.minecraft.core.Vec3i other) { return 4.0; }
        };
        calculator.addCharge(custom, 8); positions.add(custom); charges.add(8.0);
        checkPotential(calculator, positions, charges, new BlockPos(0, 80, 0));
        check(calculator.getPotentialEnergyChange(new BlockPos(0, 0, 0), -0.0) == 0.0, "zero potential charge");
    }
    private void checkPotential(net.minecraft.world.level.PotentialCalculator calculator,
                                java.util.List<BlockPos> positions, java.util.List<Double> charges, BlockPos target) {
        double expected = 0.0;
        for (int i = 0; i < positions.size(); ++i) {
            final double distance = positions.get(i).distSqr(target);
            expected += distance == 0.0 ? Double.POSITIVE_INFINITY : charges.get(i) / Math.sqrt(distance);
        }
        check(Double.doubleToLongBits(calculator.getPotentialEnergyChange(target, 1.25))
            == Double.doubleToLongBits(expected * 1.25), "potential exact sum / cache / mutable / custom position");
    }
    @EventHandler public void tick(ServerTickEndEvent event) {
        if(!Bukkit.isPrimaryThread() || event.isAsynchronous()) failed(new AssertionError("tick event moved off thread"));
        this.tickEvent=true;
    }
    private void check(boolean value,String reason) { if(!value) throw new AssertionError(reason);this.checks.incrementAndGet(); }
    private void failed(Throwable error) {
        this.failed=true; error.printStackTrace();
        try { Files.writeString(Path.of("compat-paper.json"),"{\"status\":\"failed\"}"); }
        catch(Exception writeError) { throw new RuntimeException(writeError); }
    }
}

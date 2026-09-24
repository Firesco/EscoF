package dev.escos.verify;

import ca.spottedleaf.moonrise.patches.poi_lookup.PoiAccess;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.village.poi.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;

final class PaperProbeOptimizations {
    private static final class Scan extends NearestLivingEntitySensor<LivingEntity> {
        void scan(ServerLevel level, LivingEntity body) { doTick(level, body); }
    }
    static void run(org.bukkit.World world, BiConsumer<Boolean,String> check) throws Exception {
        ServerLevel level = ((CraftWorld) world).getHandle();
        ArrayList<org.bukkit.entity.Villager> spawned = new ArrayList<>();
        for (int x = 15; x <= 16; ++x) for (int z = 15; z <= 16; ++z) world.getChunkAt(x,z);
        try {
            for (int i = 0; i < 24; ++i) spawned.add(world.spawn(new Location(world,256+i%8-4,100,256+i/8-1),
                org.bukkit.entity.Villager.class, e -> { e.setAI(false); e.setGravity(false); }));
            LivingEntity body = ((CraftLivingEntity) spawned.getFirst()).getHandle();
            body.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32);
            Scan scan = new Scan(); scan.scan(level, body);
            List<LivingEntity> first = body.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow();
            check.accept(first.size() >= 23 && !first.contains(body), "sensor candidate completeness and self exclusion");
            double previous = -1;
            for (LivingEntity entity : first) {
                double distance = body.distanceToSqr(entity);
                check.accept(distance >= previous, "sensor ascending distance"); previous = distance;
            }
            var visible = body.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseThrow();
            check.accept(visible.findClosest(e -> false).isEmpty(), "false visibility filter");
            for (LivingEntity entity : first) {
                boolean expected = Sensor.isEntityTargetable(level,body,entity);
                check.accept(visible.contains(entity) == expected, "visibility result parity");
                check.accept(visible.contains(entity) == expected, "cached visibility result parity");
            }
            List<LivingEntity> retained = new ArrayList<>(first);
            scan.scan(level,body);
            var second = body.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElseThrow();
            check.accept(first != second && first.equals(retained), "published candidate list ownership");
        } finally { for (var entity : spawned) entity.remove(); }

        PoiManager manager = level.getPoiManager();
        BlockPos center = new BlockPos(-64,70,-64);
        for (int x = -7; x <= -1; ++x) for (int z = -7; z <= -1; ++z) world.getChunkAt(x,z);
        compare(manager,center,32,0,check);
        ArrayList<BlockPos> added = new ArrayList<>();
        var registry = level.registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE);
        var acquire = PoiRecord.class.getDeclaredMethod("acquireTicket"); acquire.setAccessible(true);
        try {
            for (int i = 0; i < 24; ++i) {
                BlockPos pos = center.offset((i%5)*12-24,(i/5)*6,(i%3)*11-11);
                PoiRecord record = manager.add(pos,registry.getOrThrow(i%2==0 ? PoiTypes.HOME : PoiTypes.MEETING));
                added.add(pos);
                if (i%3==0) acquire.invoke(record);
            }
            for (int test = 0; test < 60; ++test) compare(manager,center,new int[]{0,12,32,48,64}[test%5],test,check);
            boolean threw = false;
            try {
                PoiAccess.findNearestPoiRecords(manager, type -> {throw new IllegalStateException("expected");},
                    pos -> true,center,64,4096,PoiManager.Occupancy.ANY,false,10,new ArrayList<>());
            } catch (IllegalStateException expected) { threw = true; }
            check.accept(threw,"POI predicate exception propagation");
            compare(manager,center,64,7,check);
        } finally { for (BlockPos pos : added) manager.remove(pos); }
        compare(manager,center,32,0,check);
    }
    private static void compare(PoiManager manager, BlockPos center, int range, int test,
                                BiConsumer<Boolean,String> check) {
        List<PoiRecord> expected = new ArrayList<>(), actual = new ArrayList<>();
        List<BlockPos> expectedCalls = new ArrayList<>(), actualCalls = new ArrayList<>();
        Predicate<Holder<PoiType>> type = holder -> test%2==0 || holder.is(PoiTypes.HOME);
        Predicate<BlockPos> a = pos -> {expectedCalls.add(pos); return (pos.getX()+test)%3 != 0;};
        Predicate<BlockPos> b = pos -> {actualCalls.add(pos); return (pos.getX()+test)%3 != 0;};
        PoiManager.Occupancy occupancy = PoiManager.Occupancy.values()[test%3];
        int max = 1+test%9; boolean load = test%2==0;
        PaperProbePoiReference.findNearestPoiRecords(manager,type,a,center,range,(double)range*range,occupancy,load,max,expected);
        PoiAccess.findNearestPoiRecords(manager,type,b,center,range,(double)range*range,occupancy,load,max,actual);
        same(expected,actual,check);
        check.accept(expectedCalls.equals(actualCalls),"nearest POI predicate order");
        expected.clear();actual.clear();expectedCalls.clear();actualCalls.clear();
        PaperProbePoiReference.findClosestPoiDataRecords(manager,type,(t,p)->a.test(p),center,range,(double)range*range,occupancy,load,expected);
        PoiAccess.findClosestPoiDataRecords(manager,type,(t,p)->b.test(p),center,range,(double)range*range,occupancy,load,actual);
        same(expected,actual,check);
        check.accept(expectedCalls.equals(actualCalls),"closest POI predicate order");
    }
    private static void same(List<PoiRecord> expected,List<PoiRecord> actual,BiConsumer<Boolean,String> check) {
        check.accept(expected.size()==actual.size(),"POI result count");
        for (int i=0;i<expected.size();++i) check.accept(expected.get(i)==actual.get(i),"POI identity and order");
    }
}

package dev.escos.verify;

import com.google.gson.GsonBuilder;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommunityProbe extends JavaPlugin {
    private final Map<String,Object> result = new LinkedHashMap<>();
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { probe(); } catch(Throwable error) { failed(error); }
        },80);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private void probe() throws Exception {
        check(Bukkit.isPrimaryThread(),"main thread");
        for(String name:new String[]{"LuckPerms","Vault","WorldEdit"}) {
            var plugin=Bukkit.getPluginManager().getPlugin(name);
            check(plugin!=null && plugin.isEnabled(),name+" enabled");
            this.result.put(name,plugin.getPluginMeta().getVersion());
        }
        var permission=Bukkit.getServicesManager().getRegistration(Permission.class);
        check(permission!=null && permission.getProvider().getName().equals("LuckPerms"),"Vault LuckPerms permission bridge");
        this.result.put("vault_permission_bridge",true);
        var world=Bukkit.getWorlds().getFirst();
        try(var session=WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            session.setBlock(BlockVector3.at(12,90,12),BlockTypes.DIAMOND_BLOCK.getDefaultState());
        }
        check(world.getBlockAt(12,90,12).getType()==Material.DIAMOND_BLOCK,"WorldEdit block edit and flush");
        this.result.put("worldedit_block_edit",true);
        Class apiType=Bukkit.getServicesManager().getKnownServices().stream()
            .filter(type->type.getName().equals("net.luckperms.api.LuckPerms")).findFirst().orElseThrow();
        Object api=Bukkit.getServicesManager().load(apiType);
        var getter=apiType.getMethod("getGroupManager");
        Class groupManagerType=getter.getReturnType();
        Object manager=getter.invoke(api);
        CompletableFuture<?> create=(CompletableFuture<?>)groupManagerType.getMethod("createAndLoadGroup",String.class).invoke(manager,"esco_verification");
        create.whenComplete((group,error)->{
            if(error!=null) { failed(error);return; }
            try {
                Class groupType=Class.forName("net.luckperms.api.model.group.Group",true,apiType.getClassLoader());
                check("esco_verification".equals(groupType.getMethod("getName").invoke(group)),"LuckPerms group create/load");
                CompletableFuture<?> save=(CompletableFuture<?>)groupManagerType.getMethod("saveGroup",groupType).invoke(manager,group);
                save.whenComplete((ignored,saveError)->{
                    if(saveError!=null) {failed(saveError);return;}
                    Bukkit.getScheduler().runTask(this,()->{
                        this.result.put("luckperms_group_persistence",true);
                        this.result.put("status","passed");
                        write();
                        getLogger().info("COMMUNITY_PROBE_PASS "+this.result);
                    });
                });
            } catch(Throwable failure) { failed(failure); }
        });
    }
    private static void check(boolean condition,String reason) {if(!condition)throw new AssertionError(reason);}
    private void failed(Throwable error) {
        error.printStackTrace();this.result.put("status","failed");this.result.put("error",error.toString());write();
    }
    private synchronized void write() {
        try {Files.writeString(Path.of("compat-community.json"),new GsonBuilder().setPrettyPrinting().create().toJson(this.result));}
        catch(Exception error) {throw new RuntimeException(error);}
    }
}

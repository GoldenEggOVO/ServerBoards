package dev.server.boards;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import java.util.*;
/** Restore only collision flags owned by the board session; no external scoreboard dependency. */
final class TableComfort implements Listener,AutoCloseable {
    private final ServerBoards plugin;private final Map<UUID,Boolean> previous=new HashMap<>();
    TableComfort(ServerBoards plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);}
    void sync(){for(Player p:Bukkit.getOnlinePlayers()){Room r=plugin.room(p);boolean active=r!=null&&plugin.arena.atTableWorld(p,r);if(active){previous.putIfAbsent(p.getUniqueId(),p.isCollidable());p.setCollidable(false);}else restore(p);}}
    private void restore(Player p){Boolean value=previous.remove(p.getUniqueId());if(value!=null)p.setCollidable(value);}
    @EventHandler public void hunger(FoodLevelChangeEvent event){if(event.getEntity() instanceof Player p&&plugin.room(p)!=null)event.setCancelled(true);}
    public void close(){for(Player p:Bukkit.getOnlinePlayers())restore(p);previous.clear();}
}

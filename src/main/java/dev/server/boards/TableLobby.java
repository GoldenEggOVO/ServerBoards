package dev.server.boards;

import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Public seats and table-only picking. No private hands or extra collision entities. */
final class TableLobby implements Listener, AutoCloseable {
    record Entry(String id, Location center, double radius, double height, String name,
                 String status, List<String> names, int capacity, Set<UUID> members,
                 TextDisplay nativeLabel, boolean board, Consumer<Player> join, Consumer<Player> menu) {
        int empty(){return Math.max(0,capacity-names.size());}
    }
    private final ServerBoards plugin;
    private final Map<String,TextDisplay> labels=new HashMap<>();
    private final Map<UUID,Long> clicks=new HashMap<>();
    private List<Entry> entries=List.of();
    private org.bukkit.scheduler.BukkitTask task;
    private boolean readFailed;
    TableLobby(ServerBoards plugin){this.plugin=plugin;}
    void start(){Bukkit.getPluginManager().registerEvents(this,plugin);task=Bukkit.getScheduler().runTaskTimer(plugin,this::refresh,100,20);}

    static String roster(List<String> names,int capacity){
        return "桌内："+(names.isEmpty()?"暂无玩家":String.join("、",names))+"\n空位："+Math.max(0,capacity-names.size())+" / "+capacity;
    }
    static Component text(Entry e){
        return Component.text(e.name,NamedTextColor.GOLD).append(Component.newline())
                .append(Component.text(e.status,NamedTextColor.WHITE)).append(Component.newline())
                .append(Component.text(roster(e.names,e.capacity),NamedTextColor.WHITE)).append(Component.newline())
                .append(Component.text("潜行右键：加入 / 房间菜单",NamedTextColor.GRAY));
    }
    List<Entry> collect()throws ReflectiveOperationException{
        List<Entry> out=new ArrayList<>();
        for(Room r:plugin.rooms.values()){
            Location c=plugin.arena.center(r.table);
            out.add(new Entry("board:"+r.id,c,1.1,TableGeometry.SURFACE,ServerBoards.gameName(r.kind),
                    r.phase==Room.Phase.LOBBY?"等候":r.phase==Room.Phase.FINISHED?"已结束":"进行中",
                    r.seats.stream().map(Room.Seat::name).toList(),r.capacity,
                    new HashSet<>(r.seats.stream().map(Room.Seat::id).toList()),null,true,p->plugin.join(p,r),p->plugin.menus.room(p,r)));
        }
        return out;
    }
    void refresh(){
        try{entries=List.copyOf(collect());readFailed=false;}
        catch(ReflectiveOperationException|RuntimeException ex){if(!readFailed)plugin.getLogger().log(java.util.logging.Level.WARNING,"读取桌上公开座位失败",ex);readFailed=true;return;}
        Set<String> alive=new HashSet<>();
        for(Entry e:entries){
            if(e.board)continue;
            TextDisplay label=e.nativeLabel;
            if(label==null){
                alive.add(e.id);label=labels.get(e.id);
                if(label==null||!label.isValid()){
                    label=e.center.getWorld().spawn(e.center.clone().add(0,e.height+1.45,0),TextDisplay.class,d->{
                        d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);d.setBillboard(Display.Billboard.CENTER);
                        d.setLineWidth(420);d.setViewRange(.6f);d.setAlignment(TextDisplay.TextAlignment.CENTER);
                    });labels.put(e.id,label);
                }
            }
            // Keep Mahjong's short native countdown visible; its action bar also counts down.
            if(e.nativeLabel!=null&&label.text()!=null&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(label.text()).contains("秒后开始"))continue;
            Component value=text(e);if(!value.equals(label.text()))label.text(value);
        }
        for(String id:List.copyOf(labels.keySet()))if(!alive.contains(id))labels.remove(id).remove();
    }
    static double hit(Entry e,Location eye,Vector direction){
        if(!Objects.equals(e.center.getWorld(),eye.getWorld()))return Double.POSITIVE_INFINITY;
        var c=e.center;var box=new BoundingBox(c.getX()-e.radius,c.getY(),c.getZ()-e.radius,c.getX()+e.radius,c.getY()+e.height,c.getZ()+e.radius);
        var hit=box.rayTrace(eye.toVector(),direction,TableGeometry.REACH);
        return hit==null?Double.POSITIVE_INFINITY:hit.getHitPosition().distance(eye.toVector());
    }
    @EventHandler(priority=EventPriority.LOWEST) public void use(PlayerInteractEvent e){
        if(e.getHand()==EquipmentSlot.HAND&&e.getAction().isRightClick()&&request(e.getPlayer()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void entity(PlayerInteractEntityEvent e){
        if(e.getHand()==EquipmentSlot.HAND&&request(e.getPlayer()))e.setCancelled(true);
    }
    boolean request(Player p){
        if(!p.isSneaking()||!plugin.allowed(p))return false;
        Location eye=p.getEyeLocation();Vector dir=eye.getDirection();Entry target=null;double nearest=Double.POSITIVE_INFINITY;
        for(Entry e:entries){double distance=hit(e,eye,dir);if(distance<nearest){nearest=distance;target=e;}}
        if(target==null)return false;
        var block=p.getWorld().rayTraceBlocks(eye,dir,Math.max(.001,nearest-.05),FluidCollisionMode.NEVER,true);
        if(block!=null)return false;
        long now=System.nanoTime();if(now-clicks.getOrDefault(p.getUniqueId(),0L)<250_000_000L)return true;clicks.put(p.getUniqueId(),now);
        String id=target.id;
        // Defer so this joining click cannot also select a card in the newly joined provider.
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline()||!plugin.allowed(p))return;
            try{
                Entry live=collect().stream().filter(e->e.id.equals(id)).findFirst().orElse(null);
                if(live==null){plugin.tell(p,"这张桌子已被删除。");return;}
                if(live.members.contains(p.getUniqueId())){live.menu.accept(p);return;}
                if(plugin.room(p)!=null){plugin.tell(p,"请先离开当前对局。");return;}
                if(!live.status.equals("等候")){plugin.tell(p,"这张桌子已开局，暂时不能加入。");return;}
                if(live.empty()==0){plugin.tell(p,"这张桌子已经满员。");return;}
                live.join.accept(p);refresh();
            }catch(ReflectiveOperationException ex){plugin.getLogger().log(java.util.logging.Level.WARNING,"加入桌位接口失败",ex);plugin.tell(p,"桌位接口暂不可用。");}
            catch(IllegalArgumentException|IllegalStateException ex){plugin.tell(p,ex.getMessage());}
        });return true;
    }
    @EventHandler public void quit(PlayerQuitEvent e){clicks.remove(e.getPlayer().getUniqueId());}
    @Override public void close(){if(task!=null)task.cancel();labels.values().forEach(Entity::remove);labels.clear();entries=List.of();HandlerList.unregisterAll(this);}
}

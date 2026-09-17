package dev.server.boards;

import dev.server.boards.rules.BoardGame;
import dev.server.boards.rules.Cell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Public boards only: private card views never enter this renderer. */
final class GameWorld implements Listener, AutoCloseable {
    private static final String OWNER_MARKER = "ServerBoards arena v1\n";
    private static final TextColor[] COLORS = {
        TextColor.color(0xdd6256), TextColor.color(0x5ab7e1), TextColor.color(0x61c68d),
        TextColor.color(0xefbf57), TextColor.color(0xb796e9), TextColor.color(0xf0e7d2)
    };
    private final ServerBoards plugin;
    World world;
    private final NamespacedKey tag;
    private final Map<UUID,TableView> views = new HashMap<>();
    private final TableMaps maps;
    private final Map<UUID,Long> clicks=new HashMap<>();
    private org.bukkit.scheduler.BukkitTask pointerTask;
    private final Map<UUID,Pick> selections = new HashMap<>();
    private final Map<Integer,Set<Chunk>> tableChunks = new HashMap<>();
    private final Set<Long> lobbyChunks = new HashSet<>();

    record Pick(UUID room,long revision,String source,List<String> actions) {}
    record Layout(double xUnit,double zUnit,double midX,double midY) {
        double x(Cell cell) { return (cell.x()-midX)*xUnit; }
        double z(Cell cell) { return (cell.y()-midY)*zUnit; }
        double hitWidth(String kind) { return kind.equals("checkers")?xUnit*1.65:Math.min(xUnit,zUnit)*.88; }
    }
    record Paint(Component text,Color background,boolean selected) {}
    GameWorld(ServerBoards plugin) {
        this.plugin=plugin;maps=new TableMaps(plugin);tag=new NamespacedKey("boards","board-cell");
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }

    void initialize() {
        // Portable rooms own their world anchors; no dedicated dimension is created.
        pointerTask=Bukkit.getScheduler().runTaskTimer(plugin,this::pointers,2,2);
    }
    static Path dimensionFolder(Path levelDirectory,NamespacedKey key) {
        return levelDirectory.toAbsolutePath().normalize().resolve("dimensions").resolve(key.getNamespace()).resolve(key.getKey()).normalize();
    }
    /** Read-only: never adopts an existing directory or triggers legacy migration. */
    static UUID preflightOwner(Path legacy,Path dimension) {
        if(!legacy.equals(dimension)&&Files.exists(legacy,LinkOption.NOFOLLOW_LINKS)) {
            readOwner(legacy); // Unmarked old worlds fail before CraftBukkit can migrate them.
            throw new IllegalStateException("检测到旧目录格式的棋牌世界；请先备份并完成管理员迁移，插件不会自动移动世界");
        }
        if(!Files.exists(dimension,LinkOption.NOFOLLOW_LINKS))return null;
        return readOwner(dimension);
    }
    static UUID readOwner(Path folder) {
        Path marker=folder.resolve(".servergames-owner");
        if(Files.isSymbolicLink(folder)||!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("已有世界不属于棋牌插件；未修改，请配置新的空世界名称");
        try {
            String value=Files.readString(marker,StandardCharsets.UTF_8);
            if(!value.startsWith(OWNER_MARKER))throw new IllegalStateException("棋牌世界归属标记无效，未修改世界");
            return UUID.fromString(value.substring(OWNER_MARKER.length()));
        }catch(IOException|IllegalArgumentException ex){throw new IllegalStateException("不能核对棋牌世界归属，未修改世界",ex);}
    }
    private static final class VoidGenerator extends ChunkGenerator {
        @Override public Location getFixedSpawnLocation(World world,Random random){return new Location(world,0,80,0);}
        @Override public boolean shouldGenerateNoise(){return false;}
        @Override public boolean shouldGenerateSurface(){return false;}
        @Override public boolean shouldGenerateBedrock(){return false;}
        @Override public boolean shouldGenerateCaves(){return false;}
        @Override public boolean shouldGenerateDecorations(){return false;}
        @Override public boolean shouldGenerateMobs(){return false;}
        @Override public boolean shouldGenerateStructures(){return false;}
    }
    Location center(int index){
        Room r=plugin.rooms.values().stream().filter(v->v.table==index).findFirst().orElse(null);
        if(r!=null&&r.anchorWorld!=null){World w=Bukkit.getWorld(r.anchorWorld);if(w==null)throw new IllegalStateException("桌位世界未加载");return TablePlacement.snap(new Location(w,r.anchorX,r.anchorY,r.anchorZ),2);}
        throw new IllegalStateException("房间缺少可用的世界坐标");
    }
    boolean atTableWorld(Player p,Room r){return p.getWorld().equals(center(r.table).getWorld());}
    void anchor(Room r,Location location){r.anchorWorld=location.getWorld().getUID();Location snapped=TablePlacement.snap(location,2);r.anchorX=snapped.getX();r.anchorY=snapped.getY();r.anchorZ=snapped.getZ();}

    Location externalPlatform(int index){
        if(world==null||index<0||index>=5)throw new IllegalArgumentException("无效卡牌桌号");
        Location center=new Location(world,-64-index*32+.5,80,-32+.5);
        lobbyChunks.addAll(floor(center,11));return center;
    }
    void platform(int index) {
        
        if(index<0||index>127)throw new IllegalArgumentException("无效桌号");
        Location c=center(index);Set<Chunk> chunks=new HashSet<>();
        for(int x=(c.getBlockX()-4)>>4;x<=((c.getBlockX()+4)>>4);x++)for(int z=(c.getBlockZ()-4)>>4;z<=((c.getBlockZ()+4)>>4);z++){c.getWorld().getChunkAt(x,z).addPluginChunkTicket(plugin);chunks.add(c.getWorld().getChunkAt(x,z));}
        tableChunks.put(index,chunks);
    }
    private Set<Long> floor(Location center,int radius) {
        Set<Long> chunks=new HashSet<>();
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++) {
            var block=world.getBlockAt(center.getBlockX()+x,79,center.getBlockZ()+z);
            // Never replace a block, including later administrator builds in our own arena.
            if(block.getType().isAir())block.setType(Math.abs(x)==radius||Math.abs(z)==radius?Material.DEEPSLATE_TILES:((x+z)&1)==0?Material.SMOOTH_STONE:Material.POLISHED_ANDESITE,false);
            chunks.add(chunkKey(block.getX()>>4,block.getZ()>>4));
        }
        for(long key:chunks)world.getChunkAt(chunkX(key),chunkZ(key)).addPluginChunkTicket(plugin);
        return Set.copyOf(chunks);
    }
    private static long chunkKey(int x,int z){return((long)x<<32)|(z&0xffffffffL);}
    private static int chunkX(long key){return(int)(key>>32);}
    private static int chunkZ(long key){return(int)key;}
    Location seatLocation(Room room,int seat) {
        Location c=center(room.table);double angle=2*Math.PI*Math.max(0,seat)/Math.max(2,room.capacity);
        double dx=Math.sin(angle)*2.25,dz=Math.cos(angle)*2.25;
        if(room.kind.equals("chess")||room.kind.equals("xiangqi")){dx=-dx;dz=-dz;}
        if(room.kind.equals("aeroplane")){int[] colors=room.capacity==2?new int[]{0,2}:room.capacity==3?new int[]{0,1,2}:new int[]{0,1,2,3};double a=-3*Math.PI/4-colors[Math.floorMod(seat,colors.length)]*Math.PI/2;dx=Math.sin(a)*2.25;dz=Math.cos(a)*2.25;}
        Location location=c.add(dx,0,dz).setDirection(new Vector(-dx,TableGeometry.SURFACE+.05-1.62,-dz));
        if(!location.getBlock().isPassable()||!location.clone().add(0,1,0).getBlock().isPassable())throw new IllegalArgumentException("座位被方块挡住，请换一个更开阔的位置。");
        return location;
    }

    static Layout layout(String kind,List<Cell> cells) {
        int minX=cells.stream().mapToInt(Cell::x).min().orElse(0),maxX=cells.stream().mapToInt(Cell::x).max().orElse(0);
        int minY=cells.stream().mapToInt(Cell::y).min().orElse(0),maxY=cells.stream().mapToInt(Cell::y).max().orElse(0);
        double unit=1.80/Math.max(maxX-minX+1,maxY-minY+1);
        if(kind.equals("yacht"))unit=.17; // Five wide dice slots stay inside the felt border.
        if(kind.equals("checkers")) {
            double scale=Math.min(1.80/((maxX-minX+2)*.5),1.80/((maxY-minY+1)*.8660254));
            return new Layout(.5*scale,.8660254*scale,(minX+maxX)/2.0,(minY+maxY)/2.0);
        }
        return new Layout(unit,unit,(minX+maxX)/2.0,(minY+maxY)/2.0);
    }

    void render(Room room) {
        if(room.board==null){removeView(room.id);return;}
        selections.values().removeIf(pick->pick.room().equals(room.id)&&pick.revision()!=room.revision);
        TableView view=views.get(room.id);
        if(view==null){view=new TableView(plugin,room,center(room.table),tag,maps);views.put(room.id,view);}
        else view.sync();
    }
    private String aimed(Player player,TableView view) {
        Location eye=player.getEyeLocation();Vector direction=eye.getDirection();
        if(view.geometry.kind.equals("connectfour"))return view.verticalHit(eye,direction);
        double distance=TableGeometry.intersection(eye.getY(),direction.getY(),view.origin.getY()+.03);
        TableView.Hit piece=view.hitPiece(eye,direction);
        String cell=null;
        if(piece!=null&&(distance<0||piece.distance()<distance)){distance=piece.distance();cell=piece.cell();}
        if(distance<0)return null;
        var obstacle=eye.getWorld().rayTraceBlocks(eye,direction,Math.max(.001,distance-.035),FluidCollisionMode.NEVER,true);
        if(obstacle!=null)return null;
        if(cell!=null)return cell;
        Vector hit=eye.toVector().add(direction.multiply(distance));
        return view.geometry.hit(hit.getX()-view.origin.getX(),hit.getZ()-view.origin.getZ());
    }
    private void pointers() {
        views.values().forEach(TableView::tick);
        for(Player player:Bukkit.getOnlinePlayers()){
            Room room=plugin.room(player);TableView view=room==null?null:views.get(room.id);
            if(view==null||!player.getWorld().equals(view.origin.getWorld()))continue;
            if(!plugin.allowed(player)||player.getEyeLocation().distanceSquared(view.origin)>36){view.clear(player);continue;}
            Pick pick=selections.get(player.getUniqueId());
            if(pick!=null&&(!pick.room().equals(room.id)||pick.revision()!=room.revision)){selections.remove(player.getUniqueId());pick=null;}
            view.cursor(player,pick,aimed(player,view));
        }
    }

    static Paint paint(String kind,Cell cell,Map<String,String> info,boolean selected) {
        boolean empty=cell.owner()<0;int colorId=actualColor(info,cell.owner());
        TextColor ink=empty?TextColor.color(0x778b87):COLORS[Math.floorMod(colorId,COLORS.length)];int background=0x233b36;
        String glyph=empty?"·":cell.piece(),coordinate=coordinate(kind,cell);
        switch(kind) {
            case "gomoku" -> {
                background=0xcba674;ink=cell.owner()==0?TextColor.color(0x211a16):cell.owner()==1?TextColor.color(0xfff9e9):TextColor.color(0x785a37);
                glyph=empty?"┼":cell.owner()==0?"●":"○";
            }
            case "xiangqi" -> {background=cell.y()==4||cell.y()==5?0xcbbd95:0xd8bb89;ink=cell.owner()==0?TextColor.color(0xae3128):TextColor.color(0x26333f);glyph=empty?"＋":cell.piece();}
            case "chess" -> {background=((cell.x()+cell.y())&1)==0?0xb49166:0xe0c79f;ink=cell.owner()==0?TextColor.color(0xfff6db):TextColor.color(0x2e2630);glyph=empty?"·":(cell.owner()==0?"白":"黑")+cell.piece();}
            case "checkers" -> {background=empty?0x2d4c43:0x283b35;glyph=empty?"○":"●";}
            case "aeroplane" -> {
                int route=routeColor(cell.id());background=tint(route>=0?COLORS[route%4].value():0x475b54,.3);ink=empty&&route>=0?COLORS[route%4]:ink;
                glyph=empty?cell.id().startsWith("go")?"终":cell.id().startsWith("ba")?"库":cell.id().startsWith("to")?"起":"·":cell.piece().replace("✈","机");
            }
            default -> {}
        }
        Color panel=selected?Color.fromARGB(250,173,125,43):Color.fromARGB(235,background>>16&255,background>>8&255,background&255);
        Component text=Component.text(" "+glyph+" ",ink).decorate(TextDecoration.BOLD).append(Component.newline())
            .append(Component.text(coordinate,empty?TextColor.color(0x718277):TextColor.color(0x839486)).decoration(TextDecoration.BOLD,false));
        return new Paint(text,panel,selected);
    }
    static int actualColor(Map<String,String> info,int owner) {
        if(owner<0)return-1;
        try {
            String[] colors=info.getOrDefault("colors","").replace("[","").replace("]","").split(",");
            if(owner<colors.length&&!colors[owner].isBlank())return Integer.parseInt(colors[owner].trim());
        }catch(NumberFormatException ignored){}
        return owner;
    }
    static int routeColor(String id) {
        try {
            if(id.startsWith("sk"))return Integer.parseInt(id.substring(2))%4;
            if(id.startsWith("ba")||id.startsWith("to")||id.startsWith("ld")||id.startsWith("go"))return Character.digit(id.charAt(2),10);
        }catch(RuntimeException ignored){}
        return-1;
    }
    private static int tint(int rgb,double fraction) {
        int r=(int)((rgb>>16&255)*fraction+21),g=(int)((rgb>>8&255)*fraction+24),b=(int)((rgb&255)*fraction+22);
        return r<<16|g<<8|b;
    }
    static String coordinate(String kind,Cell cell) {
        if(kind.equals("chess"))return cell.id().toUpperCase(Locale.ROOT);
        if(Set.of("gomoku","xiangqi","draughts","reversi","go","go9","go13").contains(kind))return String.valueOf((char)('A'+cell.x()))+(cell.y()+1);
        if(kind.equals("yacht"))return "骰子 "+(cell.x()/2+1);
        if(kind.equals("aeroplane")) {
            if(cell.id().startsWith("sk"))return String.valueOf(Integer.parseInt(cell.id().substring(2))+1);
            if(cell.id().startsWith("ld"))return"降落 "+(Character.digit(cell.id().charAt(4),10)+1);
            if(cell.id().startsWith("go"))return"终点";if(cell.id().startsWith("to"))return"起飞";return"机库";
        }
        return cell.id();
    }

    static List<String> sourceActions(BoardGame board,int seat,String source) {
        if(seat<0||source==null)return List.of();
        Cell cell=board.cells().stream().filter(c->c.id().equals(source)&&c.owner()==seat).findFirst().orElse(null);
        if(cell==null)return List.of();
        return board.actionsForCell(seat,source).stream().filter(action->{
            String[] parts=action.split(":");if(parts.length<3||!parts[0].equals("move"))return false;
            if(!board.id().equals("aeroplane"))return parts[1].equals(source);
            try {int plane=Integer.parseInt(parts[1]);return plane/4==seat&&cell.piece().contains(String.valueOf(plane%4+1));}
            catch(NumberFormatException ex){return false;}
        }).toList();
    }
    static List<String> destinationActions(Pick pick,String destination) {
        if(pick==null||destination==null)return List.of();
        return pick.actions().stream().filter(action->{String[] parts=action.split(":");return parts.length>=3&&parts[2].equals(destination);}).toList();
    }
    @EventHandler(priority=EventPriority.HIGH) public void click(PlayerInteractEntityEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND)return;
        if(event.getRightClicked().getPersistentDataContainer().has(tag)){event.setCancelled(true);worldClick(event.getPlayer());}
    }
    @EventHandler(priority=EventPriority.HIGH) public void use(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND)return;
        if(event.getAction()==Action.RIGHT_CLICK_AIR||event.getAction()==Action.RIGHT_CLICK_BLOCK||event.getAction()==Action.LEFT_CLICK_BLOCK)
            if(worldClick(event.getPlayer()))event.setCancelled(true);
    }
    @EventHandler public void swing(PlayerAnimationEvent event){if(event.getAnimationType()==PlayerAnimationType.ARM_SWING)worldClick(event.getPlayer());}
    @EventHandler(priority=EventPriority.HIGH) public void attack(EntityDamageByEntityEvent event){
        if(event.getEntity().getPersistentDataContainer().has(tag)){event.setCancelled(true);if(event.getDamager() instanceof Player player)worldClick(player);}
    }
    @EventHandler public void hanging(org.bukkit.event.hanging.HangingBreakEvent event){if(event.getEntity().getPersistentDataContainer().has(tag))event.setCancelled(true);}
    private boolean worldClick(Player player) {
        if(!plugin.allowed(player))return false;
        if(player.isSneaking()&&plugin.tableLobby!=null)return false;
        Room room=plugin.room(player);TableView view=room==null?null:views.get(room.id);if(view==null||room.board==null||!player.getWorld().equals(view.origin.getWorld()))return false;
        String cell=aimed(player,view);if(cell==null)return false;
        long now=System.nanoTime(),last=clicks.getOrDefault(player.getUniqueId(),0L);if(now-last<180_000_000L)return true;clicks.put(player.getUniqueId(),now);
        if(player.isSneaking()||cell.equals("@menu")){plugin.menus.room(player,room);return true;}
        if(room.phase!=Room.Phase.PLAYING)return true;
        if(room.undo!=null){plugin.menus.room(player,room);return true;}
        int seat=room.seat(player.getUniqueId());if(seat!=room.board.currentPlayer()&&!(room.board instanceof dev.server.boards.rules.GoGame go&&go.scoring())){player.sendActionBar(Component.text("还没轮到你，可以先观察棋盘。",NamedTextColor.GRAY));return true;}
        if(view.rolling()){player.sendActionBar(Component.text("骰子正在翻滚，请稍候。",NamedTextColor.GOLD));return true;}
        pickCell(player,room,seat,cell);return true;
    }
    private void pickCell(Player player,Room room,int seat,String cell) {
        if(cell.equals("@roll")) {
            if(room.board.legalActions(seat).contains("roll"))execute(player,room,List.of("roll"));else player.sendActionBar(Component.text("目前不能继续掷骰，请打开操作菜单。",NamedTextColor.GOLD));
            return;
        }
        List<String> related=room.board.actionsForCell(seat,cell);
        if(related.size()==1&&(related.getFirst().startsWith("drop:")||related.getFirst().startsWith("place:")||related.getFirst().startsWith("dead:")||related.getFirst().startsWith("hold:"))){execute(player,room,related);return;}
        Pick pick=selections.get(player.getUniqueId());
        if(pick!=null&&(!pick.room().equals(room.id)||pick.revision()!=room.revision)){selections.remove(player.getUniqueId());pick=null;}
        List<String> destinations=destinationActions(pick,cell);if(!destinations.isEmpty()){execute(player,room,destinations);return;}
        List<String> sources=sourceActions(room.board,seat,cell);
        if(!sources.isEmpty()) {
            if(pick!=null&&pick.source().equals(cell)){selections.remove(player.getUniqueId());player.sendActionBar(Component.text("已取消选择",NamedTextColor.GRAY));return;}
            selections.put(player.getUniqueId(),new Pick(room.id,room.revision,cell,List.copyOf(sources)));render(room);
            player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.25f,1.8f);
            player.sendActionBar(Component.text("已选中 · 蓝点为合法落点 · 再点棋子取消",NamedTextColor.GREEN));
        }else if(room.kind.equals("aeroplane")&&room.board.legalActions(seat).contains("roll"))plugin.tell(player,"请先右键棋盘旁的骰子，或从操作菜单掷骰。");
        else plugin.tell(player,pick==null?"请先选择自己的棋子。":"这个落点不可用，请另选落点或自己的棋子。");
    }
    private void execute(Player player,Room room,List<String> choices) {
        selections.remove(player.getUniqueId());
        if(choices.size()>1){render(room);plugin.menus.boardChoices(player,room,choices,0);return;}
        try{
            if(!plugin.allowed(player))return;
            room.requireAction(player.getUniqueId(),room.revision);
            // Keep the same identity/revision gate as menu actions, but do not
            // force a menu to cover the board after each successful world click.
            plugin.apply(room,room.seat(player.getUniqueId()),new com.google.gson.JsonPrimitive(choices.getFirst()),null);
        }
        catch(IllegalArgumentException ex){plugin.tell(player,"对局已更新，请重新选择棋子。");}
    }

    @EventHandler(ignoreCancelled=true) public void hurt(EntityDamageEvent e){if(world!=null&&e.getEntity().getWorld().equals(world))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void build(BlockPlaceEvent e){if(world!=null&&e.getBlock().getWorld().equals(world)&&!e.getPlayer().hasPermission("servergames.admin"))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(world!=null&&e.getBlock().getWorld().equals(world)&&!e.getPlayer().hasPermission("servergames.admin"))e.setCancelled(true);}
    @EventHandler public void voidFall(PlayerMoveEvent e){if(world!=null&&e.getPlayer().getWorld().equals(world)&&e.getPlayer().getY()<60)e.getPlayer().teleport(world.getSpawnLocation());}
    @EventHandler public void quit(PlayerQuitEvent e){clearSelection(e.getPlayer());}
    @EventHandler public void changeWorld(PlayerChangedWorldEvent e){clearSelection(e.getPlayer());}
    private void clearSelection(Player player) {
        clicks.remove(player.getUniqueId());views.values().forEach(view->view.clear(player));
        Pick old=selections.remove(player.getUniqueId());
        if(old!=null){Room room=plugin.rooms.get(old.room());if(room!=null)render(room);}
    }
    private void removeView(UUID room){TableView old=views.remove(room);if(old!=null)old.close();}
    void remove(Room room) {
        removeView(room.id);selections.values().removeIf(pick->pick.room().equals(room.id));
        Set<Chunk> chunks=tableChunks.remove(room.table);
        if(chunks!=null)for(Chunk chunk:chunks)if(!(chunk.getWorld().equals(world)&&lobbyChunks.contains(chunkKey(chunk.getX(),chunk.getZ())))&&tableChunks.values().stream().noneMatch(set->set.contains(chunk)))chunk.removePluginChunkTicket(plugin);

    }
    @Override public void close() {
        if(pointerTask!=null)pointerTask.cancel();
        views.values().forEach(TableView::close);views.clear();selections.clear();clicks.clear();tableChunks.clear();lobbyChunks.clear();
        for(World w:Bukkit.getWorlds())w.removePluginChunkTickets(plugin);
    }
}


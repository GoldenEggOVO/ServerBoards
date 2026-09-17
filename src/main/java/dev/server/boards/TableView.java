package dev.server.boards;

import dev.server.boards.rules.Cell;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Native tabletop rendering, reusable piece entities, bounded animation and private pointers. */
final class TableView implements AutoCloseable {
    final ServerBoards plugin;final Room room;final TableGeometry geometry;final Location origin;
    private final NamespacedKey tag;private final List<Entity> furniture=new ArrayList<>();
    private final Map<String,TokenView> tokens=new LinkedHashMap<>();
    private final Map<UUID,Overlay> overlays=new HashMap<>();
    private final List<Entity> lastMove=new ArrayList<>();
    private TextDisplay title,diceLabel;private BlockDisplay die;
    private record Pip(BlockDisplay display,Vector3f center,Quaternionf face){}
    private final List<Pip> pips=new ArrayList<>();
    private long revision=-1;private int diceFrames,dieValue=1;private Component lastTitle;
    private String lastDestination;
    private record Token(String id,Cell cell,double stack){}
    private final class TokenView {
        Token token;final List<Entity> parts=new ArrayList<>();Location from,to;int frame=6;double height,radius;
        TokenView(Token token,Location at){this.token=token;this.from=at;this.to=at;build();}
        void build(){
            Cell c=token.cell;float scale=(float)geometry.spacing;
            if(room.kind.equals("connectfour")){parts.add(block(from,c.owner()==0?Material.RED_CONCRETE:Material.YELLOW_CONCRETE,0,0,0,.22,.22,.10,null));height=.22;radius=.12;return;}
            for(TableModels.Part part:TableModels.piece(room.kind,c,room.board.publicInfo())){
                height=Math.max(height,(part.y()+part.h())*scale);radius=Math.max(radius,Math.max(Math.abs(part.x())+part.w()/2,Math.abs(part.z())+part.d()/2)*scale);
                BlockDisplay d=block(from,part.material(),part.x()*scale,part.y()*scale,part.z()*scale,part.w()*scale,part.h()*scale,part.d()*scale,null);
                if(room.kind.equals("aeroplane"))d.setRotation(GameWorld.actualColor(room.board.publicInfo(),c.owner())*90,0);parts.add(d);
            }
            if(room.kind.equals("xiangqi")||room.kind.equals("aeroplane")){
                String glyph=room.kind.equals("xiangqi")?c.piece():token.id.substring(token.id.indexOf(':')+1);
                TextDisplay label=text(from.clone().add(0,geometry.spacing*(room.kind.equals("xiangqi")?.235:.39),0),glyph,geometry.spacing*(room.kind.equals("xiangqi")?1.50:1.0),true,
                    c.owner()==0&&room.kind.equals("xiangqi")?NamedTextColor.DARK_RED:NamedTextColor.BLACK);
                if(room.kind.equals("xiangqi")&&c.owner()==0)label.setRotation(180,-90);
                parts.add(label);
            }
        }
        void move(Token next,Location at,boolean animate){
            from=position();to=at;token=next;frame=animate?0:6;
            if(!animate)positionParts(at);
        }
        Location position(){double u=Math.min(1,frame/6.0),ease=u*u*(3-2*u);return from.clone().add(to.toVector().subtract(from.toVector()).multiply(ease)).add(0,Math.sin(Math.PI*u)*geometry.spacing*.65,0);}
        void tick(){if(frame>=6)return;frame++;positionParts(position());}
        void positionParts(Location at){for(Entity part:parts){Location dest=at.clone();if(part instanceof TextDisplay)dest.add(0,geometry.spacing*(room.kind.equals("xiangqi")?.235:.39),0);dest.setYaw(part.getLocation().getYaw());dest.setPitch(part.getLocation().getPitch());part.teleport(dest);}}
        boolean valid(){return parts.stream().allMatch(Entity::isValid);}
        void remove(){parts.forEach(Entity::remove);}
    }
    private static final class Overlay {
        final String signature;final List<Entity> entities=new ArrayList<>();final List<BlockDisplay> hover=new ArrayList<>();
        String cell;Component feedback=Component.empty();int ticks;
        Overlay(String signature){this.signature=signature;}
        void remove(){entities.forEach(Entity::remove);hover.forEach(Entity::remove);}
    }
    TableView(ServerBoards plugin,Room room,Location center,NamespacedKey tag,TableMaps maps){
        this.plugin=plugin;this.room=room;this.tag=tag;geometry=new TableGeometry(room.kind,room.board.cells());origin=center.clone().add(0,TableGeometry.SURFACE,0);
        furniture.add(block(origin,Material.DARK_OAK_PLANKS,0,-.19,0,2.25,.14,2.25,null));
        for(double x:new double[]{-.99,.99})for(double z:new double[]{-.99,.99})furniture.add(block(origin,Material.STRIPPED_DARK_OAK_LOG,x,-TableGeometry.SURFACE,z,.15,TableGeometry.SURFACE-.13,.15,null));
        for(double v:new double[]{-1.06,1.06}){furniture.add(block(origin,Material.STRIPPED_DARK_OAK_WOOD,v,-.05,0,.10,.11,2.22,null));furniture.add(block(origin,Material.STRIPPED_DARK_OAK_WOOD,0,-.05,v,2.22,.11,.10,null));}
        if(room.kind.equals("connectfour")){
            for(int x=0;x<=7;x++)furniture.add(block(origin,Material.BLUE_CONCRETE,(x-3.5)*.28,.02,0,.035,1.72,.12,null));
            for(int y=0;y<=6;y++)furniture.add(block(origin,Material.BLUE_CONCRETE,0,.02+y*.28,0,2,.035,.12,null));
            title=text(origin.clone().add(0,2.05,0),"",.38,false,NamedTextColor.GOLD);title.setBillboard(Display.Billboard.CENTER);title.setLineWidth(500);furniture.add(title);sync();return;
        }
        List<org.bukkit.inventory.ItemStack> images=maps.get(origin.getWorld(),geometry);
        for(int z=0;z<2;z++)for(int x=0;x<2;x++){
            Location at=origin.clone().add(x-.5,0,z-.5);final int tile=z*2+x;
            ItemFrame f=origin.getWorld().spawn(at,ItemFrame.class,e->{tag(e,"@board");e.setFacingDirection(BlockFace.UP,true);e.setFixed(true);e.setVisible(false);e.setRotation(Rotation.NONE);e.setItem(images.get(tile),false);e.setItemDropChance(0);});
            // Hanging-entity creation snaps to a block face. Reposition once before any players receive the next frame.
            f.teleport(at);f.setFacingDirection(BlockFace.UP,true);furniture.add(f);
        }
        Interaction hit=origin.getWorld().spawn(origin.clone().add(0,.012,0),Interaction.class,e->{tag(e,"@board");e.setInteractionWidth(2.25f);e.setInteractionHeight(.025f);e.setResponsive(true);});furniture.add(hit);
        title=text(origin.clone().add(0,1.65,0),"",.48,false,NamedTextColor.GOLD);title.setBillboard(Display.Billboard.CENTER);title.setLineWidth(500);furniture.add(title);
        if(room.kind.equals("xiangqi"))furniture.add(text(origin.clone().add(0,.018,0),"楚河     汉界",.26,true,NamedTextColor.DARK_GRAY));
        if(room.kind.equals("yacht")){furniture.add(text(origin.clone().add(1.30,.025,0),"掷骰",.40,true,NamedTextColor.GOLD));}
        if(room.kind.equals("aeroplane")){
            die=block(origin.clone().add(1.30,.02,0),Material.QUARTZ_BLOCK,0,0,0,.30,.30,.30,null);furniture.add(die);
            int[][] coordinates={{0,0},{-1,-1},{1,1},{-1,1},{1,-1},{-1,0},{1,0}};
            for(int face=1;face<=6;face++){
                Quaternionf rotation=faceRotation(face);
                for(int dotIndex:pipIndices(face)){
                    Vector3f pos=new Vector3f(coordinates[dotIndex][0]*.09f,.155f,coordinates[dotIndex][1]*.09f).rotate(rotation);
                    BlockDisplay dot=block(origin.clone().add(1.30,.17,0),Material.BLACK_CONCRETE,0,0,0,.04,.006,.04,null);
                    pips.add(new Pip(dot,pos,rotation));furniture.add(dot);
                }
            }
            diceLabel=text(origin.clone().add(1.30,.025,.34),"点击掷骰",.28,true,NamedTextColor.GOLD);furniture.add(diceLabel);orientDie(new Quaternionf());
        }
        sync();
    }
    private void tag(Entity entity,String id){entity.setPersistent(false);entity.setGravity(false);entity.setInvulnerable(true);entity.getPersistentDataContainer().set(tag,PersistentDataType.STRING,room.id+"|"+id);}
    private void display(Display d,Player viewer){
        tag(d,"@model");d.setBrightness(new Display.Brightness(15,15));d.setViewRange(.35f);d.setTeleportDuration(2);d.setInterpolationDuration(2);
        if(viewer!=null)d.setVisibleByDefault(false);
    }
    private BlockDisplay block(Location at,Material mat,double x,double y,double z,double w,double h,double depth,Player viewer){
        BlockDisplay result=origin.getWorld().spawn(at,BlockDisplay.class,d->{display(d,viewer);d.setBlock(mat.createBlockData());d.setTransformation(new Transformation(new Vector3f((float)(x-w/2),(float)y,(float)(z-depth/2)),new Quaternionf(),new Vector3f((float)w,(float)h,(float)depth),new Quaternionf()));});
        if(viewer!=null)viewer.showEntity(plugin,result);return result;
    }
    private TextDisplay text(Location at,String value,double scale,boolean flat,NamedTextColor color){
        return origin.getWorld().spawn(at,TextDisplay.class,d->{display(d,null);d.setBillboard(Display.Billboard.FIXED);if(flat)d.setRotation(0,-90);d.text(Component.text(value,color));d.setLineWidth(200);d.setAlignment(TextDisplay.TextAlignment.CENTER);d.setDefaultBackground(false);d.setBackgroundColor(Color.fromARGB(0,0,0,0));d.setShadowed(false);d.setSeeThrough(false);d.setTransformation(new Transformation(new Vector3f(0,flat?(float)(-.125*scale):0,0),new Quaternionf(),new Vector3f((float)scale),new Quaternionf()));});
    }
    private List<Token> desired(){
        List<Token> list=new ArrayList<>();for(Cell c:room.board.cells())if(c.owner()>=0){
            if(room.kind.equals("aeroplane")){int stack=0;for(char digit:c.piece().toCharArray())if(digit>='1'&&digit<='4')list.add(new Token(c.owner()+":"+digit,c,stack++*.19));}
            else list.add(new Token(c.id(),c,0));
        }return list;
    }
    private Location at(Token t){if(room.kind.equals("connectfour"))return origin.clone().add((t.cell.x()-3)*.28,.05+t.cell.y()*.28,0);return origin.clone().add(geometry.x(t.cell),.03+t.stack*geometry.spacing,geometry.z(t.cell));}
    private boolean same(Token a,Token b){return a.cell.owner()==b.cell.owner()&&a.cell.piece().equals(b.cell.piece());}
    void sync(){
        boolean changed=revision>=0&&revision!=room.revision;
        if(revision==room.revision&&tokens.values().stream().allMatch(TokenView::valid)){updateTitle();return;}
        if(changed){overlays.values().forEach(Overlay::remove);overlays.clear();}
        Map<String,TokenView> old=new LinkedHashMap<>(tokens),next=new LinkedHashMap<>();List<Token> pending=new ArrayList<>();
        for(Token want:desired()){
            TokenView existing=old.get(want.id);
            if(existing!=null&&existing.valid()&&(room.kind.equals("aeroplane")||same(existing.token,want))){old.remove(want.id);boolean moved=!existing.token.cell.id().equals(want.cell.id())||existing.token.stack!=want.stack;if(moved)existing.move(want,at(want),changed);else existing.token=want;next.put(want.id,existing);}
            else pending.add(want);
        }
        String action=lastAction();String[] move=action.split(":");
        for(Token want:pending){TokenView source=null;String sourceId=null;
            if(changed&&move.length>=3&&move[0].equals("move")&&move[2].equals(want.cell.id())){
                source=old.get(move[1]);sourceId=move[1];
                if(source!=null&&(!source.valid()||!same(source.token,want))){source=null;sourceId=null;}
            }
            if(source==null&&changed&&!room.kind.equals("aeroplane"))for(var e:old.entrySet())if(e.getValue().valid()&&same(e.getValue().token,want)&&!e.getValue().token.cell.id().equals(want.cell.id())){source=e.getValue();sourceId=e.getKey();break;}
            if(source!=null){old.remove(sourceId);source.move(want,at(want),true);next.put(want.id,source);}
            else{Location pos=at(want);TokenView created=new TokenView(want,changed?pos.clone().add(0,geometry.spacing*.8,0):pos);if(changed)created.move(want,pos,true);next.put(want.id,created);}
        }
        old.values().forEach(TokenView::remove);tokens.clear();tokens.putAll(next);
        if(changed){lastMove.forEach(Entity::remove);lastMove.clear();
            lastDestination=move.length>=3?move[2]:move.length==2?move[1]:null;
            if(lastDestination!=null&&geometry.byId.containsKey(lastDestination))ring(lastMove,lastDestination,Material.GOLD_BLOCK,null,.90);
            origin.getWorld().playSound(origin,room.kind.equals("aeroplane")?Sound.BLOCK_WOODEN_BUTTON_CLICK_ON:Sound.BLOCK_WOOD_PLACE,.28f,1.4f);
            if(die!=null&&action.equals("roll")){dieValue=Integer.parseInt(room.board.publicInfo().getOrDefault("dice","1"));diceFrames=12;}
        }
        if(die!=null&&diceFrames==0){dieValue=Math.max(1,Integer.parseInt(room.board.publicInfo().getOrDefault("dice","1")));orientDie(faceRotation(dieValue).invert());}
        revision=room.revision;updateTitle();
    }
    private String lastAction(){if(room.history.isEmpty())return "";var e=room.history.get(room.history.size()-1).getAsJsonObject().get("action");return e!=null&&e.isJsonPrimitive()?e.getAsString():"";}
    private void updateTitle(){
        int turn=room.board.currentPlayer();String status=room.board.finished()?plugin.displayOutcome(room,room.board.outcome()):"轮到 "+(turn>=0&&turn<room.seats.size()?room.seats.get(turn).name():"玩家");
        if(room.phase==Room.Phase.LOBBY)status="等候准备";
        Component value=Component.text(ServerBoards.gameName(room.kind)+" · 第"+(room.table+1)+"桌",NamedTextColor.GOLD).append(Component.newline()).append(Component.text(status,NamedTextColor.WHITE)).append(Component.newline())
                .append(Component.text(TableLobby.roster(room.seats.stream().map(Room.Seat::name).toList(),room.capacity),NamedTextColor.WHITE)).append(Component.newline())
                .append(Component.text(room.kind.equals("yacht")?"点击骰子保留  |  旁边掷骰  |  菜单计分":"瞄准 · 点击落子",NamedTextColor.GRAY)).append(Component.newline())
                .append(Component.text("潜行右键：加入 / 房间菜单",NamedTextColor.GRAY));
        if(!value.equals(lastTitle)){title.text(value);lastTitle=value;}
        if(diceLabel!=null)diceLabel.text(Component.text(diceFrames>0?"掷骰中…":room.board.publicInfo().getOrDefault("pendingRoll","0").equals("0")?"点击掷骰":"请选择飞机",NamedTextColor.GOLD));
    }
    void tick(){updateTitle();tokens.values().forEach(TokenView::tick);if(diceFrames>0){
        diceFrames--;float spin=diceFrames*.85f;orientDie(new Quaternionf().rotateXYZ(spin,spin*.7f,spin*.5f));
        if(diceFrames==0){orientDie(faceRotation(dieValue).invert());updateTitle();}
    }}
    static Set<Integer> pipIndices(int value){return switch(value){case 1->Set.of(0);case 2->Set.of(1,2);case 3->Set.of(0,1,2);case 4->Set.of(1,2,3,4);case 5->Set.of(0,1,2,3,4);case 6->Set.of(1,2,3,4,5,6);default->throw new IllegalArgumentException("dice face");};}
    static Quaternionf faceRotation(int face){float half=(float)(Math.PI/2);return switch(face){case 1->new Quaternionf();case 2->new Quaternionf().rotateZ(-half);case 3->new Quaternionf().rotateX(half);case 4->new Quaternionf().rotateX(-half);case 5->new Quaternionf().rotateZ(half);case 6->new Quaternionf().rotateX(half*2);default->throw new IllegalArgumentException("dice face");};}
    private void orientDie(Quaternionf q){
        die.setInterpolationDelay(0);die.setTransformation(new Transformation(new Vector3f(-.15f,-.15f,-.15f).rotate(q).add(0,.15f,0),q,new Vector3f(.30f),new Quaternionf()));
        for(Pip pip:pips){Quaternionf rotation=new Quaternionf(q).mul(pip.face);Vector3f translation=new Vector3f(pip.center).rotate(q).sub(new Vector3f(.02f,.003f,.02f).rotate(rotation));pip.display.setInterpolationDelay(0);pip.display.setTransformation(new Transformation(translation,rotation,new Vector3f(.04f,.006f,.04f),new Quaternionf()));}
    }
    boolean rolling(){return diceFrames>0;}
    record Hit(String cell,double distance){}
    Hit hitPiece(Location eye,org.bukkit.util.Vector direction){
        Hit nearest=null;
        for(TokenView token:tokens.values()){
            Location p=token.position();var box=new org.bukkit.util.BoundingBox(p.getX()-token.radius,p.getY(),p.getZ()-token.radius,p.getX()+token.radius,p.getY()+token.height,p.getZ()+token.radius);
            var hit=box.rayTrace(eye.toVector(),direction,TableGeometry.REACH);
            if(hit!=null){double distance=hit.getHitPosition().distance(eye.toVector());if(nearest==null||distance<nearest.distance)nearest=new Hit(token.token.cell.id(),distance);}
        }
        if(die!=null){var hit=new org.bukkit.util.BoundingBox(origin.getX()+1.12,origin.getY()+.015,origin.getZ()-.18,origin.getX()+1.48,origin.getY()+.37,origin.getZ()+.18).rayTrace(eye.toVector(),direction,TableGeometry.REACH);
            if(hit!=null){double distance=hit.getHitPosition().distance(eye.toVector());if(nearest==null||distance<nearest.distance)nearest=new Hit("@roll",distance);}}
        return nearest;
    }
    String verticalHit(Location eye,org.bukkit.util.Vector direction){
        if(Math.abs(direction.getZ())<1e-6)return null;
        double distance=(origin.getZ()-eye.getZ())/direction.getZ();if(distance<0||distance>TableGeometry.REACH)return null;
        if(eye.getWorld().rayTraceBlocks(eye,direction,Math.max(.001,distance-.035),FluidCollisionMode.NEVER,true)!=null)return null;
        var point=eye.toVector().add(direction.clone().multiply(distance));double x=point.getX()-origin.getX(),y=point.getY()-origin.getY();
        int col=(int)Math.floor(x/.28+3.5),row=(int)Math.floor((y-.02)/.28);return col>=0&&col<7&&row>=0&&row<6?col+","+row:null;
    }
    void cursor(Player p,GameWorld.Pick pick,String hover){
        if(room.kind.equals("connectfour")){p.sendActionBar(Component.text(hover==null?"瞄准竖直棋盘选择列":"第 "+(hover.charAt(0)-'0'+1)+" 列 · 点击落子"));return;}
        boolean turn=room.phase==Room.Phase.PLAYING&&room.undo==null&&room.seat(p.getUniqueId())>=0&&(room.seat(p.getUniqueId())==room.board.currentPlayer()||room.board instanceof dev.server.boards.rules.GoGame go&&go.scoring());
        String signature=room.revision+"/"+turn+"/"+(pick==null?"":pick.source());Overlay old=overlays.get(p.getUniqueId());
        boolean reset=old==null||!old.signature.equals(signature);
        if(reset){if(old!=null)old.remove();old=new Overlay(signature);overlays.put(p.getUniqueId(),old);}
        if(reset&&turn&&pick!=null){ring(old.entities,pick.source(),Material.LIME_CONCRETE,p,1.0);Set<String> destinations=new HashSet<>();
            for(String action:pick.actions()){String[] parts=action.split(":");if(parts.length>=3)destinations.add(parts[2]);}
            for(String id:destinations){Cell cell=geometry.byId.get(id);if(cell!=null)old.entities.add(block(origin.clone().add(geometry.x(cell),.023,geometry.z(cell)),Material.LIGHT_BLUE_CONCRETE,0,0,0,geometry.spacing*.20,.018,geometry.spacing*.20,p));}
        }
        if(!reset&&Objects.equals(old.cell,hover)){if(++old.ticks%10==0)p.sendActionBar(old.feedback);return;}
        old.cell=hover;old.ticks=0;
        if(hover!=null&&geometry.byId.containsKey(hover)){
            Cell cell=geometry.byId.get(hover);boolean direct=turn&&room.board.actionsForCell(room.seat(p.getUniqueId()),hover).stream().anyMatch(a->a.startsWith("place:")||a.startsWith("dead:")||a.startsWith("hold:"));boolean legal=turn&&(direct||pick!=null&&!GameWorld.destinationActions(pick,hover).isEmpty()||!GameWorld.sourceActions(room.board,room.seat(p.getUniqueId()),hover).isEmpty());
            Material material=!turn?Material.GRAY_CONCRETE:legal?Material.YELLOW_CONCRETE:Material.RED_CONCRETE;
            if(old.hover.isEmpty()){List<Entity> list=new ArrayList<>();ring(list,hover,material,p,.82);list.forEach(e->oldHoverAdd(overlays.get(p.getUniqueId()),e));}
            else{Location at=origin.clone().add(geometry.x(cell),.025,geometry.z(cell));for(BlockDisplay d:old.hover){d.teleport(at);d.setBlock(material.createBlockData());}}
            String feedback=GameWorld.coordinate(room.kind,cell)+"  "+(cell.owner()<0?"空位":cell.piece())+" · "+(!turn?"等待你的回合":legal?direct?room.kind.equals("yacht")?"点击保留 / 重掷":cell.piece().contains("×")?"点击恢复活子":"点击操作":pick==null?"点击选棋":"点击落子":pick==null?"这里暂不能操作":"不可落在这里");
            old.feedback=Component.text(feedback,legal?NamedTextColor.YELLOW:NamedTextColor.GRAY);
        }else{
            old.hover.forEach(Entity::remove);old.hover.clear();
            old.feedback=Component.text("@roll".equals(hover)?rolling()?"骰子正在翻滚":turn?"点击掷骰子":"等待你的回合":"@menu".equals(hover)?"点击打开操作菜单 / 离开房间":"",NamedTextColor.GOLD);
        }
        p.sendActionBar(old.feedback);
    }
    private static void oldHoverAdd(Overlay overlay,Entity entity){overlay.hover.add((BlockDisplay)entity);}
    private void ring(List<Entity> list,String id,Material material,Player viewer,double fraction){Cell c=geometry.byId.get(id);if(c==null)return;double width=geometry.spacing*fraction,stroke=geometry.spacing*.065;Location at=origin.clone().add(geometry.x(c),.025,geometry.z(c));
        for(double side:new double[]{-width/2,width/2}){list.add(block(at,material,side,0,0,stroke,.012,width,viewer));list.add(block(at,material,0,0,side,width,.012,stroke,viewer));}}
    void clear(Player player){Overlay old=overlays.remove(player.getUniqueId());if(old!=null)old.remove();}
    @Override public void close(){tokens.values().forEach(TokenView::remove);tokens.clear();furniture.forEach(Entity::remove);overlays.values().forEach(Overlay::remove);overlays.clear();lastMove.forEach(Entity::remove);}
}

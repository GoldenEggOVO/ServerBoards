package dev.server.boards;

import com.google.gson.JsonPrimitive;
import dev.server.boards.rules.GameFactory;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TableViewTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void close(){MockBukkit.unmock();}
    @Test void tableTitleIsAboveTheCenterAndOldEdgeLabelIsAbsent() throws Exception {
        Fixture f=new Fixture("chess");
        TextDisplay title=(TextDisplay)field(f.view,"title");
        assertEquals(0,title.getLocation().getX(),0.0001);
        assertEquals(0,title.getLocation().getZ(),0.0001);
        assertEquals(((Location)field(f.view,"origin")).getY()+1.65,title.getLocation().getY(),0.0001);
        for(Entity e:f.entities)if(e instanceof TextDisplay text)
            verify(text,never()).text(net.kyori.adventure.text.Component.text("操作 / 离开",net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }
    @Test void waitingTitleShowsCurrentRosterAndUpdatesWithoutSpawningEntities()throws Exception{
        Fixture f=new Fixture("gomoku");f.room.phase=Room.Phase.LOBBY;f.room.seats.removeLast();
        TextDisplay title=(TextDisplay)field(f.view,"title");int count=f.entities.size();
        f.view.tick();var capture=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(title,atLeastOnce()).text(capture.capture());
        String initial=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(capture.getValue());
        assertTrue(initial.contains("等候准备"));assertTrue(initial.contains("桌内：本人"));assertTrue(initial.contains("空位：1 / 2"));
        f.room.join(UUID.randomUUID(),"好友");f.view.tick();verify(title,atLeastOnce()).text(capture.capture());
        String full=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(capture.getValue());
        assertTrue(full.contains("本人、好友"));assertTrue(full.contains("空位：0 / 2"));assertEquals(count,f.entities.size());
    }
    @Test void xiangqiRedLabelsFaceTheirOwnSideAndKeepRotationWhenMoving() throws Exception {
        Fixture f=new Fixture("xiangqi");
        Map<?,?> tokens=(Map<?,?>)field(f.view,"tokens");
        for(var entry:tokens.entrySet()) {
            var cell=f.room.board.cells().stream().filter(c->c.id().equals(entry.getKey())).findFirst().orElseThrow();
            for(Entity part:(List<Entity>)field(entry.getValue(),"parts"))if(part instanceof TextDisplay){
                assertEquals(cell.owner()==0?180:0,part.getLocation().getYaw());
                assertEquals(-90,part.getLocation().getPitch());
            }
        }
        String action=f.room.board.legalActions(0).getFirst();String destination=action.split(":")[2];
        f.move(action);for(int i=0;i<6;i++)f.view.tick();
        Object moved=((Map<?,?>)field(f.view,"tokens")).get(destination);
        for(Entity part:(List<Entity>)field(moved,"parts"))if(part instanceof TextDisplay)assertEquals(180,part.getLocation().getYaw());
    }
    @Test void hoveringAcrossCellsReusesFourPrivateCursorEntitiesAndNeverChangesTheBoard() {
        Fixture f=new Fixture("chess");int publicCount=f.entities.size();
        var pick=new GameWorld.Pick(f.room.id,f.room.revision,"e2",GameWorld.sourceActions(f.room.board,0,"e2"));
        f.view.cursor(f.player,pick,"e3");int initial=f.entities.size();assertEquals(10,initial-publicCount);
        for(int i=0;i<100;i++)f.view.cursor(f.player,pick,i%2==0?"e4":"e3");
        assertEquals(initial,f.entities.size());assertEquals(0,f.room.history.size());
        for(Entity e:f.entities.subList(publicCount,initial)){verify((Display)e).setVisibleByDefault(false);verify(f.player).showEntity(f.plugin,e);verify(e,never()).remove();}
        f.view.clear(f.player);for(Entity e:f.entities.subList(publicCount,initial))verify(e).remove();
        for(Entity e:f.entities.subList(0,publicCount))verify(e,never()).remove();
    }
    @Test void legalMoveReusesTheMovingPieceAndNeverTeleportsStationaryPieces() throws Exception {
        Fixture f=new Fixture("chess");Map<?,?> before=new HashMap<>((Map<?,?>)field(f.view,"tokens"));Object moving=before.get("e2");
        for(Entity e:f.entities)clearInvocations(e);
        f.move("move:e2:e4");Map<?,?> after=(Map<?,?>)field(f.view,"tokens");assertSame(moving,after.get("e4"));
        for(int i=0;i<6;i++)f.view.tick();
        for(var e:before.entrySet())if(!e.getKey().equals("e2")){
            assertSame(e.getValue(),after.get(e.getKey()));
            for(Entity part:(List<Entity>)field(e.getValue(),"parts"))verify(part,never()).teleport(any(Location.class));
        }
        f.view.sync();assertEquals(after.size(),((Map<?,?>)field(f.view,"tokens")).size());
    }
    @Test void modelRayFindsTallKingBeforeTheTablePlaneAndCloseRemovesAllEntities() throws Exception {
        Fixture f=new Fixture("chess");Map<?,?> tokens=(Map<?,?>)field(f.view,"tokens");Object king=tokens.get("e1");Location at=(Location)field(king,"to");
        double h=(double)field(king,"height");Location target=at.clone().add(0,h*.85,0),eye=target.clone().add(0,.35,-1.8);
        var hit=f.view.hitPiece(eye,target.toVector().subtract(eye.toVector()).normalize());assertNotNull(hit);assertEquals("e1",hit.cell());
        f.view.close();for(Entity e:f.entities)verify(e).remove();
    }
    @Test void connectFourHasVerticalPiecesAndRayRejectsMisses()throws Exception{
        Fixture f=new Fixture("connectfour");f.move("drop:3");f.move("drop:3");
        Map<?,?> tokens=(Map<?,?>)field(f.view,"tokens");Location low=(Location)field(tokens.get("3,0"),"to"),high=(Location)field(tokens.get("3,1"),"to");
        assertEquals(.28,high.getY()-low.getY(),1e-6);assertEquals(low.getZ(),high.getZ(),1e-6);
        Location eye=f.view.origin.clone().add(0,.15,-2);assertEquals("3,0",f.view.verticalHit(eye,new org.bukkit.util.Vector(0,0,1)));
        assertNull(f.view.verticalHit(eye,new org.bukkit.util.Vector(0,1,0)));assertNull(f.view.verticalHit(eye.clone().add(3,0,0),new org.bukkit.util.Vector(0,0,1)));
        assertNull(f.view.verticalHit(eye.clone().add(0,0,-5),new org.bukkit.util.Vector(0,0,1)));
    }
    static Object field(Object t,String n)throws Exception{var f=t.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(t);}
    static final class Fixture {
        final ServerBoards plugin=mock(ServerBoards.class);final Player player=mock(Player.class);final World world=mock(World.class);
        final List<Entity> entities=new ArrayList<>();final Map<Entity,Location> positions=new HashMap<>();final Room room;final TableView view;
        Fixture(String kind){
            when(world.spawn(any(Location.class),any(Class.class),any(Consumer.class))).thenAnswer(inv->{
                Entity e=mock((Class<? extends Entity>)inv.getArgument(1));entities.add(e);positions.put(e,((Location)inv.getArgument(0)).clone());
                when(e.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));when(e.isValid()).thenReturn(true);when(e.getLocation()).thenAnswer(a->positions.get(e).clone());
                doAnswer(a->{positions.put(e,((Location)a.getArgument(0)).clone());return true;}).when(e).teleport(any(Location.class));
                doAnswer(a->{positions.get(e).setYaw(a.getArgument(0));positions.get(e).setPitch(a.getArgument(1));return null;}).when(e).setRotation(anyFloat(),anyFloat());
                ((Consumer<Entity>)inv.getArgument(2)).accept(e);return e;
            });
            UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);room=new Room(UUID.randomUUID(),kind,2,0,0);room.join(id,"本人");room.fillBots();room.board=GameFactory.create(kind,2,0);room.phase=Room.Phase.PLAYING;
            TableMaps maps=mock(TableMaps.class);when(maps.get(eq(world),any())).thenReturn(Collections.nCopies(4,new ItemStack(Material.FILLED_MAP)));
            view=new TableView(plugin,room,new Location(world,0,80,0),new NamespacedKey("servergames","board-cell"),maps);
        }
        void move(String action){int seat=room.board.currentPlayer();room.board.apply(seat,action);room.event(seat,new JsonPrimitive(action));room.revision++;view.sync();}
    }
}

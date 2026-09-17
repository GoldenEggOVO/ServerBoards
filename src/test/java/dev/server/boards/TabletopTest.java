package dev.server.boards;

import com.google.gson.JsonPrimitive;
import dev.server.boards.rules.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TabletopTest {
    static final List<String> KINDS=List.of("chess","xiangqi","gomoku","checkers","aeroplane","draughts","reversi","yacht","go9","go13","go");
    @Test void everyCellCenterMapsBackToItsOwnCellAndFitsTheNativeMap() {
        for(String kind:KINDS){BoardGame board=GameFactory.create(kind,2,0);TableGeometry t=new TableGeometry(kind,board.cells());
            for(Cell c:board.cells()){
                assertEquals(c.id(),t.hit(t.x(c),t.z(c)),kind+" "+c.id());
                assertTrue(t.px(c)>10&&t.px(c)<246);assertTrue(t.pz(c)>10&&t.pz(c)<246);
            }
            assertNull(t.hit(-1.5,0));assertNull(t.hit(0,-1.5));assertNull(t.hit(Double.NaN,0));assertNull(t.hit(20,20));
            assertEquals("@menu",t.hit(0,1.27));
        }
    }
    @Test void pointerRejectsUpwardParallelBeyondReachAndBehindEye() {
        assertEquals(-1,TableGeometry.intersection(2,0,1));assertEquals(-1,TableGeometry.intersection(2,1,1));
        assertEquals(-1,TableGeometry.intersection(1,-1,2));assertEquals(-1,TableGeometry.intersection(20,-1,1));
        assertEquals(2,TableGeometry.intersection(2,-.5,1));assertEquals(-1,TableGeometry.intersection(Double.NaN,-1,1));
    }
    @Test void chineseStarCampArtworkMatchesAllSixtyOriginalStartingHoles() {
        BoardGame game=GameFactory.create("checkers",6,0);int colored=0;
        for(Cell c:game.cells()){assertEquals(c.owner(),TableArt.camp(c),c.id());if(TableArt.camp(c)>=0)colored++;}
        assertEquals(60,colored);
    }
    @Test void diceAlwaysFinishesWithTheAuthoritativeFaceUpAndOppositeFacesSumToSeven() {
        for(int face=1;face<=6;face++){
            assertEquals(face,TableView.pipIndices(face).size());
            Vector3f normal=new Vector3f(0,1,0).rotate(TableView.faceRotation(face));
            assertEquals(-1,normal.dot(new Vector3f(0,1,0).rotate(TableView.faceRotation(7-face))),1e-5);
            normal.rotate(TableView.faceRotation(face).invert());assertEquals(1,normal.y,1e-5);
        }
    }
    @Test void originalModelsHaveBoundedEntityCountsAndFitTheirCells() throws Exception {
        for(String kind:KINDS){BoardGame game=GameFactory.create(kind,kind.equals("checkers")?6:2,0);if(kind.equals("yacht"))game.apply(0,"roll");if(kind.startsWith("go"))game.apply(0,"place:0,0");
            for(Cell c:game.cells())if(c.owner()>=0){List<TableModels.Part> parts=TableModels.piece(kind,c,game.publicInfo());assertFalse(parts.isEmpty());assertTrue(parts.size()<=10,kind+" "+c.piece()+" "+parts.size());
                for(var p:parts){double limit=kind.equals("yacht")?1:.5;assertTrue(Math.abs(p.x())+p.w()/2<limit);assertTrue(Math.abs(p.z())+p.d()/2<limit);assertTrue(p.y()>=0&&p.h()>0&&p.w()>0&&p.d()>0);}}
        }
    }
    @Test void staticArtExportsFromExactlyTheGeometryUsedByTheServer() throws Exception {
        String export=System.getProperty("tabletop.export");
        for(String kind:KINDS){BoardGame game=GameFactory.create(kind,kind.equals("checkers")?6:kind.equals("aeroplane")?4:2,0);TableGeometry t=new TableGeometry(kind,game.cells());var image=TableArt.draw(t);
            assertEquals(256,image.getWidth());assertEquals(256,image.getHeight());assertNotEquals(image.getRGB(0,0),image.getRGB(128,128));
            if(export!=null){Path dir=Path.of(export);Files.createDirectories(dir);ImageIO.write(image,"png",dir.resolve(kind+"-board.png").toFile());
                var json=new com.google.gson.JsonObject();json.addProperty("kind",kind);var pieces=new com.google.gson.JsonArray();
                for(Cell c:game.cells())if(c.owner()>=0){var piece=new com.google.gson.JsonObject();piece.addProperty("x",t.x(c));piece.addProperty("z",t.z(c));piece.addProperty("scale",t.spacing);piece.addProperty("label",kind.equals("xiangqi")?c.piece():"");piece.addProperty("owner",c.owner());piece.add("parts",new com.google.gson.Gson().toJsonTree(TableModels.piece(kind,c,game.publicInfo())));pieces.add(piece);}
                json.add("pieces",pieces);Files.writeString(dir.resolve(kind+"-models.json"),json.toString());
            }
        }
    }
    @Test void pointerClickKeepsIdentityTurnAndDuplicatePacketGates() throws Exception {
        Fixture f=new Fixture();assertTrue(f.click());
        verify(f.plugin).apply(eq(f.room),eq(0),eq(new JsonPrimitive("place:7,7")),isNull());
        assertTrue(f.click());verify(f.plugin,times(1)).apply(any(),anyInt(),any(),any());
        f.clicks.clear();when(f.plugin.allowed(f.player)).thenReturn(false);assertFalse(f.click());
        when(f.plugin.allowed(f.player)).thenReturn(true);when(f.plugin.room(f.player)).thenReturn(null);assertFalse(f.click());
        when(f.plugin.room(f.player)).thenReturn(f.room);f.room.board.apply(0,"place:7,7");f.clicks.clear();assertTrue(f.click());
        verify(f.plugin,times(1)).apply(any(),anyInt(),any(),any());
    }
    @Test void pointerDoesNotClickThroughWallsOrOutsideItsTable() throws Exception {
        Fixture f=new Fixture();when(f.world.rayTraceBlocks(any(),any(),anyDouble(),eq(FluidCollisionMode.NEVER),eq(true))).thenReturn(new RayTraceResult(new Vector(0,1,1)));
        assertFalse(f.click());verify(f.plugin,never()).apply(any(),anyInt(),any(),any());
        when(f.world.rayTraceBlocks(any(),any(),anyDouble(),eq(FluidCollisionMode.NEVER),eq(true))).thenReturn(null);
        when(f.player.getEyeLocation()).thenReturn(new Location(f.world,0,1.62,2.25).setDirection(new Vector(0,1,0)));assertFalse(f.click());
    }
    static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static final class Fixture {
        final ServerBoards plugin=mock(ServerBoards.class);final GameWorld arena=mock(GameWorld.class,CALLS_REAL_METHODS);
        final World world=mock(World.class);final Player player=mock(Player.class);final Room room=new Room(UUID.randomUUID(),"gomoku",2,0,0);
        final Map<UUID,Long> clicks=new HashMap<>();
        Fixture()throws Exception{
            UUID id=UUID.randomUUID();room.join(id,"测试玩家");room.fillBots();room.board=GameFactory.create("gomoku",2,0);room.phase=Room.Phase.PLAYING;
            TableView view=mock(TableView.class);set(view,"origin",new Location(world,0,.85,0));set(view,"geometry",new TableGeometry("gomoku",room.board.cells()));
            set(arena,"plugin",plugin);set(arena,"views",new HashMap<>(Map.of(room.id,view)));set(arena,"clicks",clicks);set(arena,"selections",new HashMap<>());arena.world=world;
            when(plugin.allowed(player)).thenReturn(true);when(plugin.room(player)).thenReturn(room);when(player.getUniqueId()).thenReturn(id);when(player.getWorld()).thenReturn(world);
            when(player.getEyeLocation()).thenAnswer(a->new Location(world,0,1.62,2.25).setDirection(new Vector(0,-.74,-2.25)));
        }
        boolean click()throws Exception{Method method=GameWorld.class.getDeclaredMethod("worldClick",Player.class);method.setAccessible(true);return (boolean)method.invoke(arena,player);}
    }
}

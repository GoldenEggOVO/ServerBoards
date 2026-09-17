package dev.server.boards;

import dev.server.boards.rules.*;
import dev.server.boards.rules.upstream.aeroplane.Aeroplane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.NamespacedKey;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameWorldTest {
    @TempDir Path directory;
    @Test void paper262DimensionPathUsesMainLevelAndKeyNotWorldContainer() {
        Path level=directory.resolve("smoke_lobby");
        assertEquals(level.resolve("dimensions/minecraft/tablegames"),GameWorld.dimensionFolder(level,NamespacedKey.minecraft("tablegames")));
        assertEquals(level.resolve("dimensions/servergames/arena"),GameWorld.dimensionFolder(level,new NamespacedKey("servergames","arena")));
    }
    @Test void unmarkedModernDimensionAndLegacyMigrationInputAreRejectedWithoutWriting() throws Exception {
        Path legacy=directory.resolve("tablegames"),modern=directory.resolve("smoke_lobby/dimensions/minecraft/tablegames");
        assertNull(GameWorld.preflightOwner(legacy,modern));
        Files.createDirectories(modern);Files.writeString(modern.resolve("existing-build.txt"),"preserve");
        assertThrows(IllegalStateException.class,()->GameWorld.preflightOwner(legacy,modern));
        assertEquals("preserve",Files.readString(modern.resolve("existing-build.txt")));
        UUID id=UUID.randomUUID();Files.writeString(modern.resolve(".servergames-owner"),"ServerBoards arena v1\n"+id);
        assertEquals(id,GameWorld.preflightOwner(legacy,modern));
        Files.createDirectories(legacy);Files.writeString(legacy.resolve("existing-build.txt"),"preserve legacy");
        assertThrows(IllegalStateException.class,()->GameWorld.preflightOwner(legacy,modern));
        assertEquals("preserve legacy",Files.readString(legacy.resolve("existing-build.txt")));
        assertFalse(Files.exists(legacy.resolve(".servergames-owner")));
    }
    @Test void invalidMarkerCannotMakeExistingDimensionTrusted() throws Exception {
        Path modern=Files.createDirectories(directory.resolve("dimensions/minecraft/tablegames"));
        Files.writeString(modern.resolve(".servergames-owner"),"ServerBoards arena v1\nnot-a-uuid");
        assertThrows(IllegalStateException.class,()->GameWorld.readOwner(modern));
    }
    @Test void chessSourceThenDestinationRetainsAllPromotionChoices() {
        List<String> promotions=List.of("move:a7:a8:q","move:a7:a8:r","move:a7:a8:b","move:a7:a8:n");
        GameWorld.Pick pick=new GameWorld.Pick(UUID.randomUUID(),7,"a7",promotions);
        assertEquals(promotions,GameWorld.destinationActions(pick,"a8"));
        assertTrue(GameWorld.destinationActions(pick,"b8").isEmpty());
        BoardGame chess=GameFactory.create("chess",2,0);
        assertEquals(List.of("move:e2:e3","move:e2:e4").stream().sorted().toList(),GameWorld.sourceActions(chess,0,"e2").stream().sorted().toList());
        assertTrue(GameWorld.sourceActions(chess,0,"e7").isEmpty());
    }
    @Test void flightStackSelectionMatchesPlaneIdentityNotCellId() throws Exception {
        long seed=0;while(new SplittableRandom(seed).nextInt(1,7)!=1)seed++;
        BoardGame game=GameFactory.create("aeroplane",2,seed);
        Field field=AeroplaneGame.class.getDeclaredField("planes");field.setAccessible(true);
        Aeroplane[] planes=(Aeroplane[])field.get(game);
        planes[0].setInCellId("sk5");planes[1].setInCellId("sk5");planes[2].setInCellId("sk4");
        game.apply(0,"roll");
        // The destination-aware rule API includes a third plane flying INTO this
        // stack. Selecting the stack must only select its two existing aircraft.
        assertEquals(3,game.actionsForCell(0,"sk5").size());
        List<String> actions=GameWorld.sourceActions(game,0,"sk5");
        assertEquals(List.of("move:0:sk6","move:1:sk6"),actions);
        GameWorld.Pick pick=new GameWorld.Pick(UUID.randomUUID(),1,"sk5",actions);
        assertEquals(2,GameWorld.destinationActions(pick,"sk6").size());
        assertTrue(GameWorld.sourceActions(game,1,"sk5").isEmpty());
    }
    @Test void flightTakeoffWorksThroughWorldCellSelection() {
        long seed=0;while(new SplittableRandom(seed).nextInt(1,7)!=6)seed++;
        BoardGame game=GameFactory.create("aeroplane",2,seed);game.apply(0,"roll");
        List<String> actions=GameWorld.sourceActions(game,0,"ba0_0");
        assertEquals(List.of("move:0:to0"),actions);
        assertEquals(actions,GameWorld.destinationActions(new GameWorld.Pick(UUID.randomUUID(),1,"ba0_0",actions),"to0"));
    }
    @Test void checkersLayoutPreservesEqualLengthHexNeighbors() {
        BoardGame game=GameFactory.create("checkers",6,0);
        GameWorld.Layout layout=GameWorld.layout("checkers",game.cells());
        Cell origin=cell(game,"6,8"),east=cell(game,"7,8"),southEast=cell(game,"6,9");
        double first=Math.hypot(layout.x(east)-layout.x(origin),layout.z(east)-layout.z(origin));
        double second=Math.hypot(layout.x(southEast)-layout.x(origin),layout.z(southEast)-layout.z(origin));
        assertEquals(first,second,1e-6);
        for(Cell c:game.cells()){assertTrue(Math.abs(layout.x(c))<7);assertTrue(Math.abs(layout.z(c))<7);}
    }
    @Test void allBoardsFitPlatformAndAirplaneRouteColorsDoNotDependOnSeatCount() {
        for(String kind:List.of("gomoku","xiangqi","chess","aeroplane","checkers")) {
            BoardGame game=GameFactory.create(kind,2,0);GameWorld.Layout layout=GameWorld.layout(kind,game.cells());
            for(Cell c:game.cells()){assertTrue(Math.abs(layout.x(c))<8);assertTrue(Math.abs(layout.z(c))<8);}
        }
        assertEquals(2,GameWorld.actualColor(Map.of("colors","[0, 2]"),1));
        assertEquals(1,GameWorld.routeColor("sk13"));assertEquals(2,GameWorld.routeColor("to2"));
        assertEquals(3,GameWorld.routeColor("ld3_4"));assertEquals(0,GameWorld.routeColor("ba0_3"));
    }
    private static Cell cell(BoardGame game,String id){return game.cells().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();}
}

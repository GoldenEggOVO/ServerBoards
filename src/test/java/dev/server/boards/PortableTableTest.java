package dev.server.boards;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PortableTableTest {
 @TempDir Path dir;
 @BeforeEach void setup(){MockBukkit.mock();}
 @AfterEach void cleanup(){MockBukkit.unmock();}
 @Test void arbitraryWorldAndGridCenterArePreservedAndSeatChecksDoNotEditTerrain() throws Exception {
  var server=MockBukkit.getMock();var w=server.addSimpleWorld("survival");var other=server.addSimpleWorld("creative");
  var plugin=mock(ServerBoards.class);when(plugin.getName()).thenReturn("ServerBoards");when(plugin.namespace()).thenReturn("servergames");when(plugin.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());when(plugin.getServer()).thenReturn(server);when(plugin.isEnabled()).thenReturn(true);when(plugin.getDataFolder()).thenReturn(dir.toFile());
  var field=ServerBoards.class.getDeclaredField("rooms");field.setAccessible(true);field.set(plugin,new LinkedHashMap<UUID,Room>());
  var arena=new GameWorld(plugin);int worlds=server.getWorlds().size();arena.initialize();assertNull(arena.world);assertEquals(worlds,server.getWorlds().size());
  var r=new Room(UUID.randomUUID(),"gomoku",2,1,0);var c=new Location(w,-12.25,83.5,17.75);arena.anchor(r,c);plugin.rooms.put(r.id,r);
  assertEquals(new Location(w,-12,83,18),arena.center(0));
  var p=server.addPlayer();p.teleport(c);assertTrue(arena.atTableWorld(p,r));p.teleport(other.getSpawnLocation());assertFalse(arena.atTableWorld(p,r));
  // MockBukkit does not implement Block.isPassable; use an explicit collision fixture.
  var collisionWorld=spy(w);var block=mock(org.mockbukkit.mockbukkit.block.BlockMock.class);when(block.isPassable()).thenReturn(true);
  doReturn(block).when(collisionWorld).getBlockAt(org.mockito.ArgumentMatchers.any(Location.class));
  try(var bukkit=mockStatic(Bukkit.class,CALLS_REAL_METHODS)){
   bukkit.when(()->Bukkit.getWorld(r.anchorWorld)).thenReturn(collisionWorld);
   var seat=arena.seatLocation(r,0);assertEquals(w.getUID(),seat.getWorld().getUID());assertEquals(83,seat.getY());
   when(block.isPassable()).thenReturn(false);assertThrows(IllegalArgumentException.class,()->arena.seatLocation(r,0));
   verify(block,never()).setType(org.mockito.ArgumentMatchers.any(Material.class));
  }
  assertEquals(Material.AIR,w.getBlockAt(c).getType());
  // No runtime initialization or chunk tickets in this fixture; MockBukkit owns teardown.
 }
}



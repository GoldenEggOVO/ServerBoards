package dev.server.boards;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableLobbyTest {
    public static class ProviderTables {
        final List<PublicTable> values=new ArrayList<>();
        int reads;
        public Iterable<PublicTable> tables(){reads++;return values;}
    }
    public record PublicTable(String name,Location center) {
        public String getName(){return name;}
        public Location getCenter(){return center;}
        public PublicTable table(){return this;}
        public Object[] seats(){return new Object[0];}
        public String phase(){return "LOBBY";}
        public String getStatus(){return "WAITING";}
        public PublicTable getRoster(){return this;}
        public Iterable<Object> participants(){return List.of();}
    }
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    TableLobby.Entry entry(Location center,List<String> names,int capacity){
        return new TableLobby.Entry("test",center,1.5,1.03125,"麻将","等候",names,capacity,Set.of(),null,false,p->{},p->{});
    }
    @Test void rosterIncludesNamesBotsAndClampsEmptySeats(){
        assertEquals("桌内：Alice、陪练2\n空位：2 / 4",TableLobby.roster(List.of("Alice","陪练2"),4));
        assertEquals("桌内：暂无玩家\n空位：3 / 3",TableLobby.roster(List.of(),3));
        assertEquals(0,entry(null,List.of("A","B","C"),2).empty());
    }
    @Test void aimingRejectsMissesWrongWorldAndOutOfReach(){
        World w=mock(World.class),other=mock(World.class);
        var e=entry(new Location(w,0,80,0),List.of(),4);
        assertEquals(1.96875,TableLobby.hit(e,new Location(w,0,83,0),new Vector(0,-1,0)),1e-6);
        assertEquals(Double.POSITIVE_INFINITY,TableLobby.hit(e,new Location(w,2,83,0),new Vector(0,-1,0)));
        assertEquals(Double.POSITIVE_INFINITY,TableLobby.hit(e,new Location(other,0,83,0),new Vector(0,-1,0)));
        assertEquals(Double.POSITIVE_INFINITY,TableLobby.hit(e,new Location(w,0,90,0),new Vector(0,-1,0)));
        assertEquals(Double.POSITIVE_INFINITY,TableLobby.hit(e,new Location(w,0,83,0),new Vector(0,1,0)));
    }
    @Test void originalReadyPlayerDoesNotNeedToClickAgainToStart(){
        var plugin=mock(ServerBoards.class);plugin.menus=mock(GameMenus.class);
        var r=new Room(UUID.randomUUID(),"gomoku",2,1,0);Player a=mock(Player.class),b=mock(Player.class);
        when(a.getUniqueId()).thenReturn(UUID.randomUUID());when(b.getUniqueId()).thenReturn(UUID.randomUUID());
        doCallRealMethod().when(plugin).ready(any(),any());
        r.join(a.getUniqueId(),"A");plugin.ready(a,r);r.join(b.getUniqueId(),"B");plugin.ready(b,r);
        assertEquals(Set.of(a.getUniqueId(),b.getUniqueId()),r.ready);verify(plugin).start(r);
    }
    @Test void leavingLobbyOnlyClearsTheDepartingPlayersReadyState()throws Exception{
        var plugin=mock(ServerBoards.class);plugin.menus=mock(GameMenus.class);
        var field=ServerBoards.class.getDeclaredField("returns");field.setAccessible(true);field.set(plugin,new HashMap<UUID,Location>());
        var r=new Room(UUID.randomUUID(),"checkers",3,1,0);Player a=mock(Player.class);UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        when(a.getUniqueId()).thenReturn(first);r.join(first,"A");r.join(second,"B");r.ready.addAll(List.of(first,second));
        plugin.coordinator=mock(dev.server.games.api.GameCoordinator.class);when(plugin.room(a)).thenReturn(r);doCallRealMethod().when(plugin).leave(a);plugin.leave(a);
        assertEquals(Set.of(second),r.ready);assertEquals(1,r.seats.size());verify(plugin,never()).remove(r);
    }
    ServerBoards plugin(){
        var p=mock(ServerBoards.class);p.coordinator=mock(dev.server.games.api.GameCoordinator.class);
        when(p.getName()).thenReturn("ServerBoards");when(p.isEnabled()).thenReturn(true);when(p.getServer()).thenReturn(MockBukkit.getMock());
        when(p.getPluginLoader()).thenReturn(MockBukkit.createMockPlugin().getPluginLoader());when(p.allowed(any())).thenReturn(true);return p;
    }
    Player player(World world){
        var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);when(p.isSneaking()).thenReturn(true);
        when(p.getWorld()).thenReturn(world);when(p.getEyeLocation()).thenReturn(new Location(world,0,83,0,0,90));return p;
    }
    void entries(TableLobby lobby,List<TableLobby.Entry> entries)throws Exception{
        var field=TableLobby.class.getDeclaredField("entries");field.setAccessible(true);field.set(lobby,entries);
        doReturn(entries).when(lobby).collect();
    }
    @Test void joinsOnceOnNextTickAndChecksFreshCapacity()throws Exception{
        var plugin=plugin();World world=mock(World.class);Player p=player(world);var lobby=spy(new TableLobby(plugin));
        var joined=new java.util.concurrent.atomic.AtomicInteger();
        var target=new TableLobby.Entry("test",new Location(world,0,80,0),1.5,1.03,"麻将","等候",List.of("A"),2,Set.of(),null,true,x->joined.incrementAndGet(),x->{});
        entries(lobby,List.of(target));
        assertTrue(lobby.request(p));assertTrue(lobby.request(p));assertEquals(0,joined.get());
        MockBukkit.getMock().getScheduler().performOneTick();assertEquals(1,joined.get());
        // Another player takes the last seat before the deferred callback executes.
        Player second=player(world);assertTrue(lobby.request(second));
        var full=new TableLobby.Entry("test",target.center(),1.5,1.03,"麻将","等候",List.of("A","B"),2,Set.of(),null,true,target.join(),target.menu());
        doReturn(List.of(full)).when(lobby).collect();MockBukkit.getMock().getScheduler().performOneTick();assertEquals(1,joined.get());
        verify(plugin).tell(second,"这张桌子已经满员。");
    }
    @Test void seatedPlayerOpensMenuAndPlayingTableRejectsOutsider()throws Exception{
        var plugin=plugin();World world=mock(World.class);Player p=player(world);var lobby=spy(new TableLobby(plugin));
        var opened=new java.util.concurrent.atomic.AtomicInteger();var joined=new java.util.concurrent.atomic.AtomicInteger();
        var e=new TableLobby.Entry("test",new Location(world,0,80,0),1.5,1.03,"麻将","进行中",List.of("A"),4,Set.of(p.getUniqueId()),null,true,x->joined.incrementAndGet(),x->opened.incrementAndGet());
        entries(lobby,List.of(e));assertTrue(lobby.request(p));MockBukkit.getMock().getScheduler().performOneTick();assertEquals(1,opened.get());
        Player stranger=player(world);assertTrue(lobby.request(stranger));MockBukkit.getMock().getScheduler().performOneTick();
        assertEquals(0,joined.get());verify(plugin).tell(stranger,"这张桌子已开局，暂时不能加入。");
    }
    @Test void ordinaryClickAndSolidObstructionDoNotJoin()throws Exception{
        var plugin=plugin();World world=mock(World.class);Player p=player(world);var lobby=spy(new TableLobby(plugin));
        entries(lobby,List.of(entry(new Location(world,0,80,0),List.of(),4)));
        when(p.isSneaking()).thenReturn(false);assertFalse(lobby.request(p));when(p.isSneaking()).thenReturn(true);
        when(world.rayTraceBlocks(any(),any(),anyDouble(),any(),anyBoolean())).thenReturn(new org.bukkit.util.RayTraceResult(new Vector(0,82,0)));
        assertFalse(lobby.request(p));
    }
    @Test void ownedLabelsAreReusedAndDeletedWithTableButNativeLabelsAreNotRemoved()throws Exception{
        var plugin=plugin();World world=mock(World.class);TextDisplay label=mock(TextDisplay.class);when(label.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class),eq(TextDisplay.class),any(java.util.function.Consumer.class))).thenReturn(label);
        var lobby=spy(new TableLobby(plugin));var e=entry(new Location(world,0,80,0),List.of("A"),4);
        entries(lobby,List.of(e));lobby.refresh();lobby.refresh();verify(world,times(1)).spawn(any(Location.class),eq(TextDisplay.class),any(java.util.function.Consumer.class));
        entries(lobby,List.of());lobby.refresh();verify(label).remove();
        TextDisplay nativeLabel=mock(TextDisplay.class);
        var nativeEntry=new TableLobby.Entry("native",e.center(),1.5,1.03,"麻将","等候",List.of("A"),4,Set.of(),nativeLabel,false,e.join(),e.menu());
        entries(lobby,List.of(nativeEntry));lobby.refresh();lobby.close();verify(nativeLabel,never()).remove();
    }
}

package dev.server.boards;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NativeMenuTest {
    @Test void standaloneCatalogDoesNotOfferMissingServerMenu() throws Exception {
        var plugin=mock(ServerBoards.class);when(plugin.allowed(any())).thenReturn(true);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(player.getWorld()).thenReturn(world);
        var menus=new GameMenus(plugin);menus.show(player,"Games","",List.of(),null);
        var field=GameMenus.class.getDeclaredField("sessions");field.setAccessible(true);
        @SuppressWarnings("unchecked") var sessions=(Map<UUID,GameMenus.Session>)field.get(menus);
        assertFalse(sessions.get(player.getUniqueId()).buttons().stream().anyMatch(b->b.id().equals("main")));
    }
    @SuppressWarnings("unchecked") private String action(GameMenus menus,Player p)throws Exception{var field=GameMenus.class.getDeclaredField("sessions");field.setAccessible(true);var sessions=(Map<UUID,GameMenus.Session>)field.get(menus);return "boards:"+sessions.get(p.getUniqueId()).token()+" 0";}
    @Test void callbackIsSingleUseAndBoundToPlayerAndWorld()throws Exception{
        var plugin=mock(ServerBoards.class);when(plugin.allowed(any())).thenReturn(true);var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);
        var menus=new GameMenus(plugin);var invoked=new AtomicInteger();menus.show(p,"Menu","",List.of(new GameMenus.Button("Play",invoked::incrementAndGet)),null);String action=action(menus,p);
        var other=mock(Player.class);when(other.getUniqueId()).thenReturn(UUID.randomUUID());menus.handle(other,action);assertEquals(0,invoked.get());
        menus.handle(p,"boards:"+UUID.randomUUID()+" 0");assertEquals(0,invoked.get());
        var elsewhere=mock(World.class);when(elsewhere.getUID()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(elsewhere);menus.handle(p,action);assertEquals(0,invoked.get());
        when(p.getWorld()).thenReturn(world);menus.handle(p,action);menus.handle(p,action);assertEquals(1,invoked.get());
    }
    @Test void authorizationIsRecheckedBeforeCallback()throws Exception{
        var plugin=mock(ServerBoards.class);when(plugin.allowed(any())).thenReturn(true);var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);var menus=new GameMenus(plugin);var invoked=new AtomicInteger();menus.show(p,"Menu","",List.of(new GameMenus.Button("Play",invoked::incrementAndGet)),null);String action=action(menus,p);when(plugin.allowed(p)).thenReturn(false);menus.handle(p,action);assertEquals(0,invoked.get());
    }
}

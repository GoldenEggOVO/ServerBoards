package dev.server.boards;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowMenuTest {
    @Test void layoutPreservesStyleButReplacesActionsWithOwnedCallbacks() {
        var config = new YamlConfiguration();
        config.set("Title", "@title@");
        config.set("Bottom.buttons.connectfour.width", 222);
        config.set("Bottom.buttons.connectfour.actions", List.of("command: op bad"));
        config.set("Bottom.buttons.mahjong.text", "麻将");
        config.set("Events.Open", List.of("command: old"));
        UUID token = UUID.randomUUID();
        var rendered = GameMenuLayouts.render(config, "棋盘游戏", "", List.of(
            new GameMenus.Button("connectfour", "四子棋", () -> {})), token);
        assertEquals(222, rendered.config().getInt("Bottom.buttons.slot0.width"));
        assertEquals(List.of("boards:" + token + " 0"), rendered.config().getStringList("Bottom.buttons.slot0.actions"));
        assertFalse(rendered.config().contains("Bottom.buttons.mahjong"));
        assertFalse(rendered.config().contains("Events"));
    }

    @Test void windowUsesRenderedOrderAndDoesNotAlsoSendChatButtons() {
        var plugin = mock(ServerBoards.class);
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var view = mock(BoardWindow.class);
        var clicked = new AtomicInteger();
        when(view.render(anyString(), anyString(), anyString(), anyList(), any())).thenAnswer(invocation -> {
            var config = new YamlConfiguration();
            config.set("Bottom.buttons.second.text", "@label@");
            return GameMenuLayouts.render(config, "Test", "", invocation.getArgument(3), invocation.getArgument(4));
        });
        when(view.open(eq(player), any(), anyString())).thenReturn(true);
        var menus = new GameMenus(plugin, view);
        menus.show(player, "Test", "", List.of(
            new GameMenus.Button("first", "First", () -> clicked.set(1)),
            new GameMenus.Button("second", "Second", () -> clicked.set(2))), null);
        var rendered = org.mockito.ArgumentCaptor.forClass(YamlConfiguration.class);
        verify(view).open(eq(player), rendered.capture(), eq("dialog"));
        String action = rendered.getValue().getStringList("Bottom.buttons.slot0.actions").getFirst();
        menus.handle(player, action);
        assertEquals(2, clicked.get());
        clicked.set(0);
        menus.handle(player, action);
        assertEquals(0, clicked.get());
        verify(player, never()).sendMessage(anyString());
        menus.close();
    }
}

package dev.server.boards;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfflineRestoreTest {
    @TempDir Path directory;

    @Test void persistedOfflineSeatsUseOwnerAuthenticatedRestoreInsteadOfAdmission() throws Exception {
        ServerBoards plugin = mock(ServerBoards.class);
        plugin.coordinator = mock(BoardOccupancy.class);
        plugin.arena = mock(GameWorld.class);
        set(plugin, "rooms", new LinkedHashMap<UUID, Room>());
        set(plugin, "returns", new HashMap<>());
        set(plugin, "gson", new Gson());
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doCallRealMethod().when(plugin).restore();
        UUID player = UUID.randomUUID();
        String source = "{\"returns\":{},\"rooms\":[{\"id\":\"" + UUID.randomUUID() + "\",\"kind\":\"chess\",\"capacity\":2,\"seed\":1,\"table\":0,\"phase\":\"LOBBY\",\"revision\":1,\"history\":[],\"seats\":[{\"id\":\"" + player + "\",\"name\":\"offline\",\"bot\":false}]}]}";
        Files.writeString(directory.resolve("rooms.json"), source);
        when(plugin.coordinator.restoreReservation(player, "chess")).thenReturn(true);
        assertDoesNotThrow(plugin::restore);
        verify(plugin.coordinator).restoreReservation(player, "chess");
        verify(plugin.coordinator, never()).reserve(any(), any());
        assertEquals(1, plugin.rooms.size());
        assertTrue(plugin.rooms.values().iterator().next().offline.containsKey(player));
        assertEquals(source, Files.readString(directory.resolve("rooms.json")));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = ServerBoards.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

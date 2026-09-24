package dev.server.boards;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BoardOccupancyTest {
    private ServerBoards plugin(){ServerBoards plugin=mock(ServerBoards.class);plugin.coordinator=mock(BoardOccupancy.class);when(plugin.allowed(any())).thenReturn(true);doCallRealMethod().when(plugin).create(any(),anyString(),anyInt());doCallRealMethod().when(plugin).join(any(),any());return plugin;}
    private Player player(){Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());return p;}
    @Test void reservationRefusalPreventsRoomCreation(){var plugin=plugin();var p=player();assertThrows(IllegalArgumentException.class,()->plugin.create(p,"chess",2));verify(plugin,never()).createReserved(any(),anyString(),anyInt());verify(plugin.coordinator,never()).release(any(),any());}
    @Test void creationFailureReleasesReservation(){var plugin=plugin();var p=player();when(plugin.coordinator.reserve(p.getUniqueId(),"chess")).thenReturn(true);doThrow(new IllegalArgumentException("blocked terrain")).when(plugin).createReserved(p,"chess",2);assertThrows(IllegalArgumentException.class,()->plugin.create(p,"chess",2));verify(plugin.coordinator).release(p.getUniqueId(),"chess");}
    @Test void joinFailureReleasesReservationAndExistingSeatDoesNotReserveTwice(){var plugin=plugin();var p=player();Room room=new Room(UUID.randomUUID(),"chess",2,1,0);when(plugin.coordinator.reserve(p.getUniqueId(),"chess")).thenReturn(true);doThrow(new IllegalArgumentException("full")).when(plugin).joinReserved(p,room);assertThrows(IllegalArgumentException.class,()->plugin.join(p,room));verify(plugin.coordinator).release(p.getUniqueId(),"chess");clearInvocations(plugin.coordinator);room.join(p.getUniqueId(),"P");plugin.join(p,room);verify(plugin.coordinator,never()).reserve(any(),any());verify(plugin).resume(p,room);}
    @Test void exceptionAfterCreationRemovesPartialRoom(){var plugin=plugin();var p=player();Room room=new Room(UUID.randomUUID(),"chess",2,1,0);when(plugin.coordinator.reserve(p.getUniqueId(),"chess")).thenReturn(true);doAnswer(call->{when(plugin.room(p)).thenReturn(room);throw new IllegalStateException("menu failed");}).when(plugin).createReserved(p,"chess",2);assertThrows(IllegalStateException.class,()->plugin.create(p,"chess",2));verify(plugin).remove(room);}
    @Test void exceptionAfterJoinRemovesPartialSeat(){var plugin=plugin();var p=player();Room room=new Room(UUID.randomUUID(),"chess",2,1,0);when(plugin.coordinator.reserve(p.getUniqueId(),"chess")).thenReturn(true);doAnswer(call->{room.join(p.getUniqueId(),"P");throw new IllegalStateException("teleport failed");}).when(plugin).joinReserved(p,room);assertThrows(IllegalStateException.class,()->plugin.join(p,room));assertEquals(-1,room.seat(p.getUniqueId()));}
}

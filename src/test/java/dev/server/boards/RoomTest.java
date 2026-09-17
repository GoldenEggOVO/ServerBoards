package dev.server.boards;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomTest {
    Room room(){return new Room(UUID.randomUUID(),"checkers",3,1L,0);}
    @Test void uniqueMembershipPreservesExistingReadyPlayers(){Room r=room();UUID a=UUID.randomUUID();r.join(a,"a");r.ready.add(a);r.join(a,"a");assertEquals(1,r.seats.size());assertTrue(r.ready.contains(a));r.join(UUID.randomUUID(),"b");assertEquals(Set.of(a),r.ready);r.join(UUID.randomUUID(),"c");assertThrows(IllegalArgumentException.class,()->r.join(UUID.randomUUID(),"d"));}
    @Test void startLocksMembershipAndOldCallbacks(){Room r=room();UUID a=UUID.randomUUID();r.join(a,"a");r.phase=Room.Phase.PLAYING;assertThrows(IllegalArgumentException.class,()->r.join(UUID.randomUUID(),"x"));assertThrows(IllegalArgumentException.class,()->r.requireAction(a,0));assertDoesNotThrow(()->r.requireAction(a,r.revision));r.busy=true;assertThrows(IllegalArgumentException.class,()->r.requireAction(a,r.revision));r.busy=false;assertThrows(IllegalArgumentException.class,()->r.requireAction(UUID.randomUUID(),r.revision));}
    @Test void botIdentityStableAndNeverDuplicatesHuman(){Room r=room();UUID a=UUID.randomUUID();r.join(a,"a");r.fillBots();r.fillBots();assertEquals(3,r.seats.size());assertEquals(3,r.seats.stream().map(Room.Seat::id).distinct().count());assertFalse(r.seats.getFirst().bot());assertTrue(r.seats.getLast().bot());}
    @Test void actionHistoryIsDetached(){Room r=room();JsonObject action=new JsonObject();action.addProperty("type","bid");action.addProperty("value",3);r.event(0,action);action.addProperty("value",0);assertEquals(3,r.history.get(0).getAsJsonObject().getAsJsonObject("action").get("value").getAsInt());}
    @Test void supportedSeatCountsAreExplicit(){assertTrue(ServerBoards.capacityValid("connectfour",2));assertFalse(ServerBoards.capacityValid("connectfour",3));assertFalse(ServerBoards.capacityValid("checkers",5));assertTrue(ServerBoards.capacityValid("checkers",6));assertFalse(ServerBoards.capacityValid("chess",3));assertFalse(ServerBoards.capacityValid("unknown",2));}
}

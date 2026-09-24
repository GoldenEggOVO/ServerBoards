package dev.server.boards;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomReplayVerifierTest {
    @Test void acceptsAValidConnectFourHistory() {
        var source = JsonParser.parseString("""
            {"rooms":[{"id":"a","kind":"connectfour","capacity":2,"seed":1,
            "history":[{"seat":0,"action":"drop:3"},{"seat":1,"action":"drop:4"}]}]}
            """).getAsJsonObject();
        assertDoesNotThrow(() -> RoomReplayVerifier.verify(source));
    }

    @Test void identifiesTheFirstUnreplayableLegacyMove() {
        var source = JsonParser.parseString("""
            {"rooms":[{"id":"legacy-flight","kind":"aeroplane","capacity":4,"seed":1003,
            "history":[{"seat":0,"action":"roll"},{"seat":0,"action":"move:0:to0"},
            {"seat":0,"action":"roll"},{"seat":0,"action":"move:0:sk7"}]}]}
            """).getAsJsonObject();
        var error = assertThrows(IllegalArgumentException.class, () -> RoomReplayVerifier.verify(source));
        assertTrue(error.getMessage().contains("legacy-flight"));
        assertTrue(error.getMessage().contains("event 3"));
    }
}

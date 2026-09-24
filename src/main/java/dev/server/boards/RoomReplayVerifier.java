package dev.server.boards;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.server.boards.rules.GameFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline check that a migration candidate can be replayed by this exact rules build. */
public final class RoomReplayVerifier {
    private RoomReplayVerifier() { }

    static void verify(JsonObject root) {
        if (!root.has("rooms") || !root.get("rooms").isJsonArray())
            throw new IllegalArgumentException("Missing rooms array");
        for (JsonElement element : root.getAsJsonArray("rooms")) {
            JsonObject room = element.getAsJsonObject();
            String id = room.get("id").getAsString();
            try {
                var game = GameFactory.create(room.get("kind").getAsString(),
                    room.get("capacity").getAsInt(), room.get("seed").getAsLong());
                int index = 0;
                for (JsonElement event : room.getAsJsonArray("history")) {
                    JsonObject move = event.getAsJsonObject();
                    try {
                        game.apply(move.get("seat").getAsInt(), move.get("action").getAsString());
                    } catch (RuntimeException ex) {
                        throw new IllegalArgumentException("Room " + id + " event " + index +
                            " cannot replay: " + move, ex);
                    }
                    index++;
                }
            } catch (RuntimeException ex) {
                if (ex.getMessage() != null && ex.getMessage().startsWith("Room " + id + " event ")) throw ex;
                throw new IllegalArgumentException("Room " + id + " cannot replay: " + ex.getMessage(), ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: java -cp server-boards.jar dev.server.boards.RoomReplayVerifier rooms.json");
        JsonObject root = JsonParser.parseString(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8)).getAsJsonObject();
        verify(root);
        System.out.println("Replay verified: " + root.getAsJsonArray("rooms").size() + " rooms");
    }
}

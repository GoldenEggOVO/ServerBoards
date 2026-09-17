package dev.server.boards;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;

/** Keeps every accepted game replay within the worker's create-request limits. */
final class ReplayBudget {
    static final int MAX_EVENTS=4_000;
    static final int MAX_CREATE_BYTES=800_000;
    static final int RESERVED_CREATE_BYTES=4_096;

    private ReplayBudget() {}

    /**
     * Read-only preflight, before either engine receives the next action.
     * Includes the event envelope and a conservative seat integer, not merely
     * the action. The reserved bytes cover room UUID, game, seed and JSON keys.
     * False means the caller must finish the casual game without applying or
     * recording this action. Existing history is never removed or shortened.
     */
    static boolean allows(JsonArray history,JsonElement nextAction) {
        if(history==null||nextAction==null||nextAction.isJsonNull()||history.size()>=MAX_EVENTS)return false;
        JsonObject event=new JsonObject();
        // Actual seats are 0..9. Eleven characters keep this independent of
        // the room capacity and safely cover any Java int seat representation.
        event.addProperty("seat",Integer.MIN_VALUE);
        event.add("action",nextAction);
        long bytes=(long)utf8Length(history)+utf8Length(event)+(history.isEmpty()?0:1)+RESERVED_CREATE_BYTES;
        return bytes<MAX_CREATE_BYTES;
    }

    private static int utf8Length(JsonElement value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}

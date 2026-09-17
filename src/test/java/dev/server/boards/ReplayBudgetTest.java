package dev.server.boards;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ReplayBudgetTest {
    private static JsonObject action(String value){JsonObject action=new JsonObject();action.addProperty("type",value);return action;}
    private static JsonObject event(JsonElement action){JsonObject event=new JsonObject();event.addProperty("seat",9);event.add("action",action);return event;}

    @Test void lastPermittedEventIs4000AndHistoryIsNeverMutated() {
        JsonArray history=new JsonArray();JsonObject next=action("draw");
        for(int i=0;i<3999;i++)history.add(event(next));
        String before=history.toString();
        assertTrue(ReplayBudget.allows(history,next));
        assertEquals(before,history.toString());assertEquals("draw",next.get("type").getAsString());
        history.add(event(next));before=history.toString();
        assertFalse(ReplayBudget.allows(history,next));assertEquals(before,history.toString());
    }

    @Test void countsChineseAndSupplementaryCharactersAsUtf8Bytes() {
        assertTrue(ReplayBudget.allows(new JsonArray(),action("中".repeat(260_000))));
        assertFalse(ReplayBudget.allows(new JsonArray(),action("中".repeat(270_000))));
        assertFalse(ReplayBudget.allows(new JsonArray(),action("🀄".repeat(200_000))));
        JsonArray history=new JsonArray();history.add(event(action("中".repeat(260_000))));
        assertFalse(ReplayBudget.allows(history,action("中".repeat(10_000))));
    }

    @Test void reservesEnvelopeBytesAndUsesStrictLimit() {
        // Empty history [] plus {"seat":-2147483648,"action":""} and reserve.
        JsonObject wrapper=new JsonObject();wrapper.addProperty("seat",Integer.MIN_VALUE);wrapper.addProperty("action","");
        int fixed="[]".getBytes(StandardCharsets.UTF_8).length+wrapper.toString().getBytes(StandardCharsets.UTF_8).length+4096;
        int atLimit=800_000-fixed;
        assertTrue(ReplayBudget.allows(new JsonArray(),new JsonPrimitive("a".repeat(atLimit-1))));
        assertFalse(ReplayBudget.allows(new JsonArray(),new JsonPrimitive("a".repeat(atLimit))));
        // Quotes/newlines must count their JSON escapes, not raw string length.
        assertFalse(ReplayBudget.allows(new JsonArray(),new JsonPrimitive("\n".repeat(atLimit/2+1))));
    }

    @Test void acceptedHistoryFitsCompleteWorkerCreateFrameIncludingUtf8() {
        JsonArray history=new JsonArray();JsonObject next=action("中".repeat(260_000));
        assertTrue(ReplayBudget.allows(history,next));history.add(event(next));
        JsonObject create=new JsonObject();create.addProperty("op","create");create.addProperty("room","ffffffff-ffff-ffff-ffff-ffffffffffff");
        create.addProperty("kind","doudizhu");create.addProperty("players",10);create.addProperty("seed",Long.MIN_VALUE);create.add("history",history);
        int bytes=(create+"\n").getBytes(StandardCharsets.UTF_8).length;
        assertTrue(bytes<800_000);assertTrue(history.size()<=4000);
    }

    @Test void nullInputsFailClosed() {
        assertFalse(ReplayBudget.allows(null,action("draw")));
        assertFalse(ReplayBudget.allows(new JsonArray(),null));
        assertFalse(ReplayBudget.allows(new JsonArray(),JsonNull.INSTANCE));
    }
}

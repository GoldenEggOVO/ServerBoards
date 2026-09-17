package dev.server.boards.rules;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoardContractsTest {
    @Test void factoryRejectsUnsupportedPlayerCountsAndGames() {
        assertThrows(IllegalArgumentException.class, () -> GameFactory.create("gomoku", 3, 0));
        assertThrows(IllegalArgumentException.class, () -> GameFactory.create("xiangqi", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> GameFactory.create("chess", 4, 0));
        assertThrows(IllegalArgumentException.class, () -> GameFactory.create("checkers", 5, 0));
        assertThrows(IllegalArgumentException.class, () -> GameFactory.create("aeroplane", 6, 0));
        assertThrows(IllegalArgumentException.class, () -> GameFactory.create("unknown", 2, 0));
    }
    @Test void immutablePublicViewsAndRejectedActionsNeverMutate() {
        for (String id : List.of("gomoku", "xiangqi", "checkers", "aeroplane", "chess")) {
            BoardGame game = GameFactory.create(id, 2, 17);
            List<Cell> before = game.cells(); Map<String,String> info = game.publicInfo();
            List<Cell> savedSnapshot = new ArrayList<>(before);
            List<String> actions = game.legalActions(0);
            assertThrows(UnsupportedOperationException.class, () -> before.clear());
            assertThrows(UnsupportedOperationException.class, () -> info.clear());
            assertThrows(UnsupportedOperationException.class, () -> actions.clear());
            assertThrows(IllegalArgumentException.class, () -> game.apply(1, actions.getFirst()));
            assertThrows(IllegalArgumentException.class, () -> game.apply(0, "move:999:0"));
            assertThrows(IllegalArgumentException.class, () -> game.apply(0, null));
            assertEquals(before, game.cells(), id);
            assertEquals(info, game.publicInfo(), id);
            assertEquals(actions, game.legalActions(0), id);
            assertTrue(game.legalActions(-1).isEmpty()); assertTrue(game.legalActions(99).isEmpty());
            assertEquals(before.size(), before.stream().map(Cell::id).distinct().count(), id);
            game.apply(0, actions.getFirst());
            assertEquals(savedSnapshot, before, "previous views must remain frozen after a legal action");
        }
    }
    @Test void deterministicFlightReplayIncludesDiceAndRejectedRollDoesNotConsumeEntropy() {
        BoardGame a = GameFactory.create("aeroplane", 4, 98), b = GameFactory.create("aeroplane", 4, 98);
        for (int i = 0; i < 200 && !a.finished(); i++) {
            String action = a.legalActions(a.currentPlayer()).getFirst();
            assertThrows(IllegalArgumentException.class, () -> a.apply(-1, "roll"));
            a.apply(a.currentPlayer(), action); b.apply(b.currentPlayer(), action);
            assertEquals(a.cells(), b.cells()); assertEquals(a.publicInfo(), b.publicInfo());
        }
    }
}

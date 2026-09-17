package dev.server.boards.rules;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChineseCheckersGameTest {
    @SuppressWarnings("unchecked") private static void position(ChineseCheckersGame game, Map<String,Integer> pegs) throws Exception {
        Field field = ChineseCheckersGame.class.getDeclaredField("occupied"); field.setAccessible(true);
        Map<String,Integer> occupied = (Map<String,Integer>) field.get(game);
        occupied.clear(); occupied.putAll(pegs);
    }
    @Test void standardStarHas121HolesAndTenPiecesPerActiveSeat() {
        for (int players : new int[]{2,3,4,6}) {
            BoardGame game = new ChineseCheckersGame(players);
            assertEquals(121, game.cells().size());
            assertEquals(players * 10, game.cells().stream().filter(c -> c.owner() >= 0).count());
            for (int seat = 0; seat < players; seat++) {
                int s = seat;
                assertEquals(10, game.cells().stream().filter(c -> c.owner() == s).count());
            }
        }
    }
    @Test void adjacentStepAndMultiJumpAreGeneratedWithoutCapturing() throws Exception {
        ChineseCheckersGame game = new ChineseCheckersGame(2);
        position(game, Map.of("4,8", 0, "5,8", 1, "7,8", 1));
        assertTrue(game.legalActions(0).contains("move:4,8:3,8"));
        assertTrue(game.legalActions(0).contains("move:4,8:6,8"));
        assertTrue(game.legalActions(0).contains("move:4,8:8,8"));
        assertFalse(game.legalActions(0).contains("move:4,8:4,8"));
        game.apply(0, "move:4,8:8,8");
        assertEquals(3, game.cells().stream().filter(c -> c.owner() >= 0).count());
        assertEquals(0, game.cells().stream().filter(c -> c.id().equals("8,8")).findFirst().orElseThrow().owner());
        assertTrue(game.publicInfo().get("lastAction").contains("6,8"));
    }
    @Test void distantPegCannotBeUsedForLongJump() throws Exception {
        ChineseCheckersGame game = new ChineseCheckersGame(2);
        position(game, Map.of("4,8", 0, "6,8", 1));
        assertFalse(game.legalActions(0).contains("move:4,8:8,8"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "move:4,8:8,8"));
    }
    @Test void goalCampCannotBeLeft() throws Exception {
        ChineseCheckersGame game = new ChineseCheckersGame(2);
        position(game, Map.of("4,3", 0));
        assertFalse(game.legalActions(0).contains("move:4,3:4,4"));
        assertTrue(game.legalActions(0).contains("move:4,3:5,2"));
    }
    @Test void fillingTenthGoalHoleWins() throws Exception {
        ChineseCheckersGame game = new ChineseCheckersGame(2);
        Map<String,Integer> pegs = new HashMap<>();
        for (String id : List.of("6,0","5,1","6,1","5,2","6,2","7,2","5,3","6,3","7,3","4,4")) pegs.put(id,0);
        position(game, pegs);
        game.apply(0, "move:4,4:4,3");
        assertEquals("winner:0", game.outcome());
        assertTrue(game.legalActions(1).isEmpty());
    }
    @Test void multiplayerRandomFixturesConserveAllPegsAndRejectWrongTurn() {
        for (int players : new int[]{2,3,4,6}) {
            BoardGame game = new ChineseCheckersGame(players); Random random = new Random(players);
            for (int i = 0; i < 100 && !game.finished(); i++) {
                int seat = game.currentPlayer();
                List<String> actions = game.legalActions(seat);
                assertFalse(actions.isEmpty());
                String action = actions.get(random.nextInt(actions.size()));
                assertThrows(IllegalArgumentException.class, () -> game.apply((seat + 1) % players, action));
                game.apply(seat, action);
                assertEquals(players * 10, game.cells().stream().filter(c -> c.owner() >= 0).count());
            }
        }
    }
}

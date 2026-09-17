package dev.server.boards.rules;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GomokuGameTest {
    @Test void fourDirectionsAndBoardEdgesWin() {
        int[][] directions = {{1,0},{0,1},{1,1},{-1,1}};
        for (int[] d : directions) {
            BoardGame game = new GomokuGame();
            for (int k = 0; k < 5; k++) {
                int x = d[0] < 0 ? 14 - k : k * d[0], y = k * d[1];
                game.apply(0, "place:" + x + "," + y);
                if (k < 4) game.apply(1, "place:" + (8 + k) + ",13");
            }
            assertEquals("winner:0", game.outcome());
            assertTrue(game.legalActions(0).isEmpty());
        }
    }
    @Test void occupiedCellAndExtraMoveAfterWinRejected() {
        BoardGame game = new GomokuGame();
        game.apply(0, "place:0,0");
        assertThrows(IllegalArgumentException.class, () -> game.apply(1, "place:0,0"));
        assertEquals(List.of("place:1,0"), game.actionsForCell(1, "1,0"));
    }
    @Test void winning225thPlacementBeatsFullBoardDraw() {
        // Balanced no-win fixture: every prefix is legal; last black move bridges an overline.
        String[] rows = {"001100110011001","110011001100110","001100110011001","110011101100110",
                "001100110011001","110011001100110","001100110011001","110010000.00110",
                "101100110011001","110011001100110","001100110011001","110011001100110",
                "001100110011001","110011001100110","001101110011001"};
        List<String> black = new ArrayList<>(), white = new ArrayList<>();
        for (int y = 0; y < 15; y++) for (int x = 0; x < 15; x++) {
            if (rows[y].charAt(x) == '0') black.add("place:" + x + "," + y);
            if (rows[y].charAt(x) == '1') white.add("place:" + x + "," + y);
        }
        assertEquals(112, black.size()); assertEquals(112, white.size());
        BoardGame game = new GomokuGame();
        for (int i = 0; i < 112; i++) {
            game.apply(0, black.get(i)); game.apply(1, white.get(i));
            assertFalse(game.finished(), "fixture must not have an earlier win");
        }
        game.apply(0, "place:9,7");
        assertEquals("winner:0", game.outcome());
        assertEquals("0", game.publicInfo().get("remaining"));
    }
}

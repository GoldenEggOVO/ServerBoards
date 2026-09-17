package dev.server.boards.rules;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChessGameTest {
    @Test void startingPositionHas20MovesAndNoExternalEngine() {
        BoardGame game = new ChessGame();
        assertEquals(20,game.legalActions(0).size()); assertEquals(32,game.cells().stream().filter(c->c.owner()>=0).count());
    }
    @Test void castleMovesKingAndRookAndCannotPassThroughAttack() {
        BoardGame game = new ChessGame("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertTrue(game.legalActions(0).contains("move:e1:g1")); assertTrue(game.legalActions(0).contains("move:e1:c1"));
        game.apply(0,"move:e1:g1");
        assertEquals("王",cell(game,"g1").piece()); assertEquals("车",cell(game,"f1").piece()); assertEquals(-1,cell(game,"h1").owner());
        BoardGame attacked = new ChessGame("r3kr1r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertFalse(attacked.legalActions(0).contains("move:e1:g1"));
    }
    @Test void enPassantAvailableImmediatelyAndExpires() {
        BoardGame game = new ChessGame();
        for (String action : List.of("move:e2:e4","move:a7:a6","move:e4:e5","move:d7:d5")) game.apply(game.currentPlayer(),action);
        assertTrue(game.legalActions(0).contains("move:e5:d6")); game.apply(0,"move:e5:d6");
        assertEquals(-1,cell(game,"d5").owner()); assertEquals(0,cell(game,"d6").owner());
        BoardGame expired = new ChessGame("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        expired.apply(0,"move:e1:e2"); expired.apply(1,"move:e8:e7");
        assertFalse(expired.legalActions(0).contains("move:e5:d6"));
    }
    @Test void promotionRequiresExplicitChoiceAndAllFourTypesAreSupported() {
        for (String promotion : List.of("q","r","b","n")) {
            BoardGame game = new ChessGame("7k/P7/8/8/8/8/8/7K w - - 0 1");
            List<String> options = game.actionsForCell(0,"a8");
            assertEquals(4,options.size());
            assertThrows(IllegalArgumentException.class, ()->game.apply(0,"move:a7:a8"));
            game.apply(0,"move:a7:a8:"+promotion);
            assertNotEquals("兵",cell(game,"a8").piece()); assertEquals(0,cell(game,"a8").owner());
        }
    }
    @Test void foolsMateEndsInBlackWinAndStalemateDraws() {
        BoardGame game = new ChessGame();
        for (String action : List.of("move:f2:f3","move:e7:e5","move:g2:g4","move:d8:h4")) game.apply(game.currentPlayer(),action);
        assertEquals("winner:1",game.outcome()); assertTrue(game.legalActions(0).isEmpty());
        assertEquals("draw:stalemate",new ChessGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").outcome());
    }
    @Test void threefoldAndFiftyMoveAndInsufficientMaterialDraws() {
        BoardGame game = new ChessGame();
        for (int round=0; round<2; round++) for (String action : List.of("move:g1:f3","move:g8:f6","move:f3:g1","move:f6:g8")) game.apply(game.currentPlayer(),action);
        assertEquals("draw:threefold-repetition",game.outcome());
        BoardGame fifty = new ChessGame("7k/8/8/8/8/8/R7/K7 w - - 99 60");
        fifty.apply(0,"move:a2:b2"); assertEquals("draw:50-move-rule",fifty.outcome());
        assertEquals("draw:insufficient-material",new ChessGame("7k/8/8/8/8/8/8/K7 w - - 0 1").outcome());
    }
    @Test void mixedMinorAndTwoKnightPositionsAreNotAutomaticallyDead() {
        assertFalse(new ChessGame("7k/6n1/8/8/8/8/1N6/K7 w - - 0 1").finished());
        assertFalse(new ChessGame("7k/8/8/8/8/8/1NN5/K7 w - - 0 1").finished());
        assertEquals("draw:insufficient-material", new ChessGame("7k/8/8/8/8/8/1B6/K7 w - - 0 1").outcome());
    }
    private static Cell cell(BoardGame game,String id) { return game.cells().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(); }
}

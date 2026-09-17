package dev.server.boards.rules;

import dev.server.boards.rules.upstream.xiangqi.model.*;
import dev.server.boards.rules.upstream.xiangqi.utility.Point;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XiangqiGameTest {
    static Board empty() {
        Board board = new Board();
        board.setPieceAt(new Point(0,3), new General(0));
        board.setPieceAt(new Point(9,5), new General(1));
        return board;
    }
    @Test void standardInitialPositionHas32PiecesAnd44Moves() {
        BoardGame game = new XiangqiGame();
        assertEquals(32, game.cells().stream().filter(c -> c.owner() >= 0).count());
        assertEquals(44, game.legalActions(0).size());
    }
    @Test void elephantEyeAndRiverAreEnforcedIncludingUpstreamMissingRiverFix() {
        Board board = empty(); board.setPieceAt(new Point(4,2), new Elephant(0));
        BoardGame game = new XiangqiGame(board);
        assertFalse(game.legalActions(0).contains("move:2,4:4,6"));
        assertTrue(game.legalActions(0).contains("move:2,4:4,2"));
        board.setPieceAt(new Point(3,3), new Soldier(0));
        assertFalse(new XiangqiGame(board).legalActions(0).contains("move:2,4:4,2"));
        board = empty(); board.setTurn(1); board.setPieceAt(new Point(5,6), new Elephant(1));
        assertFalse(new XiangqiGame(board).legalActions(1).contains("move:6,5:4,3"));
    }
    @Test void horseLegAndCannonScreen() {
        Board board = empty(); board.setPieceAt(new Point(4,4), new Horse(0));
        board.setPieceAt(new Point(5,4), new Soldier(0));
        assertFalse(new XiangqiGame(board).legalActions(0).contains("move:4,4:5,6"));
        board = empty(); board.setPieceAt(new Point(4,0), new Cannon(0));
        board.setPieceAt(new Point(4,2), new Soldier(0)); board.setPieceAt(new Point(4,5), new Chariot(1));
        assertTrue(new XiangqiGame(board).legalActions(0).contains("move:0,4:5,4"));
        board.setPieceAt(new Point(4,2), null);
        assertFalse(new XiangqiGame(board).legalActions(0).contains("move:0,4:5,4"));
    }
    @Test void cannotExposeFacingGeneralsOrIgnoreCheck() {
        Board board = new Board();
        board.setPieceAt(new Point(0,4), new General(0)); board.setPieceAt(new Point(9,4), new General(1));
        board.setPieceAt(new Point(4,4), new Chariot(0));
        BoardGame game = new XiangqiGame(board);
        assertFalse(game.legalActions(0).contains("move:4,4:5,4"));
        assertThrows(IllegalArgumentException.class, () -> game.apply(0, "move:4,4:5,4"));
    }
    @Test void passiveThreefoldIsDraw() {
        BoardGame game = new XiangqiGame();
        String[] cycle = {"move:1,0:2,2","move:1,9:2,7","move:2,2:1,0","move:2,7:1,9"};
        for (int round = 0; round < 2; round++) for (String action : cycle) game.apply(game.currentPlayer(), action);
        assertEquals("draw:threefold-repetition", game.outcome());
    }
    @Test void singleSidePerpetualCheckLoses() {
        Board board = new Board();
        board.setPieceAt(new Point(0,4), new General(0)); board.setPieceAt(new Point(9,4), new General(1));
        board.setPieceAt(new Point(5,4), new Soldier(0)); board.setPieceAt(new Point(7,5), new Chariot(0));
        BoardGame game = new XiangqiGame(board);
        String[] cycle = {"move:5,7:4,7","move:4,9:5,9","move:4,7:5,7","move:5,9:4,9"};
        for (int round = 0; round < 2; round++) for (String action : cycle) game.apply(game.currentPlayer(), action);
        assertEquals("winner:1", game.outcome());
        assertTrue(game.publicInfo().get("resultReason").contains("长将"));
    }
    @Test void stalemateIsLossInXiangqi() {
        Board board = new Board();
        board.setPieceAt(new Point(0,3), new General(0)); board.setPieceAt(new Point(9,4), new General(1));
        board.setPieceAt(new Point(8,3), new Soldier(0)); board.setPieceAt(new Point(8,5), new Soldier(0));
        board.setTurn(1);
        assertEquals("winner:0", new XiangqiGame(board).outcome());
    }
}

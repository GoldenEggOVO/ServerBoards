package dev.server.boards.rules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConnectFourGameTest {
    private BoardGame game(){return GameFactory.create("connectfour",2,1);}
    private void play(BoardGame game,int... columns){for(int col:columns)game.apply(game.currentPlayer(),"drop:"+col);}
    @Test void gravityTurnsAndFullColumn(){var g=game();assertEquals(42,g.cells().size());g.apply(0,"drop:3");assertEquals(0,g.cells().stream().filter(c->c.id().equals("3,0")).findFirst().orElseThrow().owner());assertThrows(IllegalArgumentException.class,()->g.apply(0,"drop:2"));play(g,3,3,3,3,3);assertFalse(g.legalActions(g.currentPlayer()).contains("drop:3"));assertThrows(IllegalArgumentException.class,()->g.apply(g.currentPlayer(),"drop:3"));assertEquals(6,g.cells().stream().filter(c->c.owner()>=0).count());}
    @Test void horizontalWinAndTerminalRejection(){var g=game();play(g,0,0,1,1,2,2,3);assertEquals("winner:0",g.outcome());assertTrue(g.legalActions(1).isEmpty());assertThrows(IllegalArgumentException.class,()->g.apply(1,"drop:6"));}
    @Test void verticalWin(){var g=game();play(g,0,1,0,1,0,1,0);assertEquals("winner:0",g.outcome());}
    @Test void bothDiagonalWins(){for(boolean mirror:new boolean[]{false,true}){var g=game();int[] sequence={0,1,1,2,4,2,2,3,4,3,5,3,3};for(int col:sequence)g.apply(g.currentPlayer(),"drop:"+(mirror?6-col:col));assertEquals("winner:0",g.outcome());}}
    @Test void invalidInputDoesNotChangeTurn(){var g=game();for(String a:new String[]{"drop:-1","drop:7","drop:x","place:0,0","drop:0:1"})assertThrows(IllegalArgumentException.class,()->g.apply(0,a));assertEquals(0,g.currentPlayer());assertEquals(7,g.legalActions(0).size());}
    @Test void clicksOnAnyHeightSelectColumn(){var g=game();assertEquals(java.util.List.of("drop:2"),g.actionsForCell(0,"2,5"));}
    @Test void fullBoardWithoutFourIsDraw(){var g=game();play(g,5,3,6,5,5,0,4,6,1,0,4,3,3,2,4,6,3,1,5,0,5,4,1,6,2,5,4,3,4,2,6,2,0,1,2,6,2,3,0,1,0,1);assertEquals("draw:board-full",g.outcome());assertTrue(g.finished());assertTrue(g.legalActions(g.currentPlayer()).isEmpty());}
}

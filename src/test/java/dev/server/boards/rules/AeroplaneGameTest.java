package dev.server.boards.rules;

import dev.server.boards.rules.upstream.aeroplane.Aeroplane;
import dev.server.boards.rules.upstream.aeroplane.FlightMoves;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AeroplaneGameTest {
    private static Aeroplane p(int color, String cell) { return new Aeroplane(color, cell); }
    @Test void launchRequiresSixAndUsesTakeoffCell() {
        Aeroplane[] planes = {p(0,"ba0")};
        assertThrows(IllegalArgumentException.class, () -> FlightMoves.move(planes,0,2));
        assertEquals("ba0", planes[0].getInCellId());
        FlightMoves.move(planes,0,6); assertEquals("to0",planes[0].getInCellId());
        FlightMoves.move(planes,0,1); assertEquals("sk1",planes[0].getInCellId());
    }
    @Test void takeoffCountsEveryVisibleCellForAllColorsAndDice() {
        for (int color=0;color<4;color++) for(int roll=1;roll<=6;roll++) {
            Aeroplane[] launch={p(color,"to"+color)};
            int expected=(color*13+roll)%52;
            if(expected%4==color) expected=(expected+4)%52;
            FlightMoves.move(launch,0,roll);
            assertEquals("sk"+expected,launch[0].getInCellId(),"color="+color+", dice="+roll);
        }
    }
    @Test void jumpFlightAndHomeApproachAreAeroplaneRules() {
        Aeroplane[] planes = {p(0,"sk3")};
        FlightMoves.move(planes,0,1); assertEquals("sk8",planes[0].getInCellId());
        planes[0] = p(0,"sk19"); FlightMoves.move(planes,0,1); assertEquals("sk32",planes[0].getInCellId());
        planes[0] = p(0,"sk0"); FlightMoves.move(planes,0,1); assertEquals("ld0",planes[0].getInCellId());
    }
    @Test void exactFinishAndOvershootBounceStayInOwnLaneForEveryColor() {
        for (int color = 0; color < 4; color++) {
            Aeroplane[] planes = {p(color,"ld" + (color * 5 + 4))};
            FlightMoves.move(planes,0,3); assertEquals("ld" + (color * 5 + 3),planes[0].getInCellId());
            FlightMoves.move(planes,0,2); assertEquals("go" + color,planes[0].getInCellId());
            assertThrows(IllegalArgumentException.class, () -> FlightMoves.move(planes,0,1));
        }
    }
    @Test void singleCollisionCapturesButEnemyStackIsBlockade() {
        Aeroplane[] planes = {p(0,"sk5"),p(1,"sk6")};
        FlightMoves.move(planes,0,1); assertEquals("sk6",planes[0].getInCellId()); assertEquals("ba1",planes[1].getInCellId());
        planes = new Aeroplane[]{p(0,"sk5"),p(1,"sk6"),p(1,"sk6")};
        FlightMoves.move(planes,0,1); assertEquals("ba0",planes[0].getInCellId()); assertEquals("sk6",planes[1].getInCellId());
    }
    @Test void tripleSixReturnsUnfinishedPlanesButProtectsArrivals() throws Exception {
        long seed = 0;
        for (;; seed++) { SplittableRandom rng = new SplittableRandom(seed); if (rng.nextInt(1,7)==6 && rng.nextInt(1,7)==6 && rng.nextInt(1,7)==6) break; }
        AeroplaneGame game = new AeroplaneGame(2, seed);
        Field field = AeroplaneGame.class.getDeclaredField("planes"); field.setAccessible(true);
        Aeroplane[] planes = (Aeroplane[])field.get(game); planes[1].setInCellId("go0");
        game.apply(0,"roll");
        assertEquals(1,game.actionsForCell(0,"ba0_0").size());
        game.apply(0,game.legalActions(0).getFirst());
        game.apply(0,"roll"); game.apply(0,game.legalActions(0).getFirst()); game.apply(0,"roll");
        assertEquals(1,game.currentPlayer()); assertEquals("ba0",planes[0].getInCellId()); assertEquals("go0",planes[1].getInCellId());
        assertTrue(game.publicInfo().get("lastAction").contains("第三个"));
    }
    @Test void allSeatsHaveFourPlanesAndUniqueCrossBoardPositions() {
        for (int players=2; players<=4; players++) {
            BoardGame game = new AeroplaneGame(players,0);
            assertEquals(96, game.cells().size());
            assertEquals(96,game.cells().stream().map(c->c.x()+","+c.y()).distinct().count());
            assertEquals(52,game.cells().stream().filter(c->c.id().startsWith("sk")).count());
            assertEquals(players * 4,game.cells().stream().filter(c->c.owner()>=0).count());
        }
    }
    @Test void completeDeterministicGamesForTwoThreeAndFourSeats() {
        for (int players=2; players<=4; players++) for (int fixture=0; fixture<4; fixture++) {
            BoardGame game = new AeroplaneGame(players,players * 100L + fixture); Random moves = new Random(fixture);
            int actions=0;
            while (!game.finished() && actions++ < 20000) {
                List<String> legal = game.legalActions(game.currentPlayer()); assertFalse(legal.isEmpty());
                game.apply(game.currentPlayer(), legal.get(moves.nextInt(legal.size())));
            }
            assertTrue(game.finished(), "flight fixture must finish within budget");
            assertTrue(game.outcome().startsWith("winner:"));
        }
    }
}

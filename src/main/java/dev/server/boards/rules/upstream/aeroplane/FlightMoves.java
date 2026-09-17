package dev.server.boards.rules.upstream.aeroplane;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapted from kan01234/aeroplanes-chess MoveUtils, MIT (license bundled).
 * Retains the 52-cell clockwise route, color*13 turn, +4 jump, +12 flight,
 * five approach cells plus goal, bounce and stacked-plane collision rule.
 * Changes: no Spring fields; always four route colors even with fewer seats;
 * launch requires 6; goal IDs are per-color; completed aircraft are protected.
 */
public final class FlightMoves {
    private static final int CIRCLE = 52;
    private FlightMoves() { }

    public static void move(Aeroplane[] planes, int planeIndex, int roll) {
        Aeroplane plane = planes[planeIndex];
        int color = plane.getColor();
        String cell = plane.getInCellId();
        String prefix = cell.substring(0, 2);
        int number = Integer.parseInt(cell.substring(2));
        int count = roll;
        if (prefix.equals("go")) throw new IllegalArgumentException("飞机已经抵达终点");
        if (prefix.equals("ba")) {
            if (roll != 6) throw new IllegalArgumentException("掷出 6 才能起飞");
            plane.setInCellId("to" + color);
            return;
        }
        if (prefix.equals("to")) { prefix = "sk"; number = color * 13 + 1; count--; }
        while (count > 0) {
            if (prefix.equals("sk") && number == color * 13) { prefix = "ld"; number = color * 5; }
            else number = (number + 1) % CIRCLE;
            count--;
        }
        if (prefix.equals("sk") && number % 4 == color) {
            int flightFrom = ((color + 1) * 13 + 7) % CIRCLE;
            if (number == flightFrom) number = (number + 12) % CIRCLE;
            else if (number != color * 13) number = (number + 4) % CIRCLE;
        }
        List<Integer> encountered = new ArrayList<>();
        for (int i = 0; i < planes.length; i++) {
            if (planes[i].getColor() != color && planes[i].getInCellId().equals(prefix + number)) encountered.add(i);
        }
        if (encountered.size() > 1) { prefix = "ba"; number = color; }
        else if (encountered.size() == 1) {
            Aeroplane victim = planes[encountered.getFirst()];
            victim.setInCellId("ba" + victim.getColor());
        }
        if (prefix.equals("ld")) {
            int finish = (color + 1) * 5;
            if (number == finish) { prefix = "go"; number = color; }
            else if (number > finish) number = 2 * finish - number;
        }
        plane.setInCellId(prefix + number);
    }

    public static void unfinishedBackToBase(Aeroplane[] planes, int color) {
        for (Aeroplane plane : planes) if (plane.getColor() == color && !plane.getInCellId().startsWith("go"))
            plane.setInCellId("ba" + color);
    }
}

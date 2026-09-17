package dev.server.boards.rules;

import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Pure-Java adaptation of NucleoidMC/Gomoku Board (MIT, see META-INF/licenses).
 * The four directional run scans are retained; render code is separated and a
 * winning final placement is checked before a full-board draw.
 */
public final class GomokuGame implements BoardGame {
    private static final int SIZE = 15, WIN = 5;
    private final int[][] board = new int[SIZE][SIZE];
    private int current, empty = SIZE * SIZE;
    private String result = "ongoing", lastAction = "尚未落子";

    public GomokuGame() { for (int[] row : board) Arrays.fill(row, -1); }
    @Override public String id() { return "gomoku"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return current; }
    @Override public boolean finished() { return !result.equals("ongoing"); }
    @Override public String outcome() { return result; }
    @Override public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(SIZE * SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int p = board[y][x];
            out.add(new Cell(x + "," + y, x, y, p == 0 ? "黑" : p == 1 ? "白" : "", p));
        }
        return List.copyOf(out);
    }
    @Override public List<String> legalActions(int seat) {
        if (finished() || seat != current) return List.of();
        List<String> out = new ArrayList<>(empty);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            if (board[y][x] == -1) out.add("place:" + x + "," + y);
        return List.copyOf(out);
    }
    @Override public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action)) throw new IllegalArgumentException("不是有效落子");
        String[] xy = action.substring(6).split(",");
        int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
        board[y][x] = seat; empty--;
        lastAction = (seat == 0 ? "黑方" : "白方") + "落子 " + (x + 1) + "," + (y + 1);
        if (checkWin(seat, d -> x + d, d -> y) || checkWin(seat, d -> x, d -> y + d)
                || checkWin(seat, d -> x + d, d -> y + d) || checkWin(seat, d -> x - d, d -> y + d)) result = "winner:" + seat;
        else if (empty == 0) result = "draw:board-full";
        else current = 1 - current;
    }
    private boolean checkWin(int piece, IntUnaryOperator x, IntUnaryOperator y) {
        int max = 0, run = 0;
        for (int d = -WIN; d <= WIN; d++) {
            int cx = x.applyAsInt(d), cy = y.applyAsInt(d);
            if (cx < 0 || cy < 0 || cx >= SIZE || cy >= SIZE || board[cy][cx] != piece) run = 0;
            else max = Math.max(max, ++run);
        }
        return max >= WIN;
    }
    @Override public Map<String, String> publicInfo() {
        return Map.of("rules", "15×15 自由五子棋；连五或以上获胜；无禁手", "rulesVariant", "freestyle-15",
                "phase", finished() ? "对局结束" : "等待落子", "turn", current == 0 ? "黑方" : "白方",
                "lastAction", lastAction, "remaining", String.valueOf(empty));
    }
}

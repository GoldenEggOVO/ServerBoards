package dev.server.boards.rules;

import dev.server.boards.rules.upstream.xiangqi.model.*;
import java.util.*;

/** Room-safe adapter around James Wang's MIT Xiangqi model. */
public final class XiangqiGame implements BoardGame {
    private final Board board;
    private String result = "ongoing", lastAction = "红方先行", reason = "";
    private int noCapturePly;
    private final Map<String, List<Integer>> positions = new HashMap<>();
    private final List<CheckRecord> checks = new ArrayList<>();
    private Map<String, Move> cached;
    private record CheckRecord(int seat, boolean check) { }

    public XiangqiGame() { this(Board.initialPosition()); }
    // Only package tests can inject positions; production always starts with a normal board.
    XiangqiGame(Board position) {
        board = position.copy();
        positions.put(positionKey(), new ArrayList<>(List.of(0)));
        if (moves().isEmpty()) { result = "winner:" + (1 - currentPlayer()); reason = "无合法着法判负"; }
    }
    @Override public String id() { return "xiangqi"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return board.turn() % 2; }
    @Override public boolean finished() { return !result.equals("ongoing"); }
    @Override public String outcome() { return result; }
    @Override public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>(90);
        for (int row = 0; row < 10; row++) for (int col = 0; col < 9; col++) {
            Piece p = board.pieceAt(row, col);
            cells.add(new Cell(col + "," + row, col, row, p == null ? "" : symbol(p), p == null ? -1 : p.getColor()));
        }
        return List.copyOf(cells);
    }
    private static String symbol(Piece piece) {
        return switch (Math.abs(piece.getCode())) {
            case 1 -> piece.getColor() == 0 ? "帅" : "将";
            case 2 -> piece.getColor() == 0 ? "仕" : "士";
            case 3 -> piece.getColor() == 0 ? "相" : "象";
            case 4 -> "马"; case 5 -> "车"; case 6 -> "炮";
            case 7 -> piece.getColor() == 0 ? "兵" : "卒";
            default -> throw new IllegalStateException("Unknown piece");
        };
    }
    private Map<String, Move> moves() {
        if (cached == null) {
            Map<String, Move> map = new LinkedHashMap<>();
            for (Move move : board.legalMoves()) map.put(action(move), move);
            cached = Collections.unmodifiableMap(map);
        }
        return cached;
    }
    private static String action(Move move) {
        return "move:" + move.start.y + "," + move.start.x + ":" + move.end.y + "," + move.end.x;
    }
    @Override public List<String> legalActions(int seat) {
        return finished() || seat != currentPlayer() ? List.of() : List.copyOf(moves().keySet());
    }
    @Override public void apply(int seat, String action) {
        if (finished() || seat != currentPlayer() || action == null || !moves().containsKey(action))
            throw new IllegalArgumentException("不是有效着法");
        Move move = moves().get(action);
        noCapturePly = move.target == null ? noCapturePly + 1 : 0;
        lastAction = (seat == 0 ? "红方" : "黑方") + symbol(move.piece) + " " + action.substring(5);
        board.move(move); cached = null;
        checks.add(new CheckRecord(seat, inCheck(currentPlayer())));
        if (moves().isEmpty()) {
            result = "winner:" + seat;
            reason = "将死或困毙";
            return;
        }
        List<Integer> occurrences = positions.computeIfAbsent(positionKey(), ignored -> new ArrayList<>());
        occurrences.add(checks.size());
        if (occurrences.size() >= 3) adjudicateRepetition(occurrences.get(occurrences.size() - 3));
        if (!finished() && noCapturePly >= 120) { result = "draw:120-ply-no-capture"; reason = "连续 60 回合无吃子和棋"; }
    }
    private void adjudicateRepetition(int fromPly) {
        boolean[] allChecking = {true, true};
        int[] count = {0, 0};
        for (int i = fromPly; i < checks.size(); i++) {
            CheckRecord check = checks.get(i);
            count[check.seat()]++;
            allChecking[check.seat()] &= check.check();
        }
        for (int i = 0; i < 2; i++) allChecking[i] &= count[i] >= 2;
        if (allChecking[0] != allChecking[1]) {
            int checkingSeat = allChecking[0] ? 0 : 1;
            result = "winner:" + (1 - checkingSeat);
            reason = (checkingSeat == 0 ? "红方" : "黑方") + "单方长将判负";
        } else {
            result = "draw:threefold-repetition";
            reason = "同局面三次重复和棋";
        }
    }
    private boolean inCheck(int seat) {
        Board attack = board.copy();
        attack.setTurn(1 - seat);
        int kingCode = seat == 0 ? 1 : -1;
        return attack.candidateMoves().stream().anyMatch(m -> m.target != null && m.target.getCode() == kingCode);
    }
    private String positionKey() {
        StringBuilder key = new StringBuilder().append(currentPlayer()).append(':');
        for (int row = 0; row < 10; row++) for (int col = 0; col < 9; col++) {
            Piece p = board.pieceAt(row, col);
            key.append((char) ('h' + (p == null ? 0 : p.getCode())));
        }
        return key.toString();
    }
    @Override public Map<String, String> publicInfo() {
        return Map.of("rules", "中国象棋；困毙判负；三次重复和棋，单方长将判负；60 回合无吃子和棋",
                "rulesVariant", "casual-threefold-perpetual-check", "ruleLimit", "休闲规则：长捉按三次重复处理，不作比赛级长捉裁定",
                "phase", finished() ? "对局结束" : inCheck(currentPlayer()) ? "将军，必须应将" : "等待走棋",
                "turn", currentPlayer() == 0 ? "红方" : "黑方", "lastAction", lastAction, "resultReason", reason);
    }
}

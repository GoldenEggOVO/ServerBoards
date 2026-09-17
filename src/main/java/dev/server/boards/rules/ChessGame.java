package dev.server.boards.rules;

import dev.server.boards.rules.upstream.chesslib.Board;
import dev.server.boards.rules.upstream.chesslib.Piece;
import dev.server.boards.rules.upstream.chesslib.PieceType;
import dev.server.boards.rules.upstream.chesslib.Side;
import dev.server.boards.rules.upstream.chesslib.Square;
import dev.server.boards.rules.upstream.chesslib.move.Move;
import java.util.*;

/** Standard chess using the Apache-2.0 chesslib move generator, without Stockfish. */
public final class ChessGame implements BoardGame {
    private final Board board = new Board();
    private String result = "ongoing", lastAction = "白方先行", reason = "";
    private Map<String, Move> cached;
    public ChessGame() { }
    ChessGame(String fen) { board.loadFromFen(fen); updateResult(); }
    @Override public String id() { return "chess"; }
    @Override public int playerCount() { return 2; }
    @Override public int currentPlayer() { return board.getSideToMove() == Side.WHITE ? 0 : 1; }
    @Override public boolean finished() { return !result.equals("ongoing"); }
    @Override public String outcome() { return result; }
    private static String id(Square square) { return square.name().toLowerCase(Locale.ROOT); }
    private static String action(Move move) {
        String action = "move:" + id(move.getFrom()) + ":" + id(move.getTo());
        return move.getPromotion() == Piece.NONE ? action : action + ":" + move.getPromotion().getFenSymbol().toLowerCase(Locale.ROOT);
    }
    @Override public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(64);
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece p = board.getPiece(square);
            out.add(new Cell(id(square), square.getFile().ordinal(), square.getRank().ordinal(), p == Piece.NONE ? "" : symbol(p),
                    p == Piece.NONE ? -1 : p.getPieceSide() == Side.WHITE ? 0 : 1));
        }
        return List.copyOf(out);
    }
    private static String symbol(Piece piece) {
        return switch (piece.getPieceType()) {
            case KING -> "王"; case QUEEN -> "后"; case ROOK -> "车"; case BISHOP -> "象"; case KNIGHT -> "马"; case PAWN -> "兵";
            default -> "";
        };
    }
    private Map<String, Move> moves() {
        if (cached == null) {
            Map<String, Move> moves = new LinkedHashMap<>();
            for (Move move : board.legalMoves()) moves.put(action(move), move);
            cached = Collections.unmodifiableMap(moves);
        }
        return cached;
    }
    @Override public List<String> legalActions(int seat) { return finished() || seat != currentPlayer() ? List.of() : List.copyOf(moves().keySet()); }
    @Override public List<String> actionsForCell(int seat, String cellId) {
        if (cellId == null) return List.of();
        return legalActions(seat).stream().filter(action -> {
            String[] parts = action.split(":");
            return parts[1].equals(cellId) || parts[2].equals(cellId);
        }).toList();
    }
    @Override public void apply(int seat, String action) {
        if (finished() || seat != currentPlayer() || action == null || !moves().containsKey(action)) throw new IllegalArgumentException("不是有效国际象棋着法");
        Move move = moves().get(action);
        if (!board.doMove(move, true)) throw new IllegalStateException("规则引擎拒绝已经验证的着法");
        lastAction = (seat == 0 ? "白方 " : "黑方 ") + action.substring(5);
        if (move.getPromotion() != Piece.NONE) lastAction += " 升变为" + symbol(move.getPromotion());
        cached = null;
        updateResult();
    }
    private void updateResult() {
        // Checkmate takes precedence over a halfmove draw on the same final move.
        if (board.isMated()) { result = "winner:" + (1 - currentPlayer()); reason = "将死"; }
        else if (board.isStaleMate()) { result = "draw:stalemate"; reason = "逼和"; }
        else if (deadMaterial()) { result = "draw:insufficient-material"; reason = "子力不足以将死"; }
        else if (board.isRepetition()) { result = "draw:threefold-repetition"; reason = "同局面三次重复"; }
        else if (board.getHalfMoveCounter() >= 100) { result = "draw:50-move-rule"; reason = "50 回合无吃子且无兵移动"; }
    }
    private boolean deadMaterial() {
        // Upstream marks several four-piece mixed-minor positions as dead. A
        // cooperative mate can exist there. Only automatically adjudicate the
        // unambiguous material cases; do not confuse inability to FORCE mate with
        // inability to reach ANY mating position (notably KNN v K and KN v KN).
        List<Square> minors = new ArrayList<>();
        for (Square square : Square.values()) {
            if (square == Square.NONE) continue;
            Piece p = board.getPiece(square);
            if (p == Piece.NONE || p.getPieceType() == PieceType.KING) continue;
            if (p.getPieceType() != PieceType.BISHOP && p.getPieceType() != PieceType.KNIGHT) return false;
            minors.add(square);
        }
        if (minors.size() <= 1) return true;
        if (minors.stream().anyMatch(s -> board.getPiece(s).getPieceType() != PieceType.BISHOP)) return false;
        return minors.stream().allMatch(s -> s.isLightSquare() == minors.getFirst().isLightSquare());
    }
    @Override public Map<String, String> publicInfo() {
        return Map.of("rules", "标准国际象棋：王车易位、吃过路兵、兵升变；将死获胜，逼和/子力不足和棋",
                "rulesVariant", "standard-auto-claim-draws", "ruleLimit", "休闲模式自动执行三次重复与 50 回合和棋，无需另行申请",
                "phase", finished() ? "对局结束" : board.isKingAttacked() ? "将军，必须应将" : "等待走棋",
                "turn", currentPlayer() == 0 ? "白方" : "黑方", "lastAction", lastAction, "resultReason", reason, "fen", board.getFen());
    }
}

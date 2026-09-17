package dev.server.boards.rules;

import java.util.Locale;

public final class GameFactory {
    private GameFactory() { }
    public static BoardGame create(String game, int players, long seed) {
        if (game == null) throw new IllegalArgumentException("游戏类型不能为空");
        return switch (game.toLowerCase(Locale.ROOT)) {
            case "connectfour" -> { requireTwo(players); yield new ConnectFourGame(); }
            case "gomoku" -> { requireTwo(players); yield new GomokuGame(); }
            case "xiangqi" -> { requireTwo(players); yield new XiangqiGame(); }
            case "chess" -> { requireTwo(players); yield new ChessGame(); }
            case "draughts" -> { requireTwo(players); yield new DraughtsGame(); }
            case "reversi" -> { requireTwo(players); yield new ReversiGame(); }
            case "go", "go19" -> { requireTwo(players); yield new GoGame(19); }
            case "go9" -> { requireTwo(players); yield new GoGame(9); }
            case "go13" -> { requireTwo(players); yield new GoGame(13); }
            case "yacht" -> new YachtGame(players,seed);
            case "aeroplane", "flying" -> new AeroplaneGame(players, seed);
            case "checkers", "chinese-checkers" -> new ChineseCheckersGame(players);
            default -> throw new IllegalArgumentException("未知游戏：" + game);
        };
    }
    private static void requireTwo(int players) {
        if (players != 2) throw new IllegalArgumentException("此游戏需要两人");
    }
}

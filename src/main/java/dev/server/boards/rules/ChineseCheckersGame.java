package dev.server.boards.rules;

import dev.server.boards.rules.upstream.checkers.Board;
import dev.server.boards.rules.upstream.checkers.Point;
import java.util.*;

/**
 * Standard 121-hole star using Stephane Coutant's GPL board geometry and original
 * starting camps. Network/player state is separate; only adjacent-peg short jumps
 * are enabled, instead of the upstream long-jump variant.
 */
public final class ChineseCheckersGame implements BoardGame {
    // Exact six camps from upstream Game(boolean[]); colors 0..5 clockwise from bottom.
    private static final int[][][] CAMPS = {
        {{6,16},{5,15},{6,15},{5,14},{6,14},{7,14},{4,13},{5,13},{6,13},{7,13}},
        {{0,12},{1,12},{2,12},{3,12},{0,11},{1,11},{2,11},{1,10},{2,10},{1,9}},
        {{0,4},{1,4},{2,4},{3,4},{0,5},{1,5},{2,5},{1,6},{2,6},{1,7}},
        {{6,0},{5,1},{6,1},{5,2},{6,2},{7,2},{4,3},{5,3},{6,3},{7,3}},
        {{9,4},{10,4},{11,4},{12,4},{9,5},{10,5},{11,5},{10,6},{11,6},{10,7}},
        {{9,12},{10,12},{11,12},{12,12},{9,11},{10,11},{11,11},{10,10},{11,10},{10,9}}
    };
    private final Board geometry = new Board();
    private final int[] colors;
    private final Map<String, Integer> occupied = new LinkedHashMap<>();
    private final Map<String, Integer> repeats = new HashMap<>();
    private int current, passes;
    private String result = "ongoing", lastAction = "等待第一步";
    private Map<String, List<String>> cached;

    public ChineseCheckersGame(int players) {
        colors = switch (players) {
            case 2 -> new int[]{0, 3};
            case 3 -> new int[]{0, 2, 4};
            case 4 -> new int[]{1, 2, 4, 5};
            case 6 -> new int[]{0, 1, 2, 3, 4, 5};
            default -> throw new IllegalArgumentException("标准跳棋支持 2、3、4 或 6 人");
        };
        for (int seat = 0; seat < colors.length; seat++) for (int[] xy : CAMPS[colors[seat]]) occupied.put(xy[0] + "," + xy[1], seat);
        repeats.put(positionKey(), 1);
    }
    @Override public String id() { return "checkers"; }
    @Override public int playerCount() { return colors.length; }
    @Override public int currentPlayer() { return current; }
    @Override public boolean finished() { return !result.equals("ongoing"); }
    @Override public String outcome() { return result; }
    @Override public List<Cell> cells() {
        List<Cell> out = new ArrayList<>(121);
        for (int j = 0; j < Board.sizeJ; j++) for (int i = 0; i < Board.sizeI; i++) {
            if (!Board.hole(new Point(i, j))) continue;
            String id = i + "," + j;
            int owner = occupied.getOrDefault(id, -1);
            // Double x plus row parity preserves the hex-grid stagger for generic renderers.
            out.add(new Cell(id, 2 * i + j % 2, j, owner < 0 ? "" : "●", owner));
        }
        return List.copyOf(out);
    }
    private Set<String> target(int seat) {
        Set<String> target = new HashSet<>();
        for (int[] xy : CAMPS[(colors[seat] + 3) % 6]) target.add(xy[0] + "," + xy[1]);
        return target;
    }
    private static Point point(String id) {
        String[] parts = id.split(",");
        return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
    private static String id(Point p) { return p.i + "," + p.j; }
    private Map<String, List<String>> moves() {
        if (cached != null) return cached;
        Map<String, List<String>> out = new TreeMap<>();
        Set<String> goal = target(current);
        for (var entry : occupied.entrySet()) {
            if (entry.getValue() != current) continue;
            String origin = entry.getKey();
            Point start = point(origin);
            for (int d = 0; d < 6; d++) {
                Point to = geometry.hop(start, d);
                if (to != null && !occupied.containsKey(id(to)) && (!goal.contains(origin) || goal.contains(id(to))))
                    out.put("move:" + origin + ":" + id(to), List.of(origin, id(to)));
            }
            // Other pieces stay fixed during a multi-jump. The moving piece's origin
            // is empty for the entire search, so it can never become its own bridge.
            ArrayDeque<List<String>> queue = new ArrayDeque<>();
            Set<String> seen = new HashSet<>(); seen.add(origin); queue.add(List.of(origin));
            while (!queue.isEmpty()) {
                List<String> path = queue.remove();
                String from = path.getLast();
                for (int d = 0; d < 6; d++) {
                    Point middle = geometry.hop(point(from), d);
                    if (middle == null || id(middle).equals(origin) || !occupied.containsKey(id(middle))) continue;
                    Point to = geometry.hop(middle, d);
                    if (to == null) continue;
                    String destination = id(to);
                    if (occupied.containsKey(destination) || seen.contains(destination)) continue;
                    if (goal.contains(from) && !goal.contains(destination)) continue;
                    seen.add(destination);
                    List<String> next = new ArrayList<>(path); next.add(destination);
                    List<String> frozen = List.copyOf(next);
                    out.put("move:" + origin + ":" + destination, frozen);
                    queue.add(frozen);
                }
            }
        }
        if (out.isEmpty()) out.put("pass", List.of());
        cached = Collections.unmodifiableMap(out);
        return cached;
    }
    @Override public List<String> legalActions(int seat) {
        return finished() || seat != current ? List.of() : List.copyOf(moves().keySet());
    }
    @Override public void apply(int seat, String action) {
        if (finished() || seat != current || action == null || !moves().containsKey(action)) throw new IllegalArgumentException("不是有效跳棋移动");
        if (action.equals("pass")) { passes++; lastAction = "玩家 " + (seat + 1) + " 无合法移动，跳过"; }
        else {
            List<String> path = moves().get(action);
            occupied.remove(path.getFirst()); occupied.put(path.getLast(), seat); passes = 0;
            lastAction = "玩家 " + (seat + 1) + " " + String.join(" → ", path);
            if (target(seat).stream().allMatch(p -> occupied.getOrDefault(p, -1) == seat)) result = "winner:" + seat;
        }
        cached = null;
        if (finished()) return;
        current = (current + 1) % colors.length;
        if (passes >= colors.length) result = "draw:blocked";
        else if (repeats.merge(positionKey(), 1, Integer::sum) >= 3) result = "draw:threefold-repetition";
    }
    private String positionKey() { return current + ":" + new TreeMap<>(occupied); }
    @Override public Map<String, String> publicInfo() {
        return Map.of("rules", "六角星 121 孔，每人 10 子；邻格步行或隔一子连续短跳；先占满对营获胜",
                "rulesVariant", "standard-star-short-jumps", "ruleLimit", "进入目标营后不能离开；允许经过其他营地；三次重复和棋",
                "phase", finished() ? "对局结束" : "等待走棋", "turn", "玩家 " + (current + 1),
                "lastAction", lastAction, "colors", Arrays.toString(colors), "coordinateSystem", "x=2*column+row%2,y=row");
    }
}

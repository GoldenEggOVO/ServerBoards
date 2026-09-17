package dev.server.boards.rules;

import dev.server.boards.rules.upstream.aeroplane.Aeroplane;
import dev.server.boards.rules.upstream.aeroplane.FlightMoves;
import java.util.*;

/** Aeroplane Chess, not Ludo. Seed is supplied by the authoritative room service. */
public final class AeroplaneGame implements BoardGame {
    private final int[] colors;
    private final Aeroplane[] planes;
    private final SplittableRandom random;
    private int current, pendingRoll, lastRoll, sixes;
    private String result = "ongoing", lastAction = "等待掷骰";
    private static final LinkedHashMap<String, int[]> GEOMETRY = geometry();
    private Map<String, Integer> cached;

    public AeroplaneGame(int players, long seed) {
        colors = switch (players) {
            case 2 -> new int[]{0, 2}; case 3 -> new int[]{0, 1, 2}; case 4 -> new int[]{0, 1, 2, 3};
            default -> throw new IllegalArgumentException("飞行棋支持 2 到 4 人");
        };
        planes = new Aeroplane[players * 4];
        for (int i = 0; i < planes.length; i++) planes[i] = new Aeroplane(colors[i / 4], "ba" + colors[i / 4]);
        random = new SplittableRandom(seed);
    }
    @Override public String id() { return "aeroplane"; }
    @Override public int playerCount() { return colors.length; }
    @Override public int currentPlayer() { return current; }
    @Override public boolean finished() { return !result.equals("ongoing"); }
    @Override public String outcome() { return result; }
    private static String cellId(Aeroplane plane, int index) {
        String cell = plane.getInCellId(); int color = plane.getColor();
        if (cell.startsWith("ba")) return cell + "_" + (index % 4);
        if (cell.startsWith("ld")) return "ld" + color + "_" + (Integer.parseInt(cell.substring(2)) - color * 5);
        return cell;
    }
    @Override public List<Cell> cells() {
        Map<String, List<Integer>> occupants = new HashMap<>();
        for (int i = 0; i < planes.length; i++) occupants.computeIfAbsent(cellId(planes[i], i), ignored -> new ArrayList<>()).add(i);
        List<Cell> out = new ArrayList<>(GEOMETRY.size());
        for (var cell : GEOMETRY.entrySet()) {
            List<Integer> indices = occupants.getOrDefault(cell.getKey(), List.of());
            String piece = indices.isEmpty() ? "" : "✈" + String.join("", indices.stream().map(i -> String.valueOf(i % 4 + 1)).toList());
            out.add(new Cell(cell.getKey(), cell.getValue()[0], cell.getValue()[1], piece, indices.isEmpty() ? -1 : indices.getFirst() / 4));
        }
        return List.copyOf(out);
    }
    private static Aeroplane[] copy(Aeroplane[] original) {
        return Arrays.stream(original).map(p -> new Aeroplane(p.getColor(), p.getInCellId())).toArray(Aeroplane[]::new);
    }
    private Map<String, Integer> moves() {
        if (cached != null) return cached;
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = current * 4; i < current * 4 + 4; i++) {
            String p = planes[i].getInCellId();
            if (p.startsWith("go") || p.startsWith("ba") && pendingRoll != 6) continue;
            Aeroplane[] forecast = copy(planes);
            FlightMoves.move(forecast, i, pendingRoll);
            out.put("move:" + i + ":" + cellId(forecast[i], i), i);
        }
        cached = Collections.unmodifiableMap(out);
        return cached;
    }
    @Override public List<String> legalActions(int seat) {
        if (finished() || seat != current) return List.of();
        return pendingRoll == 0 ? List.of("roll") : List.copyOf(moves().keySet());
    }
    @Override public List<String> actionsForCell(int seat, String cell) {
        if (cell == null || finished() || seat != current || pendingRoll == 0) return List.of();
        return moves().entrySet().stream().filter(e -> cellId(planes[e.getValue()], e.getValue()).equals(cell)
                || e.getKey().endsWith(":" + cell)).map(Map.Entry::getKey).toList();
    }
    @Override public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action)) throw new IllegalArgumentException("不是有效飞行棋操作");
        if (action.equals("roll")) {
            pendingRoll = lastRoll = random.nextInt(1, 7); cached = null;
            lastAction = "玩家 " + (seat + 1) + " 掷出 " + lastRoll;
            if (lastRoll == 6 && ++sixes == 3) {
                FlightMoves.unfinishedBackToBase(planes, colors[seat]);
                lastAction += "，连续第三个 6，未到终点飞机回营";
                next(false);
            } else if (moves().isEmpty()) {
                lastAction += "，没有可移动的飞机";
                next(lastRoll == 6);
            }
            return;
        }
        int index = moves().get(action);
        FlightMoves.move(planes, index, pendingRoll);
        lastAction = "玩家 " + (seat + 1) + " 移动飞机 " + (index % 4 + 1) + " → " + cellId(planes[index], index);
        boolean win = true;
        for (int i = seat * 4; i < seat * 4 + 4; i++) win &= planes[i].getInCellId().startsWith("go");
        if (win) { result = "winner:" + seat; pendingRoll = 0; cached = null; }
        else next(pendingRoll == 6);
    }
    private void next(boolean again) {
        if (!again) { current = (current + 1) % colors.length; sixes = 0; }
        pendingRoll = 0; cached = null;
    }
    @Override public Map<String, String> publicInfo() {
        return Map.of("rules", "每人 4 架；6 起飞并连掷；同色跳 4、捷径飞 12；精确冲线，超出回弹",
                "rulesVariant", "aeroplane-six-launch-single-bonus-blockade", "ruleLimit", "跳/飞每次只奖励一次；撞单机击落，撞叠机自身回营；第三个连续 6 令未完成飞机回营",
                "phase", finished() ? "对局结束" : pendingRoll == 0 ? "等待掷骰" : "选择飞机",
                "turn", "玩家 " + (current + 1), "lastAction", lastAction, "dice", String.valueOf(lastRoll),
                "pendingRoll", String.valueOf(pendingRoll), "colors", Arrays.toString(colors));
    }
    private static LinkedHashMap<String, int[]> geometry() {
        LinkedHashMap<String, int[]> out = new LinkedHashMap<>();
        List<int[]> ring = new ArrayList<>();
        ring.add(new int[]{7,0});
        for (int y = 0; y <= 5; y++) ring.add(new int[]{6,y});
        for (int x = 5; x >= 0; x--) ring.add(new int[]{x,6});
        ring.add(new int[]{0,7}); ring.add(new int[]{0,8});
        for (int x = 1; x <= 5; x++) ring.add(new int[]{x,8});
        for (int y = 9; y <= 14; y++) ring.add(new int[]{6,y});
        ring.add(new int[]{7,14}); ring.add(new int[]{8,14});
        for (int y = 13; y >= 9; y--) ring.add(new int[]{8,y});
        for (int x = 9; x <= 14; x++) ring.add(new int[]{x,8});
        ring.add(new int[]{14,7}); ring.add(new int[]{14,6});
        for (int x = 13; x >= 9; x--) ring.add(new int[]{x,6});
        for (int y = 5; y >= 0; y--) ring.add(new int[]{8,y});
        if (ring.size() != 52) throw new IllegalStateException("52-cell flight route required");
        for (int i = 0; i < 52; i++) out.put("sk" + i, ring.get(i));
        for (int color = 0; color < 4; color++) {
            for (int step = 0; step < 6; step++) {
                int[] p = rotate(7, step + 1, color);
                out.put(step == 5 ? "go" + color : "ld" + color + "_" + step, p);
            }
            out.put("to" + color, rotate(5, 0, color));
            for (int i = 0; i < 4; i++) out.put("ba" + color + "_" + i, rotate(2 + 2 * (i % 2), 2 + 2 * (i / 2), color));
        }
        return out;
    }
    private static int[] rotate(int x, int y, int quarter) {
        for (int i = 0; i < quarter; i++) { int next = y; y = 14 - x; x = next; }
        return new int[]{x, y};
    }
}

package dev.server.boards.probe;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only plugin for an isolated, loopback Purpur server. */
public final class BoardsStandaloneProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::runProbe, 80);
    }

    private void runProbe() {
        try {
            require(Bukkit.getIp().equals("127.0.0.1") && Bukkit.getPort() == 25617, "loopback fixture");
            if (Boolean.getBoolean("boards.probe.migration")) {
                migrationProbe();
                return;
            }
            if (Boolean.getBoolean("boards.probe.menu")) {
                menuProbe();
                return;
            }
            if (Boolean.getBoolean("boards.probe.sgmenu")) {
                sgMenuProbe();
                return;
            }
            for (String absent : List.of("ServerGames", "ServerMenu", "ServerCasino", "KaMenu"))
                require(Bukkit.getPluginManager().getPlugin(absent) == null, "unexpected " + absent);
            Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerBoards"));
            require(boards.isEnabled(), "Boards enabled");
            World world = Bukkit.getWorlds().getFirst();
            PluginCommand command = Objects.requireNonNull(Bukkit.getPluginCommand("serverboards:boards"));
            AtomicInteger dialogs = new AtomicInteger();
            Player player = player(world, dialogs, true);
            Player denied = player(world, dialogs, false);
            int before = dialogs.get();
            command.execute(denied, "boards", new String[0]);
            require(dialogs.get() == before, "permission denied");
            command.execute(player, "boards", new String[0]);
            require(dialogs.get() == before + 1, "native catalog opened");
            Object menus = field(boards, "menus");
            Map<?, ?> sessions = (Map<?, ?>) field(menus, "sessions");
            Object session = sessions.get(player.getUniqueId());
            require(session != null, "menu session exists");
            Method buttons = session.getClass().getDeclaredMethod("buttons");
            buttons.setAccessible(true);
            List<?> entries = (List<?>) buttons.invoke(session);
            Method token = session.getClass().getDeclaredMethod("token");
            token.setAccessible(true);
            String action = "boards:" + token.invoke(session) + " 0";
            int shown = dialogs.get();
            call(menus, "handle", new Class<?>[]{Player.class, String.class}, player, action);
            require(dialogs.get() == shown + 1, "menu callback opened next native dialog");
            call(menus, "handle", new Class<?>[]{Player.class, String.class}, player, action);
            require(dialogs.get() == shown + 1, "callback consumed once");
            require(!entries.isEmpty(), "catalog entries");

            Map<?, ?> rooms = (Map<?, ?>) field(boards, "rooms");
            Path marker = getDataFolder().toPath().resolve("created-room.txt");
            if (Files.exists(marker)) {
                require(rooms.size() == 1, "one room restored");
                Object room = rooms.values().iterator().next();
                require(field(room, "anchorWorld").equals(world.getUID()), "world anchor restored");
                require(field(room, "board") != null, "board replay restored");
                require(Files.readString(boards.getDataFolder().toPath().resolve("rooms.json")).contains("drop:3"), "move history retained");
                require(!world.getEntitiesByClass(TextDisplay.class).isEmpty(), "board entities rendered");
                getLogger().info("BOARDS_STANDALONE_RESTORE_PASS rooms=1 dialogs=" + dialogs.get());
            } else {
                require(rooms.isEmpty(), "fresh fixture");
                call(boards, "create", new Class<?>[]{Player.class, String.class, int.class}, player, "connectfour", 2);
                require(rooms.size() == 1, "room created");
                Object room = rooms.values().iterator().next();
                call(boards, "startWithBots", new Class<?>[]{Player.class, room.getClass()}, player, room);
                command.execute(player, "boards", new String[]{"move", "drop:3"});
                Path data = boards.getDataFolder().toPath().resolve("rooms.json");
                require(Files.readString(data).contains("drop:3"), "core rule action persisted");
                require(!world.getEntitiesByClass(TextDisplay.class).isEmpty(), "board entities rendered");
                Files.createDirectories(getDataFolder().toPath());
                Files.writeString(marker, String.valueOf(field(room, "id")));
                getLogger().info("BOARDS_STANDALONE_CREATE_PASS rooms=1 dialogs=" + dialogs.get());
            }
        } catch (Throwable ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "BOARDS_STANDALONE_PROBE_FAIL", ex);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sgMenuProbe() throws Exception {
        Plugin games = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerGames"));
        Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerBoards"));
        require(games.isEnabled() && boards.isEnabled(), "optional plugins enabled");
        ClassLoader loader = games.getClass().getClassLoader();
        Class<?> api = Class.forName("dev.server.games.api.GameCoordinator", true, loader);
        Object service = Bukkit.getServicesManager().load((Class) api);
        require(service != null, "optional coordinator exists");
        Collection<?> providers = (Collection<?>) api.getMethod("providers").invoke(service);
        Class<?> providerType = Class.forName("dev.server.games.api.GameProvider", true, loader);
        Set<String> kinds = new HashSet<>();
        for (Object provider : providers) kinds.add((String) providerType.getMethod("id").invoke(provider));
        require(Collections.disjoint(kinds, List.of("connectfour", "chess", "xiangqi", "go9")), "Boards has no ServerGames providers");
        UUID playerId = UUID.fromString("00d14a82-6b6c-46ce-b7f8-ecc56a133420");
        require(((Map<?, ?>) field(field(boards, "coordinator"), "seats")).containsKey(playerId), "restored seat owned by Boards");
        Optional<?> occupied = (Optional<?>) api.getMethod("occupiedBy", UUID.class).invoke(service, playerId);
        require(occupied.isEmpty(), "ServerGames does not own Boards seat");
        AtomicInteger dialogs = new AtomicInteger();
        Player player = player(Bukkit.getWorlds().getFirst(), dialogs, true);
        Objects.requireNonNull(Bukkit.getPluginCommand("servergames:servergames"))
            .execute(player, "sg", new String[]{"menu"});
        require(dialogs.get() == 1, "/sg menu opens Boards Dialog");
        getLogger().info("BOARDS_SG_MENU_PASS providers=" + kinds.size() + " dialogs=" + dialogs.get());
    }

    @SuppressWarnings("unchecked")
    private void menuProbe() throws Exception {
        Plugin menu = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerMenu"));
        Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerBoards"));
        require(menu.isEnabled() && boards.isEnabled(), "optional menu enabled");
        AtomicInteger dialogs = new AtomicInteger();
        Player player = player(Bukkit.getWorlds().getFirst(), dialogs, true);
        ClassLoader loader = menu.getClass().getClassLoader();
        Class<?> view = Class.forName("dev.server.menu.DialogView", true, loader);
        Class<?> daily = Class.forName("dev.server.menu.DailyMenus", true, loader);
        var constructor = daily.getDeclaredConstructor(menu.getClass(), view);
        constructor.setAccessible(true);
        Object model = constructor.newInstance(menu, field(menu, "dialogs"));
        Object page = call(model, "build", new Class<?>[]{Player.class, String.class, int.class}, player, "main", 0);
        List<?> entries = (List<?>) call(page, "entries", new Class<?>[0]);
        int before = dialogs.get();
        boolean found = false;
        for (Object entry : entries) {
            if (!"games".equals(call(entry, "id", new Class<?>[0]))) continue;
            ((java.util.function.Consumer<Map<String, String>>) call(entry, "action", new Class<?>[0])).accept(Map.of());
            found = true;
            break;
        }
        require(found && dialogs.get() == before + 1, "ServerMenu games entry opens Boards Dialog");
        getLogger().info("BOARDS_MENU_PASS dialogs=" + dialogs.get());
    }

    private void migrationProbe() throws Exception {
        for (String absent : List.of("ServerGames", "ServerMenu", "ServerCasino", "KaMenu"))
            require(Bukkit.getPluginManager().getPlugin(absent) == null, "unexpected " + absent);
        Plugin boards = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("ServerBoards"));
        require(boards.isEnabled(), "Boards restored legacy rooms");
        Map<?, ?> rooms = (Map<?, ?>) field(boards, "rooms");
        require(rooms.size() == 4, "four replayable legacy chess rooms restored");
        Set<String> kinds = new HashSet<>();
        int history = 0;
        for (Object room : rooms.values()) {
            kinds.add((String) field(room, "kind"));
            require(field(room, "board") != null, "rules replayed");
            require(field(room, "anchorWorld").equals(Bukkit.getWorlds().getFirst().getUID()), "explicit anchor used");
            Object moves = field(room, "history");
            history += (int) moves.getClass().getMethod("size").invoke(moves);
        }
        require(kinds.containsAll(List.of("gomoku", "xiangqi", "chess", "checkers")), "legacy kinds");
        require(history >= 21, "legacy history kept: " + history);
        require(!Bukkit.getWorlds().getFirst().getEntitiesByClass(TextDisplay.class).isEmpty(), "legacy boards rendered");
        getLogger().info("BOARDS_MIGRATION_PASS rooms=4 history=" + history);
    }

    private Player player(World world, AtomicInteger dialogs, boolean allowed) {
        UUID id = allowed ? UUID.fromString("00d14a82-6b6c-46ce-b7f8-ecc56a133420") : UUID.randomUUID();
        Location location = new Location(world, 0.5, 83, 0.5);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "BoardsProbe";
                case "getWorld" -> world;
                case "getLocation", "getEyeLocation" -> location.clone();
                case "getServer" -> getServer();
                case "isOnline", "isValid", "isPermissionSet" -> true;
                case "isDead", "isOp" -> false;
                case "hasPermission" -> allowed;
                case "teleport" -> true;
                case "showDialog" -> { require(args[0] != null, "native dialog built"); dialogs.incrementAndGet(); yield null; }
                case "sendMessage", "sendActionBar" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> "BoardsProbe";
                default -> null;
            });
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

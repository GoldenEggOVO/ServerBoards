package dev.server.boards;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.*;

/** Display only. GameMenus owns authorization and single-use sessions. */
final class BoardWindow {
    private final ServerBoards plugin;
    private final GameMenuLayouts layouts;

    BoardWindow(ServerBoards plugin) {
        this.plugin = plugin;
        layouts = new GameMenuLayouts(plugin);
    }

    GameMenuLayouts.Rendered render(String page, String title, String description,
                                   List<GameMenus.Button> buttons, UUID token) {
        return layouts.load(page, title, description, buttons, token);
    }

    boolean open(Player player, YamlConfiguration config, String page) {
        List<DialogBody> body = new ArrayList<>();
        var sections = config.getConfigurationSection("Body");
        if (sections != null) for (String key : sections.getKeys(false)) {
            String path = "Body." + key;
            if (config.contains(path + ".text")) body.add(DialogBody.plainMessage(
                text(config.getString(path + ".text", "")), config.getInt(path + ".width", 360)));
        }
        List<ActionButton> buttons = new ArrayList<>();
        var entries = config.getConfigurationSection("Bottom.buttons");
        if (entries != null) for (String key : entries.getKeys(false)) {
            buttons.add(button(player, config, "Bottom.buttons." + key));
        }
        var exit = config.contains("Bottom.exit") ? button(player, config, "Bottom.exit") : null;
        var base = DialogBase.create(text(config.getString("Title", "棋盘游戏")), null,
            true, false, DialogBase.DialogAfterAction.CLOSE, body, List.of());
        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(
            DialogType.multiAction(buttons, exit, Math.clamp(config.getInt("Bottom.columns", 2), 1, 3)))));
        return true;
    }

    private ActionButton button(Player player, YamlConfiguration config, String path) {
        List<String> actions = config.getStringList(path + ".actions");
        var action = actions.isEmpty() ? null : DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(player.getUniqueId()) || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled() && actor.isOnline()) plugin.menus.handle(actor, actions.getFirst());
            });
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build());
        String tooltip = String.join("\n", config.getStringList(path + ".tooltip"));
        return ActionButton.create(text(config.getString(path + ".text", "关闭")),
            tooltip.isBlank() ? null : text(tooltip), Math.clamp(config.getInt(path + ".width", 174), 1, 1024), action);
    }

    static Component text(String value) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(value.replace('§', '&'));
    }
}

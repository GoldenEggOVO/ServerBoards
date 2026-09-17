package dev.server.boards;
import dev.server.games.api.GameProvider;
import org.bukkit.entity.Player;
import java.util.UUID;
final class BoardProvider implements GameProvider {
    private final ServerBoards plugin;private final String kind;
    BoardProvider(ServerBoards plugin,String kind){this.plugin=plugin;this.kind=kind;}
    public String id(){return kind;}
    public String displayName(){return ServerBoards.gameName(kind);}
    public void open(Player player){plugin.menus.games(player,kind);}
    public boolean create(Player player,String mode){plugin.create(player,kind,mode==null||mode.isBlank()?ServerBoards.defaultCapacity(kind):Integer.parseInt(mode));return true;}
    public boolean join(Player player,String tableId){Room room=plugin.find(tableId);if(!room.kind.equals(kind))throw new IllegalArgumentException("房间游戏类型不匹配");plugin.join(player,room);return true;}
    public void leave(Player player){Room room=plugin.room(player);if(room!=null&&room.kind.equals(kind))plugin.leave(player);}
    public boolean active(UUID playerId){Room room=plugin.room(playerId);return room!=null&&room.kind.equals(kind);}
}

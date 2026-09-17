package dev.server.boards;

import com.google.gson.*;
import dev.server.boards.rules.BoardGame;
import java.util.*;

/** Main-thread room state. No Minecraft types: identity and replay are independently testable. */
final class Room {
    enum Phase { LOBBY, STARTING, PLAYING, PAUSED, FINISHED, ABORTED }
    record Seat(UUID id, String name, boolean bot) {}
    final UUID id; final String kind; final int capacity; long seed; final int table;
    UUID anchorWorld; double anchorX,anchorY,anchorZ;
    RoundActions.Undo undo;
    final List<Seat> seats=new ArrayList<>(); final Set<UUID> ready=new HashSet<>();
    final Map<UUID,Long> offline=new HashMap<>(); final JsonArray history=new JsonArray();
    Phase phase=Phase.LOBBY; long revision=0; long changed=System.currentTimeMillis();
    boolean busy=false; boolean restoring=false; boolean completed=false; BoardGame board; String result="";
    Room(UUID id,String kind,int capacity,long seed,int table) { this.id=id;this.kind=kind;this.capacity=capacity;this.seed=seed;this.table=table; }
    int seat(UUID player) { for(int i=0;i<seats.size();i++) if(seats.get(i).id().equals(player)) return i; return -1; }
    void join(UUID player,String name) {
        if(seat(player)>=0) return;
        if(phase!=Phase.LOBBY || seats.size()>=capacity) throw new IllegalArgumentException("这个房间已经满员或开局");
        seats.add(new Seat(player,name,false)); changed=System.currentTimeMillis(); revision++;
    }
    void fillBots() { while(seats.size()<capacity) {int i=seats.size();seats.add(new Seat(UUID.nameUUIDFromBytes((id+":"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"陪练"+(i+1),true));} }
    void requireAction(UUID player,long expected) {
        if(phase!=Phase.PLAYING || busy || undo!=null || revision!=expected || seat(player)<0) throw new IllegalArgumentException("对局已更新或正在协商悔棋，请重新打开牌桌");
    }
    int turn(){return board!=null?board.currentPlayer():-1;}
    String name(){return ServerBoards.gameName(kind)+" · "+id.toString().substring(0,6);}
    void event(int seat,JsonElement action){JsonObject e=new JsonObject();e.addProperty("seat",seat);e.add("action",action.deepCopy());history.add(e);}
}

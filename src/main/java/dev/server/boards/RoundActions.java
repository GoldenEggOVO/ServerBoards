package dev.server.boards;

import dev.server.boards.rules.GameFactory;
import java.util.*;

/** Consent and fresh rounds; reconstruct before committing any takeback. */
final class RoundActions {
    static final class Undo {
        final UUID requester;final int cut;final long expires;final Set<UUID> pending;
        Undo(UUID requester,int cut,long expires,Set<UUID> pending){this.requester=requester;this.cut=cut;this.expires=expires;this.pending=pending;}
    }
    static void request(Room r,UUID player,long now){
        int seat=r.seat(player);
        if(seat<0||r.board==null||r.busy||r.undo!=null||r.phase!=Room.Phase.PLAYING&&r.phase!=Room.Phase.FINISHED)throw new IllegalArgumentException("当前不能申请悔棋");
        int cut=r.history.size()-1;while(cut>=0&&r.history.get(cut).getAsJsonObject().get("seat").getAsInt()!=seat)cut--;
        if(cut<0)throw new IllegalArgumentException("你还没有可撤销的回合");
        if(Set.of("draughts","yacht","aeroplane").contains(r.kind))while(cut>0&&r.history.get(cut-1).getAsJsonObject().get("seat").getAsInt()==seat)cut--;
        Set<UUID> others=new HashSet<>();for(Room.Seat s:r.seats)if(!s.bot()&&!s.id().equals(player))others.add(s.id());
        r.undo=new Undo(player,cut,now+30_000,others);r.revision++;
    }
    static boolean approve(Room r,UUID player,long now){
        if(r.undo==null||now>=r.undo.expires||!r.undo.pending.remove(player))throw new IllegalArgumentException("悔棋申请已失效或无需你确认");
        r.revision++;return r.undo.pending.isEmpty();
    }
    static void reject(Room r,UUID player){if(r.undo==null||r.seat(player)<0)throw new IllegalArgumentException("没有待处理的悔棋申请");r.undo=null;r.revision++;r.changed=System.currentTimeMillis();}
    static void apply(Room r){
        if(r.undo==null||!r.undo.pending.isEmpty())throw new IllegalArgumentException("需等待其他真人玩家同意");
        var restored=GameFactory.create(r.kind,r.capacity,r.seed);
        for(int i=0;i<r.undo.cut;i++){var event=r.history.get(i).getAsJsonObject();restored.apply(event.get("seat").getAsInt(),event.get("action").getAsString());}
        while(r.history.size()>r.undo.cut)r.history.remove(r.history.size()-1);
        r.board=restored;r.undo=null;r.phase=restored.finished()?Room.Phase.FINISHED:Room.Phase.PLAYING;r.completed=restored.finished();r.result=restored.finished()?restored.outcome():"";r.ready.clear();r.revision++;r.changed=System.currentTimeMillis();
    }
    static boolean rematchReady(Room r,UUID player){
        if(r.phase!=Room.Phase.FINISHED||r.busy||r.undo!=null||r.seat(player)<0)throw new IllegalArgumentException("结束本局后才能再来一局");
        r.ready.add(player);r.revision++;r.changed=System.currentTimeMillis();return r.seats.stream().allMatch(s->s.bot()||r.ready.contains(s.id()));
    }
    static void fresh(Room r,long seed){
        if(r.phase!=Room.Phase.FINISHED||r.seats.stream().anyMatch(s->!s.bot()&&!r.ready.contains(s.id())))throw new IllegalArgumentException("等待同桌玩家准备重开");
        while(!r.history.isEmpty())r.history.remove(r.history.size()-1);
        r.seed=seed;r.board=null;r.result="";r.completed=false;r.restoring=false;r.undo=null;r.ready.clear();r.phase=Room.Phase.LOBBY;r.revision++;r.changed=System.currentTimeMillis();
    }
}

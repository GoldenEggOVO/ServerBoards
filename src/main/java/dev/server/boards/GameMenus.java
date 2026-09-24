package dev.server.boards;

import com.google.gson.*;
import dev.server.boards.rules.GoGame;
import dev.server.boards.rules.YachtGame;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

final class GameMenus implements AutoCloseable {
    record Button(String id,String label,Runnable action){Button(String label,Runnable action){this("entry",label,action);}}
    record Session(UUID token,UUID world,long expires,List<Button> buttons){}
    private final ServerBoards plugin;private final Map<UUID,Session> sessions=new HashMap<>();
    private final BoardWindow window;
    GameMenus(ServerBoards p){this(p,null);}
    GameMenus(ServerBoards p,BoardWindow window){plugin=p;this.window=window;}
    void show(Player p,String title,String description,List<Button> buttons,Runnable back){
        show(p,title,description,buttons,back,"dialog");
    }
    void show(Player p,String title,String description,List<Button> buttons,Runnable back,String page){
        if(!plugin.allowed(p))return;
        List<Button> entries=new ArrayList<>(buttons);
        if(back!=null)entries.add(new Button("back","返回上一页",back));
        else if(plugin.mainMenuAvailable())entries.add(new Button("main","返回主菜单",()->{forget(p);Bukkit.dispatchCommand(p,"servermenu:servermenu main");}));
        entries.add(new Button("close","关闭菜单",()->forget(p)));
        UUID token=UUID.randomUUID();
        if(window!=null){
            var rendered=window.render(page,title,description,entries,token);
            entries=rendered.buttons();
            sessions.put(p.getUniqueId(),new Session(token,p.getWorld().getUID(),System.currentTimeMillis()+120000,List.copyOf(entries)));
            if(window.open(p,rendered.config(),page))return;
        }
        sessions.put(p.getUniqueId(),new Session(token,p.getWorld().getUID(),System.currentTimeMillis()+120000,List.copyOf(entries)));
        p.sendMessage("§6"+title+"\n§f"+description);
        for(int i=0;i<entries.size();i++)p.sendMessage(net.kyori.adventure.text.Component.text("["+entries.get(i).label()+"] ").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/boards click boards:"+token+" "+i)));
    }
    void handle(Player p,String action){
        String[] a=action.split(" ");if(a.length!=2||!a[0].startsWith("boards:"))return;
        try{UUID token=UUID.fromString(a[0].substring(7));int index=Integer.parseInt(a[1]);Session s=sessions.get(p.getUniqueId());
            if(s==null||!s.token().equals(token)||!s.world().equals(p.getWorld().getUID())||System.currentTimeMillis()>=s.expires()||index<0||index>=s.buttons().size())return;
            sessions.remove(p.getUniqueId());if(plugin.allowed(p))s.buttons().get(index).action().run();
        }catch(IllegalArgumentException ex){plugin.tell(p,ex.getMessage()==null?"页面已更新，请重新打开":ex.getMessage());}
    }
    void forget(Player p){sessions.remove(p.getUniqueId());}
    void main(Player p){
        List<Button> b=new ArrayList<>();Room current=plugin.room(p);
        if(current!=null)b.add(new Button("resume","继续当前对局",()->plugin.resume(p,current)));
        for(String kind:ServerBoards.NAMES.keySet())if(!Set.of("go9","go13").contains(kind))b.add(new Button(kind,ServerBoards.gameName(kind),()->games(p,kind)));
        show(p,"棋牌游戏","",b,null,"catalog");
    }
    void games(Player p,String kind){
        List<Button>b=new ArrayList<>();b.add(new Button("§a创建房间",()->sizes(p,kind)));
        plugin.rooms.values().stream().filter(r->r.kind.equals(kind)||kind.equals("go")&&Set.of("go9","go13").contains(r.kind)).limit(12).forEach(r->b.add(new Button(r.name()+"  "+r.seats.size()+"/"+r.capacity+" · "+phase(r),()->{if(r.seat(p.getUniqueId())>=0)plugin.resume(p,r);else if(r.phase==Room.Phase.LOBBY)plugin.join(p,r);else observe(p,r);})));
        show(p,ServerBoards.gameName(kind),"",b,()->main(p));
    }
    void sizes(Player p,String kind){if(kind.equals("go")){show(p,"选择围棋棋盘","9路适合短局，13路适中，19路为完整大棋盘；均采用面积计分、白贴7.5目。",List.of(new Button("9路 · 快速对弈",()->plugin.create(p,"go9",2)),new Button("13路 · 进阶对弈",()->plugin.create(p,"go13",2)),new Button("19路 · 完整棋盘",()->plugin.create(p,"go",2))),()->games(p,kind));return;}int[] sizes=switch(kind){case"uno"->new int[]{2,3,4,6,8,10};case"checkers"->new int[]{2,3,4,6};case"aeroplane","yacht"->new int[]{2,3,4};default->new int[]{ServerBoards.defaultCapacity(kind)};};
        if(sizes.length==1){plugin.create(p,kind,sizes[0]);return;}List<Button>b=new ArrayList<>();for(int size:sizes)b.add(new Button(size+"人桌",()->plugin.create(p,kind,size)));show(p,"选择人数","创建后可等好友加入，也可补齐陪练。",b,()->games(p,kind));}
    static String phase(Room r){return switch(r.phase){case LOBBY->"等候";case STARTING->"正在准备";case PLAYING->"对局中";case PAUSED->"已暂停，记录保留";case FINISHED->"已结束";case ABORTED->"已关闭";};}
    String status(Room r){StringBuilder s=new StringBuilder("§7"+phase(r)+"\n");for(int i=0;i<r.seats.size();i++){Room.Seat seat=r.seats.get(i);s.append(i+1).append("号 ").append(seat.name()).append(r.ready.contains(seat.id())?" ✓":"").append(r.turn()==i?" §e← 当前回合§7":"").append('\n');}
        if(r.board!=null)r.board.publicInfo().forEach((k,v)->{if(!Set.of("rules","rulesVariant").contains(k))s.append(v).append('\n');});
        if(r.undo!=null)s.append("§e正在等待悔棋确认，暂时停止计时和落子。\n");
        if(r.phase==Room.Phase.FINISHED||r.phase==Room.Phase.PAUSED)s.append(plugin.displayOutcome(r,r.result));return s.toString();}
    void room(Player p,Room r){
        if(!plugin.rooms.containsKey(r.id)){main(p);return;}int seat=r.seat(p.getUniqueId());if(seat<0){observe(p,r);return;}
        List<Button>b=new ArrayList<>();long revision=r.revision;
        if(r.phase==Room.Phase.LOBBY){b.add(new Button("ready",r.ready.contains(p.getUniqueId())?"取消准备":"准备",()->plugin.ready(p,r)));if(seat==0)b.add(new Button("bots","补齐陪练并开始",()->plugin.startWithBots(p,r)));}
        if(r.phase==Room.Phase.PLAYING){
            b.add(new Button("play","回到对局",()->{forget(p);plugin.enterArena(p,r);}));
            if(r.board instanceof GoGame go){for(String action:List.of("pass","accept","resume"))if(go.legalActions(seat).contains(action))b.add(new Button(actionLabel(r,action),()->plugin.action(p,r,revision,new JsonPrimitive(action))));}
        }
        if(r.undo!=null){if(r.undo.pending.contains(p.getUniqueId()))b.add(new Button("§a同意悔棋",()->plugin.approveUndo(p,r)));b.add(new Button(r.undo.requester.equals(p.getUniqueId())?"取消悔棋申请":"§c拒绝悔棋",()->plugin.rejectUndo(p,r)));}
        if(r.phase==Room.Phase.FINISHED)b.add(new Button("rematch",r.ready.contains(p.getUniqueId())?"已准备，等待同桌":"再来一局",()->plugin.rematch(p,r)));
        b.add(new Button("options","房间选项",()->roomOptions(p,r)));
        show(p,r.name(),roomSummary(r),b,()->games(p,r.kind),"room");
    }
    String roomSummary(Room r){
        StringBuilder text=new StringBuilder(phase(r)+" · "+r.seats.size()+"/"+r.capacity+" 人\n");
        for(int i=0;i<r.seats.size();i++){Room.Seat seat=r.seats.get(i);text.append(seat.name());
            if(r.phase==Room.Phase.LOBBY||r.phase==Room.Phase.FINISHED)text.append(r.ready.contains(seat.id())?" ✓ 已准备":" · 未准备");
            else if(r.turn()==i)text.append(" ← 当前回合");text.append('\n');}
        if(r.undo!=null)text.append("等待悔棋确认\n");
        if(r.phase==Room.Phase.FINISHED||r.phase==Room.Phase.PAUSED)text.append(plugin.displayOutcome(r,r.result));
        return text.toString().strip();
    }
    void roomOptions(Player p,Room r){
        if(!plugin.rooms.containsKey(r.id)||r.seat(p.getUniqueId())<0){main(p);return;}
        List<Button>b=new ArrayList<>();
        b.add(new Button("刷新房间",()->room(p,r)));
        if(r.undo==null&&r.board!=null&&!r.history.isEmpty()&&(r.phase==Room.Phase.PLAYING||r.phase==Room.Phase.FINISHED))b.add(new Button("申请悔棋",()->plugin.requestUndo(p,r)));
        b.add(new Button("离开房间",()->confirmLeave(p)));
        show(p,"房间选项","",b,()->room(p,r));
    }
    void yacht(Player p,Room r){
        if(!(r.board instanceof YachtGame g))return;int seat=r.seat(p.getUniqueId());long rev=r.revision;List<String> legal=g.legalActions(seat);List<Button>b=new ArrayList<>();
        if(legal.contains("roll"))b.add(new Button("roll","掷骰子 · 剩余 "+(3-g.rolls())+" 次",()->yachtAction(p,r,rev,"roll")));
        for(int i=0;i<5;i++){String a="hold:die"+i;if(legal.contains(a))b.add(new Button((g.held(i)?"§a✓ 保留":"□ 重掷")+" 第"+(i+1)+"颗 · "+g.dice()[i]+"点",()->yachtAction(p,r,rev,a)));}
        StringBuilder sheet=new StringBuilder("骰子：");for(int i=0;i<5;i++)sheet.append(g.dice()[i]).append(g.held(i)?"✓  ":"  ");sheet.append("\n");
        b.add(new Button("score",legal.stream().anyMatch(a->a.startsWith("score:"))?"选择计分项":"查看计分表",()->yachtScores(p,r)));
        show(p,"快艇骰子",sheet+"\n"+roomSummary(r),b,()->room(p,r),"yacht");
    }
    void yachtScores(Player p,Room r){
        if(!(r.board instanceof YachtGame g)||r.seat(p.getUniqueId())<0)return;
        long revision=r.revision;List<String> legal=g.legalActions(r.seat(p.getUniqueId()));
        List<Button> buttons=new ArrayList<>();StringBuilder sheet=new StringBuilder();
        for(int i=0;i<12;i++){
            String action="score:"+YachtGame.CATEGORIES.get(i);
            sheet.append(YachtGame.LABELS.get(i)).append("：");
            for(int seat=0;seat<r.capacity;seat++)sheet.append(seat+1).append("号 ").append(g.written(seat,i)<0?"—":g.written(seat,i)).append("  ");
            sheet.append('\n');
            if(legal.contains(action))buttons.add(new Button(YachtGame.LABELS.get(i)+" · "+YachtGame.score(i,g.dice())+" 分",()->yachtAction(p,r,revision,action)));
        }
        show(p,"计分表",sheet.toString(),buttons,()->yacht(p,r),"yacht");
    }
    void yachtAction(Player p,Room r,long revision,String action){plugin.action(p,r,revision,new JsonPrimitive(action));if(plugin.rooms.containsKey(r.id)&&r.phase==Room.Phase.PLAYING)yacht(p,r);}
    void observe(Player p,Room r){
        if(plugin.room(p)!=null){plugin.tell(p,"请先结束自己的对局再观战");return;}
        List<Button>b=new ArrayList<>();b.add(new Button("刷新公开牌局",()->observe(p,r)));
        if(r.board!=null)b.add(new Button("前往棋盘旁",()->{if(!p.getWorld().equals(plugin.arena.world))plugin.returns.putIfAbsent(p.getUniqueId(),p.getLocation());p.teleport(plugin.arena.seatLocation(r,0));}));
        show(p,r.name()+" · 观战",status(r)+"\n§8观战仅显示公开信息，不显示任何玩家手牌。",b,()->games(p,r.kind));
    }
    void boardSources(Player p,Room r,int page){
        int seat=r.seat(p.getUniqueId());if(r.board==null)return;
        List<String> legal=r.board.legalActions(seat);Map<String,List<String>> bySource=new LinkedHashMap<>();
        for(String action:legal){String[] split=action.split(":");String key=split.length>=2?split[1]:action;bySource.computeIfAbsent(key,k->new ArrayList<>()).add(action);}
        List<String> keys=new ArrayList<>(bySource.keySet());List<Button>b=new ArrayList<>();int from=Math.max(0,Math.min(page*12,keys.size()));
        for(String key:keys.subList(from,Math.min(from+12,keys.size())))b.add(new Button(labelCell(r,key)+" · "+bySource.get(key).size()+"种走法",()->boardChoices(p,r,bySource.get(key),0)));
        if(from>0)b.add(new Button("上一页",()->boardSources(p,r,page-1)));if(from+12<keys.size())b.add(new Button("下一页",()->boardSources(p,r,page+1)));
        show(p,"选择棋子",legal.isEmpty()?"现在没有可操作的棋子，请等待你的回合。":"选择棋子，再选择落点；也可以直接点击世界里的棋盘。",b,()->room(p,r));
    }
    String labelCell(Room r,String id){return r.board.cells().stream().filter(c->c.id().equals(id)).map(c->c.id()+" "+c.piece()).findFirst().orElse(id.equals("roll")?"掷骰子":id);}
    void boardChoices(Player p,Room r,List<String> choices,int page){
        long revision=r.revision;List<Button>b=new ArrayList<>();int from=Math.max(0,Math.min(page*12,choices.size()));
        for(String action:choices.subList(from,Math.min(from+12,choices.size())))b.add(new Button(actionLabel(r,action),()->plugin.action(p,r,revision,new JsonPrimitive(action))));
        if(from>0)b.add(new Button("上一页",()->{if(r.revision!=revision)boardSources(p,r,0);else boardChoices(p,r,choices,page-1);}));if(from+12<choices.size())b.add(new Button("下一页",()->{if(r.revision!=revision)boardSources(p,r,0);else boardChoices(p,r,choices,page+1);}));
        show(p,"选择走法",r.kind.equals("chess")?"升变有后、车、象、马四个选项。每次操作都会重新检查回合。":"选择本回合允许的操作，也可以回到桌边直接点击棋盘。",b,()->boardSources(p,r,0));
    }
    static String actionLabel(Room r,String action){String[] s=action.split(":");if(action.equals("roll"))return"掷骰子";if(action.equals("pass"))return"停一手 · 连续双方停手进入计分";if(action.equals("accept"))return"§a确认死子与计分";if(action.equals("resume"))return"§e对计分有异议 · 继续行棋";if(s.length==2)return(s[0].equals("dead")?"标记 / 取消死子 ":"落子 ")+s[1];if(s.length>=3)return s[1]+" → "+s[2]+(s.length==4?" · 升变"+switch(s[3]){case"q"->"后";case"r"->"车";case"b"->"象";case"n"->"马";default->s[3];}:"");return action;}



    void confirmLeave(Player p){Room r=plugin.room(p);if(r==null){main(p);return;}show(p,"离开房间","对局中离开会结束整桌免费局；只关闭菜单则保留座位。",List.of(new Button("§c确认离开",()->plugin.leave(p))),()->room(p,r));}
    void rules(Player p,String kind){String text=switch(kind){
        case"gomoku"->"15×15自由五子棋，先连成五子或更多获胜；不设黑方禁手。";
        case"xiangqi"->"中国象棋：将帅照面、马腿、象眼、象不过河、九宫及自将过滤。将死或困毙判负；重复和长将规则以规则面板标注为准。";
        case"chess"->"国际象棋：王车易位、吃过路兵、四种升变、将死与逼和，含重复与50回合和棋判定。";
        case"aeroplane"->"飞行棋：6点起飞，同色跳跃/飞越，叠机壁垒；连续第三个6使未完成飞机回营。以此服务器变体为准。";
        case"checkers"->"121孔六角星中国跳棋，支持2/3/4/6人；相邻步行或连续短跳，把全部棋子送入对面营地获胜。";
        case"draughts"->"8×8 英美式西洋跳棋：黑先，兵只向前走及吃子，王可双向短跳；有吃必吃，连吃必须用同一棋子，升王立即结束该回合。三次同局面或双方各40步无吃子、无兵移动自动和棋。";
        case"reversi"->"8×8 黑白棋：黑先，横竖斜线夹住对方棋子并全部翻面；无合法落点自动停手，双方都无法落子时，多子者获胜。";
        case"go","go9","go13"->"围棋有9/13/19路。面积计分、白贴7.5目，禁自杀和全局同形。双方连续停手后进入死子协商：点击棋块切换标记，双方确认后结算；任何一方都可恢复行棋解决争议。陪练只会基础落子，不具备职业判断。";
        case"yacht"->"2–4人快艇骰子，每人12轮，每回合最多掷3次，可保留任意骰子；每种计分项只填一次，可主动填零。小顺15、大顺30、快艇50，上半区达到63分额外加35分；没有额外快艇奖励。点击桌上骰子可保留，操作菜单填写计分表。";
        case"connectfour"->"7列6行竖直四子棋；点击列后重力落子，横、竖、双斜向四连获胜；满盘无赢家则和局。";
        default->"选择棋类后查看规则。";
    };Room r=plugin.room(p);if(r!=null&&r.board!=null)text+="\n\n"+r.board.publicInfo().getOrDefault("rules","")+"\n"+r.board.publicInfo().getOrDefault("rulesVariant","");show(p,ServerBoards.NAMES.getOrDefault(kind,kind)+"规则",text+"\n\n§7回合超时由基础陪练代走；离线/离开世界保留120秒。关闭菜单不退房。",List.of(),()->{if(r!=null)room(p,r);else main(p);});}
    static String roomState(String state){return switch(state){case "WAITING","LOBBY"->"等候";case "PLAYING","RUNNING"->"对局中";case "STARTING"->"正在准备";case "FINISHED","ENDED"->"已结束";case "PAUSED"->"已暂停";case "ABORTED","CLOSED"->"已关闭";default->state;};}
    static String roomLabel(String game,String id,int occupied,int capacity,String state){return game+" · "+id+"  "+occupied+"/"+capacity+" · "+roomState(state);}


    @Override public void close(){sessions.clear();}
}

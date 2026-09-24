package dev.server.boards;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Editable window layout with room actions bound only at open time. */
final class GameMenuLayouts {
    static final List<String> PAGES=List.of("dialog","catalog","room","yacht");
    private final Path directory;
    record Rendered(YamlConfiguration config,List<GameMenus.Button> buttons){}
    GameMenuLayouts(ServerBoards plugin){
        directory=plugin.getDataFolder().toPath().resolve("menus");
        try {
            Files.createDirectories(directory);
            for(String page:PAGES){
                Path file=directory.resolve(page+".yml");
                if(!Files.exists(file))try(InputStream input=plugin.getResource("menus/"+page+".yml")){
                    if(input==null)throw new IOException("Missing "+page);
                    Files.copy(input,file);
                }
            }
        } catch(IOException ex){throw new IllegalStateException("不能释放棋牌菜单模板",ex);}
    }
    Rendered load(String page,String title,String description,List<GameMenus.Button> buttons,UUID token){
        try{YamlConfiguration config=new YamlConfiguration();try(Reader in=Files.newBufferedReader(directory.resolve(page+".yml"),StandardCharsets.UTF_8)){config.load(in);}return render(config,title,description,buttons,token);}
        catch(Exception ex){throw new IllegalArgumentException("菜单模板读取失败，请检查 ServerBoards/menus/"+page+".yml",ex);}
    }
    static Rendered render(YamlConfiguration config,String title,String description,List<GameMenus.Button> supplied,UUID token){
        var section=config.getConfigurationSection("Bottom.buttons");List<String> order=section==null?List.of():new ArrayList<>(section.getKeys(false));
        Map<String,Map<String,Object>> styles=new HashMap<>();if(section!=null)for(String id:order){var node=section.getConfigurationSection(id);if(node!=null)styles.put(id,new LinkedHashMap<>(node.getValues(false)));}
        var exit=config.getConfigurationSection("Bottom.exit");if(exit!=null)styles.put("close",new LinkedHashMap<>(exit.getValues(false)));
        List<GameMenus.Button> entries=new ArrayList<>(supplied);entries.sort(Comparator.comparingInt(b->b.id().equals("resume")?-1:b.id().equals("close")?order.size()+3:b.id().equals("main")?order.size()+2:b.id().equals("back")?order.size()+1:order.contains(b.id())?order.indexOf(b.id()):order.size()));
        config.set("Title",config.getString("Title","@title@").replace("@title@",title));
        for(String node:List.of("description","content")){String path="Body."+node+".text";if(config.contains(path)){if(description.isBlank())config.set("Body."+node,null);else config.set(path,config.getString(path).replace("@description@",description));}}
        config.set("Settings.can_escape",true);config.set("Settings.after_action","CLOSE");config.set("Settings.lifetime","120s");config.set("Bottom.type","multi");config.set("Bottom.buttons",null);config.set("Bottom.exit",null);config.set("Events",null);config.set("Inputs",null);
        for(int i=0;i<entries.size();i++){var b=entries.get(i);var style=styles.getOrDefault(b.id(),styles.getOrDefault("entry",Map.of()));String path=b.id().equals("close")?"Bottom.exit":"Bottom.buttons.slot"+i;
            String label=b.id().equals("close")||b.id().equals("back")?b.label():b.label().replaceAll("(?i)[§&][0-9A-FK-OR]","");
            config.set(path+".text",(b.id().equals("resume")&&!styles.containsKey("resume")?"&e@label@":String.valueOf(style.getOrDefault("text","&f@label@"))).replace("@label@",label));config.set(path+".width",style.getOrDefault("width",174));if(style.containsKey("tooltip"))config.set(path+".tooltip",style.get("tooltip"));config.set(path+".actions",List.of("boards:"+token+" "+i));}
        return new Rendered(config,List.copyOf(entries));
    }
}

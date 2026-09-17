package dev.server.boards;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** Twenty static native maps shared by every table, with stable IDs across restarts. */
final class TableMaps {
    private final ServerBoards plugin;
    private final File file;
    private final YamlConfiguration index;
    private final Map<String,List<ItemStack>> cache=new HashMap<>();
    TableMaps(ServerBoards plugin){this(plugin,"table-map-ids.yml");}
    TableMaps(ServerBoards plugin,String filename){this.plugin=plugin;file=new File(plugin.getDataFolder(),filename);index=YamlConfiguration.loadConfiguration(file);}
    List<ItemStack> get(World world,TableGeometry geometry){
        return getImage(world,geometry.kind,()->TableArt.draw(geometry));
    }
    List<ItemStack> getImage(World world,String kind,java.util.function.Supplier<BufferedImage> art){
        String key="v1."+world.getUID()+"."+kind;
        return cache.computeIfAbsent(key,ignored->{
            BufferedImage all=art.get();List<ItemStack> items=new ArrayList<>();
            int columns=all.getWidth()/128,rows=all.getHeight()/128;
            for(int z=0;z<rows;z++)for(int x=0;x<columns;x++){
                String path=key+"."+(z*columns+x);int id=index.getInt(path,-1);MapView map=id>=0?Bukkit.getMap(id):null;
                if(map==null){map=Bukkit.createMap(world);index.set(path,map.getId());}
                map.setTrackingPosition(false);map.setUnlimitedTracking(false);map.setLocked(true);
                map.getRenderers().forEach(map::removeRenderer);
                BufferedImage tile=all.getSubimage(x*128,z*128,128,128);
                map.addRenderer(new MapRenderer(false){boolean painted;
                    @Override public void render(MapView view,MapCanvas canvas,Player player){if(!painted){canvas.drawImage(0,0,tile);painted=true;}}
                });
                ItemStack item=new ItemStack(Material.FILLED_MAP);MapMeta meta=(MapMeta)item.getItemMeta();meta.setMapView(map);item.setItemMeta(meta);items.add(item);
            }
            try{index.save(file);}catch(IOException ex){throw new IllegalStateException("不能保存棋盘地图编号",ex);}
            return List.copyOf(items);
        });
    }
}

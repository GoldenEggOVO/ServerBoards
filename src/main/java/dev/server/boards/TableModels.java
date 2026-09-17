package dev.server.boards;

import dev.server.boards.rules.Cell;
import org.bukkit.Material;
import java.util.*;

/** Small original voxel meshes: dimensions are in units of one cell spacing. */
final class TableModels {
    record Part(double x,double y,double z,double w,double h,double d,Material material){}
    static final Material[] COLORS={Material.RED_CONCRETE,Material.LIGHT_BLUE_CONCRETE,Material.GREEN_CONCRETE,Material.YELLOW_CONCRETE,Material.PURPLE_CONCRETE,Material.PINK_CONCRETE};
    static List<Part> piece(String kind,Cell cell,Map<String,String> info){
        List<Part> p=new ArrayList<>();int owner=cell.owner();
        Material color=COLORS[Math.floorMod(GameWorld.actualColor(info,owner),6)];
        switch(kind){
            case "gomoku", "go", "go9", "go13", "reversi" -> {Material stone=owner==0?Material.BLACK_CONCRETE:Material.WHITE_CONCRETE;disc(p,stone,.76,.13);if(cell.piece().contains("×")){box(p,0,.16,0,.65,.035,.10,Material.RED_CONCRETE);box(p,0,.16,0,.10,.035,.65,Material.RED_CONCRETE);}}
            case "draughts" -> {Material stone=owner==0?Material.POLISHED_BLACKSTONE:Material.SMOOTH_QUARTZ;disc(p,stone,.76,.19);if(cell.piece().equals("王")){disc(p,Material.GOLD_BLOCK,.54,.30);box(p,0,.31,0,.18,.07,.18,stone);}}
            case "yacht" -> {Material body=cell.piece().endsWith("✓")?Material.LIGHT_BLUE_CONCRETE:Material.SMOOTH_QUARTZ;box(p,0,0,0,1.14,1.14,1.14,body);int face=Character.digit(cell.piece().charAt(0),10);int[][] dots={{0,0},{-1,-1},{1,1},{-1,1},{1,-1},{-1,0},{1,0}};for(int i:TableView.pipIndices(face))box(p,dots[i][0]*.30,1.145,dots[i][1]*.30,.14,.025,.14,Material.BLACK_CONCRETE);}
            case "xiangqi" -> {disc(p,Material.STRIPPED_BIRCH_WOOD,.78,.19);}
            case "checkers" -> {disc(p,color,.68,.18);box(p,0,.16,0,.44,.22,.44,color);box(p,-.07,.35,-.07,.18,.04,.18,Material.WHITE_CONCRETE);}
            case "aeroplane" -> {
                box(p,0,.06,0,.17,.15,.79,color);box(p,0,.10,.04,.74,.10,.19,color);
                box(p,0,.12,.28,.40,.08,.12,color);box(p,0,.20,.26,.07,.16,.15,color);
                box(p,0,.215,-.19,.13,.06,.18,Material.LIGHT_BLUE_STAINED_GLASS);
            }
            case "chess" -> {
                Material body=owner==0?Material.SMOOTH_QUARTZ:Material.POLISHED_BLACKSTONE;
                Material trim=owner==0?Material.GOLD_BLOCK:Material.COPPER_BLOCK;
                disc(p,body,.76,.12);box(p,0,.13,0,.49,.10,.49,body);box(p,0,.23,0,.28,.28,.28,body);
                switch(cell.piece()){
                    case "兵" -> {box(p,0,.49,0,.39,.24,.39,body);}
                    case "车" -> {box(p,0,.50,0,.57,.14,.57,body);for(double x:new double[]{-.20,.20})for(double z:new double[]{-.20,.20})box(p,x,.64,z,.16,.16,.16,body);}
                    case "马" -> {box(p,0,.49,.04,.28,.33,.30,body);box(p,0,.70,-.15,.28,.22,.45,body);box(p,0,.92,.02,.25,.12,.11,body);}
                    case "象" -> {box(p,0,.51,0,.46,.08,.46,trim);box(p,0,.59,0,.33,.25,.33,body);box(p,0,.84,0,.16,.13,.16,body);}
                    case "后" -> {box(p,0,.51,0,.42,.25,.42,body);box(p,0,.76,0,.55,.08,.55,trim);for(double x:new double[]{-.19,.19})box(p,x,.84,0,.13,.15,.34,body);}
                    case "王" -> {box(p,0,.51,0,.44,.28,.44,body);box(p,0,.79,0,.15,.32,.15,trim);box(p,0,.93,0,.40,.10,.15,trim);}
                    default -> throw new IllegalArgumentException("Unknown chess piece "+cell.piece());
                }
            }
            default -> throw new IllegalArgumentException(kind);
        }
        return List.copyOf(p);
    }
    static void disc(List<Part> parts,Material material,double width,double height){
        // Three intersecting boxes form a rounded stepped octagon at constant low entity cost.
        box(parts,0,0,0,width*.68,height,width,material);box(parts,0,.001,0,width,height*.99,width*.68,material);
        box(parts,0,height*.80,0,width*.64,height*.24,width*.64,material);
    }
    static void box(List<Part> parts,double x,double y,double z,double w,double h,double d,Material mat){parts.add(new Part(x,y,z,w,h,d,mat));}
}

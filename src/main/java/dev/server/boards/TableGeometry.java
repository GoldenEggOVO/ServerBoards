package dev.server.boards;

import dev.server.boards.rules.Cell;
import java.util.*;

/** Shared coordinates for the map, models and hit testing; never clamps a miss to an edge cell. */
final class TableGeometry {
    // Up-facing item frames are snapped to integer block height + 1/32 by Purpur.
    static final double SURFACE=1.03125, REACH=5.5;
    final String kind;
    final GameWorld.Layout layout;
    final List<Cell> cells;
    final Map<String,Cell> byId;
    final double spacing;
    TableGeometry(String kind,List<Cell> cells) {
        this.kind=kind;this.cells=List.copyOf(cells);layout=GameWorld.layout(kind,cells);
        byId=new LinkedHashMap<>();cells.forEach(c->byId.put(c.id(),c));
        spacing=kind.equals("checkers")?layout.xUnit()*2:Math.min(layout.xUnit(),layout.zUnit());
    }
    double x(Cell c){return layout.x(c);}
    double z(Cell c){return layout.z(c);}
    boolean squares(){return Set.of("chess","draughts","reversi").contains(kind);}
    double radius(){return spacing*(squares()?.499:kind.equals("yacht")?.65:.46);}
    String hit(double x,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(z))return null;
        if(Math.abs(x-1.30)<.20&&Math.abs(z)<.22&&(kind.equals("aeroplane")||kind.equals("yacht")))return "@roll";
        if(Math.abs(x)<.45&&Math.abs(z-1.27)<.15)return "@menu";
        Cell best=null;double distance=Double.MAX_VALUE;
        for(Cell c:cells){double dx=x-x(c),dz=z-z(c);double d=squares()?Math.max(Math.abs(dx),Math.abs(dz)):Math.hypot(dx,dz);
            if(d<distance){distance=d;best=c;}}
        return best!=null&&distance<=radius()?best.id():null;
    }
    static double intersection(double eyeY,double dy,double planeY) {
        if(!Double.isFinite(eyeY)||!Double.isFinite(dy)||!Double.isFinite(planeY)||dy>=-1e-5)return -1;
        double t=(planeY-eyeY)/dy;return t>=0&&t<=REACH?t:-1;
    }
    int px(Cell c){return pixel(x(c));}
    int pz(Cell c){return pixel(z(c));}
    static int pixel(double coordinate){return (int)Math.round((coordinate+1)*128);}
}

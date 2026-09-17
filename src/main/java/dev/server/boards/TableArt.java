package dev.server.boards;

import dev.server.boards.rules.Cell;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** Original server artwork. No artwork or decompiled source from BoardGames is bundled. */
final class TableArt {
    static final int[] COLORS={0xc64a45,0x4598c1,0x4eab7b,0xe2b64b,0x9b72c2,0xe88ca4};
    static BufferedImage draw(TableGeometry t) {
        BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x674936));g.fillRect(0,0,256,256);
        g.setColor(new Color(0xc7a06a));g.drawRect(3,3,249,249);g.drawRect(6,6,243,243);
        g.setColor(new Color(t.kind.equals("checkers")?0xe8dfbf:0xe5c48b));g.fillRect(10,10,236,236);
        // Fine wood grain is static pixels, not extra entities or per-tick packets.
        g.setColor(new Color(128,103,56,18));for(int y=13;y<244;y+=5)g.drawLine(11,y,244,y);
        g.setStroke(new BasicStroke(1.25f));
        switch(t.kind){
            case "chess", "draughts" -> chess(g,t);
            case "reversi" -> reversi(g,t);
            case "gomoku", "go", "go9", "go13" -> grid(g,t,false);
            case "yacht" -> yacht(g,t);
            case "xiangqi" -> grid(g,t,true);
            case "checkers" -> checkers(g,t);
            case "aeroplane" -> flight(g,t);
            default -> throw new IllegalArgumentException(t.kind);
        }
        g.dispose();return image;
    }
    static void grid(Graphics2D g,TableGeometry t,boolean river) {
        int maxX=t.cells.stream().mapToInt(Cell::x).max().orElse(14),maxY=t.cells.stream().mapToInt(Cell::y).max().orElse(14);
        int left=t.px(t.cells.getFirst()),top=t.pz(t.cells.getFirst());
        int right=TableGeometry.pixel((maxX-t.layout.midX())*t.layout.xUnit());
        int bottom=TableGeometry.pixel((maxY-t.layout.midY())*t.layout.zUnit());
        g.setColor(new Color(0x63492d));
        for(int y=0;y<=maxY;y++){int py=TableGeometry.pixel((y-t.layout.midY())*t.layout.zUnit());g.drawLine(left,py,right,py);}
        for(int x=0;x<=maxX;x++){
            int px=TableGeometry.pixel((x-t.layout.midX())*t.layout.xUnit());
            if(river&&x>0&&x<8){g.drawLine(px,top,px,TableGeometry.pixel((4-t.layout.midY())*t.layout.zUnit()));g.drawLine(px,TableGeometry.pixel((5-t.layout.midY())*t.layout.zUnit()),px,bottom);}
            else g.drawLine(px,top,px,bottom);
        }
        if(river){
            for(int row:new int[]{0,7})for(int diagonal=0;diagonal<2;diagonal++){
                int x1=diagonal==0?3:5,x2=diagonal==0?5:3;
                g.drawLine(TableGeometry.pixel((x1-t.layout.midX())*t.layout.xUnit()),TableGeometry.pixel((row-t.layout.midY())*t.layout.zUnit()),TableGeometry.pixel((x2-t.layout.midX())*t.layout.xUnit()),TableGeometry.pixel((row+2-t.layout.midY())*t.layout.zUnit()));
            }
            // River lettering uses Minecraft's font on a TextDisplay, portable across server fonts.
        }else{
            int edge=maxX==8?2:3,mid=maxX/2;for(int x:new int[]{edge,mid,maxX-edge})for(int y:new int[]{edge,mid,maxY-edge})dot(g,TableGeometry.pixel((x-mid)*t.layout.xUnit()),TableGeometry.pixel((y-mid)*t.layout.zUnit()),maxX>14?1.7:2.5,0x493521);
        }
    }
    static void reversi(Graphics2D g,TableGeometry t){g.setColor(new Color(0x27664d));g.fillRect(11,11,234,234);g.setColor(new Color(0x173f35));double h=t.spacing*64;for(Cell c:t.cells)g.drawRect((int)Math.round(t.px(c)-h),(int)Math.round(t.pz(c)-h),(int)Math.round(h*2),(int)Math.round(h*2));for(int x:new int[]{2,6})for(int y:new int[]{2,6})dot(g,TableGeometry.pixel((x-4)*t.spacing),TableGeometry.pixel((y-4)*t.spacing),2,0xc3b67e);}
    static void yacht(Graphics2D g,TableGeometry t){g.setColor(new Color(0x214f47));g.fillRect(11,11,234,234);g.setColor(new Color(0xc9ac72));g.drawRoundRect(17,17,221,221,12,12);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,14));g.drawString("YACHT DICE",79,52);for(Cell c:t.cells){int x=t.px(c),z=t.pz(c);g.drawRoundRect(x-19,z-19,38,38,5,5);g.drawString(""+(c.x()/2+1),x-4,z+42);}g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,10));g.drawString("5 DICE   /   3 ROLLS   /   12 ROUNDS",41,212);}
    static void chess(Graphics2D g,TableGeometry t) {
        double h=t.spacing*128/2;
        for(Cell c:t.cells){g.setColor(new Color(((c.x()+c.y())&1)==0?0x6e8778:0xf0dec0));
            int x=(int)Math.round(t.px(c)-h),z=(int)Math.round(t.pz(c)-h),w=(int)Math.ceil(h*2)+1;g.fillRect(x,z,w,w);}
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,8));
        for(Cell c:t.cells){g.setColor(new Color(0x513d2c));if(c.y()==0)g.drawString(String.valueOf((char)('A'+c.x())),t.px(c)-3,10);if(c.x()==0)g.drawString(""+(c.y()+1),3,t.pz(c)+3);}
    }
    static void checkers(Graphics2D g,TableGeometry t) {
        double edge=t.spacing*128;
        g.setColor(new Color(0xc2b18d));g.setStroke(new BasicStroke(.8f));
        for(int i=0;i<t.cells.size();i++)for(int j=i+1;j<t.cells.size();j++){
            Cell a=t.cells.get(i),b=t.cells.get(j);if(Math.hypot(t.px(a)-t.px(b),t.pz(a)-t.pz(b))<edge*1.1)g.drawLine(t.px(a),t.pz(a),t.px(b),t.pz(b));
        }
        for(Cell c:t.cells){int color=camp(c);
            dot(g,t.px(c),t.pz(c),edge*.35,color<0?0xb9aa88:COLORS[color]);dot(g,t.px(c),t.pz(c),edge*.20,0x645d4d);}
    }
    static int camp(Cell c){if(c.y()>=13)return 0;if(c.y()<=3)return 3;
        if(c.y()>=9&&c.x()<=c.y()-6)return 1;
        if(c.y()<=7&&c.x()<=10-c.y())return 2;
        if(c.y()>=9&&c.x()>=30-c.y())return 5;
        if(c.y()<=7&&c.x()>=14+c.y())return 4;
        return -1;
    }
    static void flight(Graphics2D g,TableGeometry t) {
        double s=t.spacing*128;
        for(int color=0;color<4;color++){
            final int c=color;List<Cell> base=t.cells.stream().filter(p->p.id().startsWith("ba"+c)).toList();
            int minX=base.stream().mapToInt(t::px).min().orElse(0),minY=base.stream().mapToInt(t::pz).min().orElse(0);
            g.setColor(new Color(COLORS[color]));g.fillRoundRect((int)(minX-s*.8),(int)(minY-s*.8),(int)(s*3.6),(int)(s*3.6),12,12);
        }
        for(Cell c:t.cells){int color=GameWorld.routeColor(c.id());dot(g,t.px(c),t.pz(c),s*.47,color<0?0xd3c29c:COLORS[color]);
            dot(g,t.px(c),t.pz(c),s*.32,0xf4e9cc);
            if(c.id().startsWith("go"))dot(g,t.px(c),t.pz(c),s*.18,COLORS[color]);
            if(c.id().startsWith("sk")){g.setColor(new Color(COLORS[color]));g.fillRect(t.px(c)-1,t.pz(c)-1,3,3);}
        }
    }
    static void dot(Graphics2D g,int x,int y,double radius,int color){g.setColor(new Color(color));g.fillOval((int)Math.round(x-radius),(int)Math.round(y-radius),(int)Math.round(radius*2),(int)Math.round(radius*2));}
}

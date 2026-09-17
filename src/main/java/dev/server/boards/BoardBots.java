package dev.server.boards;

import dev.server.boards.rules.*;
import java.util.*;

/** Small bounded casual heuristics; never simulate on the live rules engine. */
final class BoardBots {
    static String choose(BoardGame board,int seat,Random random){
        List<String> legal=board.legalActions(seat);if(legal.isEmpty())return null;
        if(board instanceof YachtGame yacht){
            if(yacht.rolls()==0)return "roll";
            if(yacht.rolls()<3){int[] dice=yacht.dice();int[] counts=new int[7];for(int d:dice)counts[d]++;int mode=1;for(int d=2;d<=6;d++)if(counts[d]>=counts[mode])mode=d;
                for(int i=0;i<5;i++)if((dice[i]==mode)!=yacht.held(i))return "hold:die"+i;
                if(legal.contains("roll"))return "roll";
            }
            return legal.stream().filter(a->a.startsWith("score:")).max(Comparator.comparingInt(a->YachtGame.score(YachtGame.CATEGORIES.indexOf(a.substring(6)),yacht.dice()))).orElseThrow();
        }
        if(board instanceof GoGame go){
            if(go.scoring())return legal.contains("accept")?"accept":"resume";
            if(legal.size()==1||go.cells().stream().filter(c->c.owner()>=0).count()>go.size()*go.size()*.78)return "pass";
            List<String> placements=legal.stream().filter(a->a.startsWith("place:")).toList();return placements.get(random.nextInt(placements.size()));
        }
        if(board instanceof ReversiGame){List<String> corners=legal.stream().filter(a->Set.of("place:0,0","place:7,0","place:0,7","place:7,7").contains(a)).toList();if(!corners.isEmpty())return corners.getFirst();}
        return legal.get(random.nextInt(legal.size()));
    }
}

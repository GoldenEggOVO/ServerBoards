package dev.server.boards;

import org.bukkit.Location;

/** Even-width map mosaics meet at a block corner; odd-width mosaics at a block centre. */
final class TablePlacement {
    static Location snap(Location source, int width) {
        double offset = width % 2 == 0 ? 0.0 : 0.5;
        return new Location(source.getWorld(), Math.floor(source.getX() - offset + .5) + offset,
                Math.floor(source.getY()), Math.floor(source.getZ() - offset + .5) + offset);
    }
}

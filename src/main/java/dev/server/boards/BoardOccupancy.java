package dev.server.boards;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Board-owned reservations, including seats restored for offline players. */
final class BoardOccupancy {
    private final Map<UUID, String> seats = new HashMap<>();

    synchronized boolean reserve(UUID player, String kind) {
        return seats.putIfAbsent(player, kind) == null;
    }

    synchronized boolean restoreReservation(UUID player, String kind) {
        return reserve(player, kind);
    }

    synchronized void release(UUID player, String kind) {
        seats.remove(player, kind);
    }

    synchronized void close() {
        seats.clear();
    }
}

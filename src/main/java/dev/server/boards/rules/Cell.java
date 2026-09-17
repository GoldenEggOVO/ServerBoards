package dev.server.boards.rules;

/** Immutable public cell. Owner -1 denotes an empty cell; seats are zero based. */
public record Cell(String id, int x, int y, String piece, int owner) { }

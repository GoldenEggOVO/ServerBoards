// Upstream MIT: kan01234/aeroplanes-chess. See META-INF/licenses.
package dev.server.boards.rules.upstream.aeroplane;

public enum CellPrefix {

	BASE("ba"), TAKEOFF("to"), SKY("sk"), LANDING("ld"), GOAL("go");

	private String prefix;

	private CellPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getPrefix() {
		return prefix;
	}

}

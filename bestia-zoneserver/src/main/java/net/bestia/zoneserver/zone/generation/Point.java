package net.bestia.zoneserver.zone.generation;

/**
 * Immutable size object.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Point {

	private final int x;
	private final int y;

	public Point(int x, int y) {

		this.x = x;
		this.y = y;
	}

	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}

	@Override
	public String toString() {
		return String.format("Point[x: %d, y: %d]", x, y);
	}
}

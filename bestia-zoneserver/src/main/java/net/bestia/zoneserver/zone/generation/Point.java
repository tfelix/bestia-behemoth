package net.bestia.zoneserver.zone.generation;

/**
 * Immutable size object.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Point {

	private final long x;
	private final long y;

	public Point(long x, long y) {

		this.x = x;
		this.y = y;
	}

	public long getX() {
		return x;
	}
	
	public long getY() {
		return y;
	}

	@Override
	public String toString() {
		return String.format("Point[x: %d, y: %d]", x, y);
	}
}

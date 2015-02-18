package net.bestia.core.game.zone;

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public class Point {

	public final int x;
	public final int y;

	public Point(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public String toString() {
		return String.format("Point[x: %d, y: %d]", x, y);
	}

	@Override
	public int hashCode() {
		return 31 * x + y;
	}

	public boolean equals(Object o) {
		if (o == null || !(o instanceof Point)) {
			return false;
		}

		Point p = (Point) o;
		return x == p.x && y == p.y;
	}

}

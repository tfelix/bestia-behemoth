package net.bestia.zoneserver.zone;

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public class Vector2 {

	public final int x;
	public final int y;

	public Vector2(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public String toString() {
		return String.format("Vec2[x: %d, y: %d]", x, y);
	}

	@Override
	public int hashCode() {
		return 31 * x + y;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof Vector2)) {
			return false;
		}

		Vector2 p = (Vector2) o;
		return x == p.x && y == p.y;
	}

}

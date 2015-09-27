package net.bestia.zoneserver.zone.shape;

import java.util.Objects;

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public class Vector2 implements CollisionShape {

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
		return Objects.hash(x, y);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof Vector2)) {
			return false;
		}

		Vector2 p = (Vector2) o;
		return x == p.x && y == p.y;
	}

	@Override
	public boolean collide(Vector2 s) {
		return CollisionHelper.collide(this, s);
	}

	@Override
	public boolean collide(Circle s) {
		return CollisionHelper.collide(s, this);
	}

	@Override
	public boolean collide(Rect s) {
		return CollisionHelper.collide(this, s);
	}

	@Override
	public Rect getBoundingBox() {
		return new Rect(x, y, 0, 0);
	}

}

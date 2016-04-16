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
	
	private final int anchorX;
	private final int anchorY;


	public Vector2(int x, int y) {
		this.x = x;
		this.y = y;
		
		this.anchorX = x;
		this.anchorY = y;
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

		// No need to check for anchor since it should be the same as x and y.
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

	@Override
	public boolean collide(CollisionShape s) {
		return s.collide(this);
	}

	@Override
	public Vector2 getAnchor() {
		return new Vector2(anchorX, anchorY);
	}

	@Override
	public CollisionShape moveByAnchor(int x, int y) {
		return new Vector2(x, y);
	}

}

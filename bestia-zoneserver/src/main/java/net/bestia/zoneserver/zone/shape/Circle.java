package net.bestia.zoneserver.zone.shape;

import com.google.common.base.Objects;

public class Circle implements CollisionShape {

	private final Vector2 center;
	private Vector2 anchor;
	private final int radius;

	public Circle(int x, int y, int radius) {

		if (radius < 0) {
			throw new IllegalArgumentException("Radius can not be negative.");
		}

		this.center = new Vector2(x, y);
		this.radius = radius;
		this.anchor = this.center;
	}

	public Circle(int x, int y, int radius, float anchorX, float anchorY) {
		if (radius < 0) {
			throw new IllegalArgumentException("Radius can not be negative.");
		}
		checkAnchor(anchorX, anchorY);

		this.center = new Vector2(x, y);
		this.radius = radius;
		this.anchor = new Vector2(x - (int) (2 * radius * anchorX), y - (int) (2 * radius * anchorY));
	}

	private void checkAnchor(float x, float y) {
		if (x < 0f || x > 1.0f) {
			throw new IllegalArgumentException("X must be betweeen 0.0 and 1.0");
		}
		if (y < 0f || y > 1.0f) {
			throw new IllegalArgumentException("Y must be betweeen 0.0 and 1.0");
		}
	}

	public int getRadius() {
		return radius;
	}

	public Vector2 getCenter() {
		return center;
	}

	@Override
	public boolean collide(Vector2 s) {
		return CollisionHelper.collide(this, s);
	}

	@Override
	public boolean collide(Circle s) {
		return CollisionHelper.collide(this, s);
	}

	@Override
	public boolean collide(Rect s) {
		return CollisionHelper.collide(this, s);
	}

	@Override
	public Rect getBoundingBox() {
		final int leftX = center.x - radius;
		final int rightX = center.x + radius;
		final int topY = center.y - radius;
		final int bottomY = center.y + radius;
		return new Rect(leftX, topY, rightX - leftX, bottomY - topY);
	}

	@Override
	public String toString() {
		return String.format("Circle[x: %d, y: %d, r: %d]", center.x, center.y, radius);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(center, radius, anchor);
	}

	@Override
	public boolean collide(CollisionShape s) {
		return s.collide(this);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Circle other = (Circle) obj;
		if (center == null) {
			if (other.center != null)
				return false;
		} else if (!center.equals(other.center))
			return false;
		if (radius != other.radius)
			return false;
		if (anchor.equals(other.anchor)) {
			return false;
		}
		return true;
	}

	@Override
	public Vector2 getAnchor() {
		return anchor;
	}
}

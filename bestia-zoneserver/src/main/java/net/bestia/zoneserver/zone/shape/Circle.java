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

	public Circle(int x, int y, int radius, int anchorX, int anchorY) {
		if (radius < 0) {
			throw new IllegalArgumentException("Radius can not be negative.");
		}
		this.center = new Vector2(x, y);
		this.radius = radius;
		
		checkAnchor(anchorX, anchorY);

		this.anchor = new Vector2(anchorX, anchorY);
	}

	private void checkAnchor(int x, int y) {
		final int dX = center.x - x;
		final int dY = center.y - y;
		if(Math.sqrt(dX * dX + dY * dY) > radius + 1) {
			throw new IllegalArgumentException("Anchor must be inside the circle.");
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
		if (!anchor.equals(other.anchor)) {
			return false;
		}
		return true;
	}

	@Override
	public Vector2 getAnchor() {
		return anchor;
	}

	@Override
	public CollisionShape moveByAnchor(int x, int y) {
		final int dX = x - getAnchor().x;
		final int dY = y - getAnchor().y;

		final int cX = center.x + dX;
		final int cY = center.y + dY;

		final Circle c = new Circle(cX, cY, radius, dX, dY);
		return c;
	}
}

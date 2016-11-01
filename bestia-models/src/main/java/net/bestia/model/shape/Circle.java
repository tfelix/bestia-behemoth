package net.bestia.model.shape;

import com.google.common.base.Objects;

public final class Circle implements Collision {

	private final Point center;
	private Point anchor;
	private final int radius;

	public Circle(long x, long y, int radius) {

		if (radius < 0) {
			throw new IllegalArgumentException("Radius can not be negative.");
		}

		this.center = new Point(x, y);
		this.radius = radius;
		this.anchor = this.center;
	}

	public Circle(long x, long y, int radius, long anchorX, long anchorY) {
		if (radius < 0) {
			throw new IllegalArgumentException("Radius can not be negative.");
		}
		this.center = new Point(x, y);
		this.radius = radius;

		checkAnchor(anchorX, anchorY);

		this.anchor = new Point(anchorX, anchorY);
	}

	private void checkAnchor(long x, long y) {
		final long dX = center.getX() - x;
		final long dY = center.getY() - y;
		if (Math.sqrt(dX * dX + dY * dY) > radius + 1) {
			throw new IllegalArgumentException("Anchor must be inside the circle.");
		}
	}

	public int getRadius() {
		return radius;
	}

	public Point getCenter() {
		return center;
	}

	@Override
	public boolean collide(Point s) {
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
		final long leftX = center.getX() - radius;
		final long rightX = center.getY() + radius;
		final long topY = center.getX() - radius;
		final long bottomY = center.getY() + radius;
		return new Rect(leftX, topY, rightX - leftX, bottomY - topY);
	}

	@Override
	public String toString() {
		return String.format("Circle[x: %d, y: %d, r: %d]",
				center.getX(), center.getY(), radius);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(center, radius, anchor);
	}

	@Override
	public boolean collide(Collision s) {
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
	public Point getAnchor() {
		return anchor;
	}

	@Override
	public Collision moveByAnchor(int x, int y) {
		final long dX = x - getAnchor().getX();
		final long dY = y - getAnchor().getY();

		final long cX = center.getX() + dX;
		final long cY = center.getY() + dY;

		final Circle c = new Circle(cX, cY, radius, dX, dY);
		return c;
	}
}
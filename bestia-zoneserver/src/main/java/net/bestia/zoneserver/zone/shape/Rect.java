package net.bestia.zoneserver.zone.shape;

import java.util.Objects;

/**
 * Rectangle. Immutable. Can be used as collision bounding box shape and other
 * things.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public class Rect implements CollisionShape {

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	private final int anchorX;
	private final int anchorY;

	/**
	 * Ctor. Createa a bounding box at x and y equals 0. The anchor is set
	 * default to the middle.
	 * 
	 * @param width
	 *            Width
	 * @param height
	 *            Height
	 */
	public Rect(int width, int height) {
		this.x = 0;
		this.y = 0;
		this.width = width;
		this.height = height;

		this.anchorX = width / 2;
		this.anchorY = height / 2;
	}

	public Rect(int x, int y, int width, int height, int anchorX, int anchorY) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;

		checkNotNegative(width, height);
		checkAnchor(anchorX, anchorY);

		this.anchorX = anchorX;
		this.anchorY = anchorY;
	}
	
	private void checkAnchor(int aX, int aY) {
		if (aX < x || aX > x + width) {
			throw new IllegalArgumentException("X must be inside the rectangle.");
		}
		if (aY < y || aY > y + height) {
			throw new IllegalArgumentException("Y must be inside the rectangle.");
		}
	}

	/**
	 * Ctor.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	public Rect(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;

		checkNotNegative(width, height);

		this.width = width;
		this.height = height;

		this.anchorX = x + width / 2;
		this.anchorY = y + height / 2;
	}

	/*
	 * public Rect(Vector2 s, Vector2 size) { this.x = s.x; this.y = s.y;
	 * 
	 * checkNotNegative(size.x, size.y);
	 * 
	 * this.width = size.x; this.height = size.y; }
	 */

	private void checkNotNegative(int width, int height) {
		if (width < 0) {
			throw new IllegalArgumentException("Width can not be null.");
		}
		if (height < 0) {
			throw new IllegalArgumentException("Height can not be null.");
		}
	}

	@Override
	public String toString() {
		return String.format("Dimension[x: %d, y: %d, width: %d, height: %d]", x, y, width, height);
	}

	@Override
	public int hashCode() {
		return Objects.hash(width, height, x, y);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof Rect)) {
			return false;
		}

		Rect p = (Rect) o;
		return width == p.width && height == p.height && x == p.x && y == p.y;
	}

	/**
	 * Returns the width.
	 * 
	 * @return Width.
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Returns the height.
	 * 
	 * @return Height.
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Returns the x value. Offset of the box from origin.
	 * 
	 * @return x
	 */
	public int getX() {
		return x;
	}

	/**
	 * Returns the y value. Offset of the box from origin.
	 * 
	 * @return y
	 */
	public int getY() {
		return y;
	}

	/**
	 * Checks if the given coordinate lies within the rectangle.
	 * 
	 * @param s
	 *            Coordinates to check against this rectangle.
	 * @return TRUE if it lies within, FALSE otherwise.
	 */
	@Override
	public boolean collide(Point s) {
		return CollisionHelper.collide(s, this);
	}

	@Override
	public boolean collide(Circle s) {
		return CollisionHelper.collide(s, this);
	}

	@Override
	public boolean collide(Rect s) {
		return CollisionHelper.collide(s, this);
	}

	@Override
	public boolean collide(CollisionShape s) {
		return s.collide(this);
	}

	@Override
	public Rect getBoundingBox() {
		return this;
	}

	@Override
	public Point getAnchor() {
		return new Point(anchorX, anchorY);
	}

	@Override
	public CollisionShape moveByAnchor(int x, int y) {
		final int dX = x - getAnchor().x;
		final int dY = y - getAnchor().y;

		final int cX = getX() + dX;
		final int cY = getY() + dY;

		final Rect r = new Rect(cX, cY, getWidth(), getHeight(), x, y);
		return r;
	}
}

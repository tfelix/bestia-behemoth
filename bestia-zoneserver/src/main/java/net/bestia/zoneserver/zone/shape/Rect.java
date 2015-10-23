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

	private final Vector2 anchor;

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

		this.anchor = new Vector2(width / 2, height / 2);
	}

	public Rect(int x, int y, int width, int height, float anchorX, float anchorY) {
		this.x = x;
		this.y = y;

		checkNotNegative(width, height);

		this.width = width;
		this.height = height;

		this.anchor = new Vector2(x + (int) (width * anchorX), y + (int) (height * anchorY));
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

		this.anchor = new Vector2(x + width / 2, y + height / 2);
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
	public boolean collide(Vector2 s) {
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
	public Vector2 getAnchor() {
		return anchor;
	}
}

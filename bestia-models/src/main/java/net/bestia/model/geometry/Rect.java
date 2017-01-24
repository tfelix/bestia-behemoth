package net.bestia.model.geometry;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rectangle. Immutable. Can be used as collision bounding box shape and other
 * things.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public final class Rect implements CollisionShape, Serializable {

	private static final long serialVersionUID = 1L;
	
	@JsonProperty("o")
	private final Point origin;
	
	@JsonProperty("s")
	private final Size size;
	
	@JsonProperty("a")
	private final Point anchor;

	/**
	 * Ctor. Createa a bounding box at x and y equals 0. The anchor is set
	 * default to the middle.
	 * 
	 * @param width
	 *            Width
	 * @param height
	 *            Height
	 */
	public Rect(long width, long height) {
		this(0, 0, width, height);
		
		// no op.
	}

	public Rect(long x, long y, long width, long height, long anchorX, long anchorY) {
		
		checkNotNegative(width, height);

		this.origin = new Point(x, y);
		this.size = new Size(width, height);
		
		checkAnchor(anchorX, anchorY);
		
		this.anchor = new Point(anchorX, anchorY);
	}

	/**
	 * Ctor.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	public Rect(long x, long y, long width, long height) {
		this(x, y, width, height, width / 2, height / 2);

		// no op.
	}

	private void checkAnchor(long aX, long aY) {
		if (aX < 0 || aX > size.getWidth()) {
			throw new IllegalArgumentException("X must be inside the rectangle.");
		}
		if (aY < 0 || aY > size.getHeight()) {
			throw new IllegalArgumentException("Y must be inside the rectangle.");
		}
	}

	private void checkNotNegative(long width, long height) {
		if (width < 0) {
			throw new IllegalArgumentException("Width can not be null.");
		}
		if (height < 0) {
			throw new IllegalArgumentException("Height can not be null.");
		}
	}

	@Override
	public String toString() {
		return String.format("Rect[origin: %s, size: %s]", origin.toString(), size.toString());
	}

	@Override
	public int hashCode() {
		return Objects.hash(origin, size, anchor);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof Rect)) {
			return false;
		}

		Rect p = (Rect) o;
		return size.equals(p.size) && origin.equals(p.origin) && anchor.equals(p.anchor);
	}

	/**
	 * Returns the width.
	 * 
	 * @return Width.
	 */
	public long getWidth() {
		return size.getWidth();
	}

	/**
	 * Returns the height.
	 * 
	 * @return Height.
	 */
	public long getHeight() {
		return size.getHeight();
	}

	/**
	 * Returns the x value. Offset of the box from origin.
	 * 
	 * @return x
	 */
	public long getX() {
		return origin.getX();
	}

	/**
	 * Returns the y value. Offset of the box from origin.
	 * 
	 * @return y
	 */
	public long getY() {
		return origin.getY();
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
		return anchor;
	}

	@Override
	public CollisionShape moveByAnchor(int x, int y) {
		final long dX = x - (getX() + getAnchor().getX());
		final long dY = y - (getY() + getAnchor().getY());

		final long cX = getX() + dX;
		final long cY = getY() + dY;

		final Rect r = new Rect(cX, cY, getWidth(), getHeight(), getAnchor().getX(), getAnchor().getY());
		return r;
	}

	public Point getOrigin() {
		return origin;
	}
}

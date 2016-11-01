package net.bestia.model.shape;

import java.io.Serializable;
import java.util.Objects;

import net.bestia.model.domain.Position;

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public final class Point implements Collision, Serializable {

	private static final long serialVersionUID = 1L;

	private final long x;
	private final long y;

	public Point(long x, long y) {
		this.x = x;
		this.y = y;
	}

	public String toString() {
		return String.format("Point[x: %d, y: %d]", x, y);
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof Point)) {
			return false;
		}

		// No need to check for anchor since it should be the same as x and y.
		Point p = (Point) o;
		return x == p.x && y == p.y;
	}

	/**
	 * The X coordinate of this point.
	 * 
	 * @return X
	 */
	public long getX() {
		return x;
	}

	/**
	 * The Y coordinate of this point.
	 * 
	 * @return Y
	 */
	public long getY() {
		return y;
	}

	@Override
	public boolean collide(Point s) {
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
	public boolean collide(Collision s) {
		return s.collide(this);
	}

	@Override
	public Point getAnchor() {
		return this;
	}

	@Override
	public Collision moveByAnchor(int x, int y) {
		return new Point(x, y);
	}

	/**
	 * Helper method to transform a {@link Location} into a Vector which is
	 * often needed for calculations.
	 * 
	 * @param loc
	 *            The position to generate a vector from.
	 * @return A generated {@link Point} from the {@link Location}.
	 */
	public static Point fromPosition(Position loc) {
		return new Point(loc.getX(), loc.getY());
	}

	/**
	 * Returns the euclidian distance to the other point p.
	 * 
	 * @param p
	 *            The other point to calculate the euclidian distance.
	 * @return The distance from this point to the given point p.
	 */
	public double getDistance(Point p) {
		return Math.sqrt(Math.abs(getX() - p.getX()) + Math.abs(getY() - p.getY()));
	}

}
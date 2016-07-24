package net.bestia.zoneserver.zone.shape;

import java.util.Objects;

import net.bestia.model.domain.Location;

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 * 
 * @author Thomas Felix <thoams.felix@tfelix.de>
 *
 */
public class Point implements CollisionShape {

	public final int x;
	public final int y;

	private final int anchorX;
	private final int anchorY;

	public Point(int x, int y) {
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
		if (o == null || !(o instanceof Point)) {
			return false;
		}

		// No need to check for anchor since it should be the same as x and y.
		Point p = (Point) o;
		return x == p.x && y == p.y;
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
	public boolean collide(CollisionShape s) {
		return s.collide(this);
	}

	@Override
	public Point getAnchor() {
		return new Point(anchorX, anchorY);
	}

	@Override
	public CollisionShape moveByAnchor(int x, int y) {
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
	public static Point fromLocation(Location loc) {
		return new Point(loc.getX(), loc.getY());
	}

}

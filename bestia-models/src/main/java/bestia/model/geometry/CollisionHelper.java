package bestia.model.geometry;

/**
 * This class contains the shared collision code for the {@link CollisionShape}
 * implementations. Since collision is implemented with a visitor pattern code
 * would have to be implemented twice. In order to prevent this code sharing all
 * the collision methods are implemented here.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
final class CollisionHelper {

	/**
	 * Priv. ctor. No instance. Use static methods.
	 */
	private CollisionHelper() {
		// no op.
	}

	/**
	 * Checks if a {@link Point} and a {@link Rect} collide.
	 * 
	 * @param s
	 *            Vector.
	 * @param r
	 *            Rect.
	 * @return TRUE if they collide. FALSE otherwise.
	 */
	public static boolean collide(Point s, Rect r) {

		final boolean xLeft = s.getX() < r.getX();
		final boolean yTop = s.getY() < r.getY();
		final boolean xRight = s.getX() > (r.getX() + r.getWidth());
		final boolean yBottom = s.getY() > r.getY() + r.getHeight();

		return !xLeft && !yTop && !xRight && !yBottom;
	}

	/**
	 * Checks if a {@link Circle} and a {@link Rect} collide.
	 * 
	 * @param s
	 *            Circle.
	 * @param r
	 *            Rect.
	 * @return TRUE if they collide. FALSE otherwise.
	 */
	public static boolean collide(Circle s, Rect r) {

		final Point cc = s.getCenter();

		if (r.collide(cc)) {
			return true;
		}

		// Check where the center of the circle is compared to the rectangle.
		final long x = r.getX();
		final long y = r.getY();
		final long x2 = x + r.getWidth();
		final long y2 = y + r.getHeight();

		if (cc.getX() < x && cc.getY() < y) {
			// Top left case.

			final int d = (int) Math.sqrt((cc.getX() - x) * (cc.getX() - x) +
					(cc.getY() - y) * (cc.getY() - y));
			return d <= s.getRadius();
		} else if (cc.getX() >= x && cc.getX() <= x2 && cc.getY() <= y) {
			// Top case.
			final long d = y - cc.getY();
			return d <= s.getRadius();
		} else if (cc.getX() > x2 && cc.getY() < y) {
			// Right top case.
			final int d = (int) Math.sqrt((x2 - cc.getX()) * (x2 - cc.getX()) + (y - cc.getY()) * (y - cc.getY()));
			return d <= s.getRadius();
		} else if (cc.getX() >= x2 && cc.getY() >= y && cc.getY() <= y2) {
			// Right case.
			final long d = cc.getX() - x2;
			return d <= s.getRadius();
		} else if (cc.getX() > x2 && cc.getY() > y2) {
			// Right bottom case.
			final int d = (int) Math.sqrt((cc.getX() - x2) * (cc.getX() - x2) +
					(cc.getY() - y2) * (cc.getY() - y2));
			return d <= s.getRadius();
		} else if (cc.getX() >= x && cc.getX() <= x2 && cc.getY() >= y2) {
			// Bottom case.
			final long d = cc.getY() - y2;
			return d <= s.getRadius();
		} else if (cc.getX() < x && cc.getY() > y2) {
			// Bottom left case.
			final int d = (int) Math.sqrt((cc.getX() - x) * (cc.getX() - x) + (cc.getY() - y2) * (cc.getY() - y2));
			return d <= s.getRadius();
		} else {
			// Left case.
			final long d = x - cc.getX();
			return d <= s.getRadius();
		}
	}

	/**
	 * Checks if a {@link Circle} and a {@link Point} collide.
	 * 
	 * @param c
	 *            Circle.
	 * @param v
	 *            Vector.
	 * @return TRUE if they collide. FALSE otherwise.
	 */
	public static boolean collide(Circle c, Point v) {
		final long distance = Math.abs(c.getCenter().getX() - v.getX()) +
				Math.abs(c.getCenter().getY() - v.getY());
		return (distance <= c.getRadius());
	}

	/**
	 * Checks if two {@link Rect} collides.
	 * 
	 * @param r1
	 *            First Rect.
	 * @param r2
	 *            Second Rect.
	 * @return TRUE if they collide. FALSE otherwise.
	 */
	public static boolean collide(Rect r1, Rect r2) {
		final boolean xCheck1 = r1.getX() < r2.getX() + r2.getWidth();
		final boolean xCheck2 = r1.getX() + r1.getWidth() > r2.getX();
		final boolean yCheck1 = r1.getY() < r2.getY() + r2.getHeight();
		final boolean yCheck2 = r1.getHeight() + r1.getY() > r2.getY();

		return xCheck1 && xCheck2 && yCheck1 && yCheck2;
	}

	/**
	 * Checks if two {@link Circle} collide.
	 * 
	 * @param s
	 *            First circle.
	 * @param s2
	 *            Second circle.
	 * @return TRUE if they collide. FALSE otherwise.
	 */
	public static boolean collide(Circle s, Circle s2) {
		final double distance = s.getCenter().getDistance(s2.getCenter());
		return (distance < s.getRadius() + s2.getRadius());
	}

	/**
	 * Checks if two {@link Point} collide.
	 * 
	 * @param s
	 *            First vector.
	 * @param s2
	 *            Second vector.
	 * @return TRUE if they collide. False otherwise.
	 */
	public static boolean collide(Point s, Point s2) {
		return s.equals(s2);
	}

}

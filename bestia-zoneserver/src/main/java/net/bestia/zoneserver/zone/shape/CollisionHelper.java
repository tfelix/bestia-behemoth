package net.bestia.zoneserver.zone.shape;

/**
 * This class contains the shared collision code for the {@link Collision}
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

		final boolean xLeft = s.x < r.getX();
		final boolean yTop = s.y < r.getY();
		final boolean xRight = s.x > (r.getX() + r.getWidth());
		final boolean yBottom = s.y > r.getY() + r.getHeight();

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

		if (cc.x < x && cc.y < y) {
			// Top left case.
			final int d = (int) Math.sqrt((cc.x - x) * (cc.x - x) + (cc.y - y) * (cc.y - y));
			return d <= s.getRadius();
		} else if (cc.x >= x && cc.x <= x2 && cc.y <= y) {
			// Top case.
			final long d = y - cc.y;
			return d <= s.getRadius();
		} else if (cc.x > x2 && cc.y < y) {
			// Right top case.
			final int d = (int) Math.sqrt((x2 - cc.x) * (x2 - cc.x) + (y - cc.y) * (y - cc.y));
			return d <= s.getRadius();
		} else if (cc.x >= x2 && cc.y >= y && cc.y <= y2) {
			// Right case.
			final long d = cc.x - x2;
			return d <= s.getRadius();
		} else if (cc.x > x2 && cc.y > y2) {
			// Right bottom case.
			final int d = (int) Math.sqrt((cc.x - x2) * (cc.x - x2) + (cc.y - y2) * (cc.y - y2));
			return d <= s.getRadius();
		} else if (cc.x >= x && cc.x <= x2 && cc.y >= y2) {
			// Bottom case.
			final long d = cc.y - y2;
			return d <= s.getRadius();
		} else if (cc.x < x && cc.y > y2) {
			// Bottom left case.
			final int d = (int) Math.sqrt((cc.x - x) * (cc.x - x) + (cc.y - y2) * (cc.y - y2));
			return d <= s.getRadius();
		} else {
			// Left case.
			final long d = x - cc.x;
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
		final long distance = Math.abs(c.getCenter().x - v.x) + Math.abs(c.getCenter().y - v.y);
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
		final long distance = Math.abs(s.getCenter().x - s2.getCenter().x)
				+ Math.abs(s.getCenter().y - s2.getCenter().y);
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
		return s.x == s2.x && s.y == s2.y;
	}

}

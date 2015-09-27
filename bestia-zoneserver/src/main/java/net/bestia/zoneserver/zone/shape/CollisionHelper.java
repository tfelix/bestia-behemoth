package net.bestia.zoneserver.zone.shape;

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

	public static boolean collide(Vector2 s, Rect r) {

		final boolean xLeft = s.x < r.getX();
		final boolean yTop = s.y < r.getY();
		final boolean xRight = s.x > (r.getX() + r.getWidth());
		final boolean yBottom = s.y > r.getY() + r.getHeight();

		return !xLeft && !yTop && !xRight && !yBottom;
	}

	public static boolean collide(Circle s, Rect r) {
		
		final Vector2 cc = s.getCenter();

		if (s.collide(cc)) {
			return true;
		}

		// Check where the center of the circle is compared to the rectangle.
		// TODO

		return false;
	}

	public static boolean collide(Circle c, Vector2 v) {
		final int distance = Math.abs(c.getCenter().x - v.x) + Math.abs(c.getCenter().y - v.y);
		return (distance <= c.getRadius());
	}

	public static boolean collide(Rect r1, Rect r2) {
		final boolean xCheck1 = r1.getX() < r2.getX() + r2.getWidth();
		final boolean xCheck2 = r1.getX() + r1.getWidth() > r2.getX();
		final boolean yCheck1 = r1.getY() < r2.getY() + r2.getHeight();
		final boolean yCheck2 = r1.getHeight() + r1.getY() > r2.getY();

		return xCheck1 && xCheck2 && yCheck1 && yCheck2;
	}

	public static boolean collide(Circle s, Circle s2) {
		final int distance = Math.abs(s.getCenter().x - s2.getCenter().x)
				+ Math.abs(s.getCenter().y - s2.getCenter().y);
		return (distance < s.getRadius() + s2.getRadius());
	}

	public static boolean collide(Vector2 s, Vector2 s2) {
		return s.x == s2.x && s.y == s2.y;
	}

}

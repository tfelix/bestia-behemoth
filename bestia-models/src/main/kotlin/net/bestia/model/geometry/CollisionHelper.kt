package net.bestia.model.geometry

/**
 * This class contains the shared collision code for the [Shape]
 * implementations. Since collision is implemented with a visitor pattern code
 * would have to be implemented twice. In order to prevent this code sharing all
 * the collision methods are implemented here.
 *
 * @author Thomas Felix
 */
internal object CollisionHelper {

  /**
   * Checks if a [Point] and a [Rect] collide.
   *
   * @param s
   * Vector.
   * @param r
   * Rect.
   * @return TRUE if they collide. FALSE otherwise.
   */
  fun collide(s: Point, r: Rect): Boolean {

    val xLeft = s.x < r.x
    val yTop = s.y < r.y
    val xRight = s.x > r.x + r.width
    val yBottom = s.y > r.y + r.height

    return !xLeft && !yTop && !xRight && !yBottom
  }

  /**
   * Checks if a [Circle] and a [Rect] collide.
   *
   * @param s
   * Circle.
   * @param r
   * Rect.
   * @return TRUE if they collide. FALSE otherwise.
   */
  fun collide(s: Circle, r: Rect): Boolean {

    val cc = s.center

    if (r.collide(cc)) {
      return true
    }

    // Check where the center of the circle is compared to the rectangle.
    val x = r.x
    val y = r.y
    val x2 = x + r.width
    val y2 = y + r.height

    if (cc.x < x && cc.y < y) {
      // Top left case.

      val d = Math.sqrt(((cc.x - x) * (cc.x - x) + (cc.y - y) * (cc.y - y)).toDouble()).toInt()
      return d <= s.radius
    } else if (cc.x in x..x2 && cc.y <= y) {
      // Top case.
      val d = y - cc.y
      return d <= s.radius
    } else if (cc.x > x2 && cc.y < y) {
      // Right top case.
      val d = Math.sqrt(((x2 - cc.x) * (x2 - cc.x) + (y - cc.y) * (y - cc.y)).toDouble()).toInt()
      return d <= s.radius
    } else if (cc.x >= x2 && cc.y >= y && cc.y <= y2) {
      // Right case.
      val d = cc.x - x2
      return d <= s.radius
    } else if (cc.x > x2 && cc.y > y2) {
      // Right bottom case.
      val d = Math.sqrt(((cc.x - x2) * (cc.x - x2) + (cc.y - y2) * (cc.y - y2)).toDouble()).toInt()
      return d <= s.radius
    } else if (cc.x in x..x2 && cc.y >= y2) {
      // Bottom case.
      val d = cc.y - y2
      return d <= s.radius
    } else if (cc.x < x && cc.y > y2) {
      // Bottom left case.
      val d = Math.sqrt(((cc.x - x) * (cc.x - x) + (cc.y - y2) * (cc.y - y2)).toDouble()).toInt()
      return d <= s.radius
    } else {
      // Left case.
      val d = x - cc.x
      return d <= s.radius
    }
  }

  /**
   * Checks if a [Circle] and a [Point] collide.
   *
   * @param c
   * Circle.
   * @param v
   * Vector.
   * @return TRUE if they collide. FALSE otherwise.
   */
  fun collide(c: Circle, v: Point): Boolean {
    val distance = Math.abs(c.center.x - v.x) + Math.abs(c.center.y - v.y)
    return distance <= c.radius
  }

  /**
   * Checks if two [Rect] collides.
   *
   * @param r1
   * First Rect.
   * @param r2
   * Second Rect.
   * @return TRUE if they collide. FALSE otherwise.
   */
  fun collide(r1: Rect, r2: Rect): Boolean {
    val xCheck1 = r1.x < r2.x + r2.width
    val xCheck2 = r1.x + r1.width > r2.x
    val yCheck1 = r1.y < r2.y + r2.height
    val yCheck2 = r1.height + r1.y > r2.y

    return xCheck1 && xCheck2 && yCheck1 && yCheck2
  }

  /**
   * Checks if two [Circle] collide.
   *
   * @param s
   * First circle.
   * @param s2
   * Second circle.
   * @return TRUE if they collide. FALSE otherwise.
   */
  fun collide(s: Circle, s2: Circle): Boolean {
    val distance = s.center.getDistance(s2.center)
    return distance < s.radius + s2.radius
  }

  /**
   * Checks if two [Point] collide.
   *
   * @param s
   * First vector.
   * @param s2
   * Second vector.
   * @return TRUE if they collide. False otherwise.
   */
  fun collide(s: Point, s2: Point): Boolean {
    return s == s2
  }
}

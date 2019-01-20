package net.bestia.model.geometry

import org.junit.Assert
import org.junit.Test

class CircleTest {

  @Test(expected = IllegalArgumentException::class)
  fun negRadius_throws() {
    Circle(10, 10, -4)
  }

  @Test
  fun ctor_ok() {
    Circle(3, 10, 2)
  }

  @Test
  fun getCenter_ok() {
    val (center) = Circle(3, 10, 2)
    Assert.assertTrue(center == Point(3, 10))
  }

  @Test
  fun getAnchor_ok() {
    var c = Circle(3, 10, 2)
    Assert.assertTrue(c.anchor == Point(3, 10))

    c = Circle(10, 10, 5, 12, 12)
    Assert.assertTrue(c.anchor == Point(12, 12))
  }

  @Test
  fun getBoundingBox_ok() {
    val c = Circle(3, 10, 2)
    val bb = c.boundingBox
    Assert.assertEquals(Rect(1, 8, 4, 4), bb)
  }

  @Test
  fun collide_circle_ok() {
    val c = Circle(10, 10, 2)
    val c2 = Circle(15, 15, 2)
    val c3 = Circle(10, 11, 5)

    Assert.assertTrue(c.collide(c3))
    Assert.assertFalse(c.collide(c2))
    Assert.assertTrue(c2.collide(c3))
  }

  @Test
  fun collide_point_ok() {
    val c = Circle(10, 10, 2)
    val p1 = Point(12, 10)
    val p2 = Point(10, 10)
    val p3 = Point(45, 23)

    Assert.assertTrue(c.collide(p1))
    Assert.assertTrue(c.collide(p2))
    Assert.assertFalse(c.collide(p3))
  }

  @Test
  fun collide_rect_ok() {
    val c = Circle(10, 10, 2)
    val r1 = Rect(3, 3, 20, 20)
    val r2 = Rect(12, 10, 4, 4)
    val r3 = Rect(10, 13, 5, 5)

    Assert.assertTrue(c.collide(r1))
    Assert.assertTrue(c.collide(r2))
    Assert.assertFalse(c.collide(r3))
  }

  @Test
  fun moveByAnchor_ok() {
    var c1 = Circle(10, 10, 2)
    var c2 = Circle(14, 14, 4, 16, 16)

    c1 = c1.moveByAnchor(15, 16)
    c2 = c2.moveByAnchor(10, 10)

    Assert.assertTrue(c1.center == Point(15, 16))
    Assert.assertTrue(c2.center == Point(8, 8))
  }

}

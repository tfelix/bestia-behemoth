package net.bestia.model.geometry

import org.junit.Assert
import org.junit.Test

class RectTest {

  @Test
  fun moveAnchor() {
    var r = Rect(10, 10, 3, 3)
    r = r.moveTo(15, 15)

    Assert.assertEquals(14, r.x)
    Assert.assertEquals(14, r.y)
    Assert.assertEquals(15, r.anchor.x)
    Assert.assertEquals(15, r.anchor.y)
  }

  @Test
  fun ctor_anchorAtCorners() {
    Rect(0, 0, 20, 20)
    Rect(-10, -10, 30, 30)
    Rect(-10, -10, 30, 30, -10, -10)
  }

  @Test
  fun collide_point_success() {
    val r1 = Rect(10, 10, 15, 15)
    val p2 = Point(10, 10)

    Assert.assertTrue(r1.collide(p2))
    Assert.assertTrue(p2.collide(r1))
  }

  @Test
  fun collide_point_fail() {
    val r1 = Rect(10, 10, 15, 15)
    val p2 = Point(9, 10)
    val p3 = Point(25, 28)

    Assert.assertFalse(r1.collide(p2))
    Assert.assertFalse(r1.collide(p3))
    Assert.assertFalse(p2.collide(r1))
    Assert.assertFalse(p3.collide(r1))
  }

  @Test
  fun collide_circle_success() {
    val r = Rect(10, 10, 5, 5)
    val c = Circle(18, 10, 7)

    Assert.assertTrue(r.collide(c))
    Assert.assertTrue(c.collide(r))
  }

  @Test
  fun collide_circle_fail() {
    val r = Rect(10, 10, 5, 5)
    val c = Circle(18, 10, 2)

    Assert.assertFalse(r.collide(c))
    Assert.assertFalse(c.collide(r))
  }

  @Test
  fun collide_rect_success() {
    val r = Rect(10, 10, 10, 10)
    val r2 = Rect(11, 10, 5, 5)

    Assert.assertTrue(r.collide(r2))
    Assert.assertTrue(r2.collide(r))
  }

  @Test
  fun collide_rect_fail() {
    val r = Rect(10, 10)
    val r2 = Rect(11, 10, 5, 5)

    Assert.assertFalse(r.collide(r2))
    Assert.assertFalse(r2.collide(r))
  }

  @Test
  fun getAnchor_anchorInMiddle() {
    val r = Rect(12, 12, 3, 3)
    Assert.assertEquals(Point(13, 13), r.anchor)
  }

  @Test
  fun getBoundingBox() {
    val r = Rect(10, 10, 5, 5)
    Assert.assertEquals(10, r.boundingBox.x)
    Assert.assertEquals(10, r.boundingBox.y)
    Assert.assertEquals(5, r.boundingBox.height)
    Assert.assertEquals(5, r.boundingBox.width)
  }
}

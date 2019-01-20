package net.bestia.model.geometry

import java.io.Serializable

import org.junit.Assert
import org.junit.Test

import net.bestia.model.geometry.Circle
import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect

class PointTest {

  @Test
  fun is_serializable() {
    Assert.assertTrue(Serializable::class.java.isAssignableFrom(Point::class.java))
  }

  @Test
  fun moveAnchor() {
    var p = Point(10, 10)
    p = p.moveByAnchor(3, 3)
    Assert.assertEquals(3, p.x)
    Assert.assertEquals(3, p.y)
  }

  @Test
  fun collide_point_success() {
    val p1 = Point(10, 10)
    val p2 = Point(10, 10)

    Assert.assertTrue(p1.collide(p2))
  }

  @Test
  fun collide_point_fail() {
    val p1 = Point(10, 10)
    val p2 = Point(11, 10)

    Assert.assertFalse(p1.collide(p2))
  }

  @Test
  fun collide_circle_success() {
    val p1 = Point(10, 10)
    val c = Circle(13, 10, 4)

    Assert.assertTrue(p1.collide(c))
  }

  @Test
  fun collide_circle_fail() {
    val p1 = Point(10, 10)
    val c = Circle(13, 10, 3)

    Assert.assertTrue(p1.collide(c))
  }

  @Test
  fun collide_rect_success() {
    val p1 = Point(10, 10)
    val r = Rect(9, 9, 5, 5)

    Assert.assertTrue(p1.collide(r))
  }

  @Test
  fun collide_rect_fail() {
    val p1 = Point(10, 10)
    val r = Rect(11, 10, 5, 5)

    Assert.assertFalse(p1.collide(r))
  }

  @Test
  fun getAnchor() {
    val p1 = Point(10, 10)
    Assert.assertEquals(p1, p1.anchor)
  }

  @Test
  fun getBoundingBox() {
    val p1 = Point(10, 10)
    Assert.assertEquals(10, p1.boundingBox.x)
    Assert.assertEquals(10, p1.boundingBox.y)
    Assert.assertEquals(1, p1.boundingBox.height)
    Assert.assertEquals(1, p1.boundingBox.width)
  }


  fun getDistance() {
    // TODO
  }

  fun jackson_serialize() {
    // TODO
  }

}

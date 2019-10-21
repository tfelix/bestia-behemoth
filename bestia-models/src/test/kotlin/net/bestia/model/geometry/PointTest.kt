package net.bestia.model.geometry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.Serializable

class PointTest {

  @Test
  fun is_serializable() {
    assertTrue(Serializable::class.java.isAssignableFrom(Vec3::class.java))
  }

  @Test
  fun moveAnchor() {
    var p = Vec3(10, 10, 10)
    p = p.moveTo(3, 3, 3)
    assertEquals(3, p.x)
    assertEquals(3, p.y)
  }

  @Test
  fun collide_point_success() {
    val p1 = Vec3(10, 10, 10)
    val p2 = Vec3(10, 10, 10)

    assertTrue(p1.collide(p2))
  }

  @Test
  fun collide_point_fail() {
    val p1 = Vec3(10, 10, 10)
    val p2 = Vec3(11, 10, 10)

    assertFalse(p1.collide(p2))
  }

  @Test
  fun collide_circle_success() {
    val p1 = Vec3(10, 10, 10)
    val c = Sphere(13, 10, 10, 4)

    assertTrue(p1.collide(c))
  }

  @Test
  fun collide_circle_fail() {
    val p1 = Vec3(10, 10, 10)
    val c = Sphere(13, 10, 10, 3)

    assertTrue(p1.collide(c))
  }

  @Test
  fun collide_rect_success() {
    val p1 = Vec3(10, 10, 10)
    val r = Rect(9, 9, 9, 5, 5, 5)

    assertTrue(p1.collide(r))
  }

  @Test
  fun collide_rect_fail() {
    val p1 = Vec3(10, 10, 10)
    val r = Rect(11, 10, 10, 5, 5, 5)

    assertFalse(p1.collide(r))
  }

  @Test
  fun getAnchor() {
    val p1 = Vec3(10, 10, 10)
    assertEquals(p1, p1.anchor)
  }

  @Test
  fun getBoundingBox() {
    val p1 = Vec3(10, 10, 10)
    assertEquals(10, p1.boundingBox.x)
    assertEquals(10, p1.boundingBox.y)
    assertEquals(1, p1.boundingBox.height)
    assertEquals(1, p1.boundingBox.width)
  }
}

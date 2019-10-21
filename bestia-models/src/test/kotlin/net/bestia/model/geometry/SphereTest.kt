package net.bestia.model.geometry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SphereTest {

  @Test
  fun negRadius_throws() {
    assertThrows(java.lang.IllegalArgumentException::class.java) {
      Sphere(10, 10, 4, -4)
    }
  }

  @Test
  fun getCenter_ok() {
    val (center) = Sphere(3, 10, 10, 2)
    assertTrue(center == Vec3(3, 10, 2))
  }

  @Test
  fun collide_circle_ok() {
    val c = Sphere(10, 10, 10, 2)
    val c2 = Sphere(15, 15, 15, 2)
    val c3 = Sphere(10, 11, 10, 5)

    assertTrue(c.collide(c3))
    assertFalse(c.collide(c2))
    assertTrue(c2.collide(c3))
  }

  @Test
  fun collide_point_ok() {
    val c = Sphere(10, 10, 10, 2)
    val p1 = Vec3(12, 10, 0)
    val p2 = Vec3(10, 10, 0)
    val p3 = Vec3(45, 23, 0)

    assertTrue(c.collide(p1))
    assertTrue(c.collide(p2))
    assertFalse(c.collide(p3))
  }

  @Test
  fun collide_rect_ok() {
    val c = Sphere(10, 10, 10, 2)
    val r1 = Rect(3, 3, 3, 20, 20, 20)
    val r2 = Rect(12, 10, 10, 4, 4, 4)
    val r3 = Rect(10, 13, 10, 5, 5, 5)

    assertTrue(c.collide(r1))
    assertTrue(c.collide(r2))
    assertFalse(c.collide(r3))
  }

  @Test
  fun moveByAnchor_ok() {
    var c1 = Sphere(10, 10, 10, 2)
    var c2 = Sphere(10, 10, 10, 2)

    c1 = c1.moveTo(15, 16, 7)
    c2 = c2.moveTo(10, 10, 10)

    assertTrue(c1.center == Vec3(15, 16, 0))
    assertTrue(c2.center == Vec3(8, 8, 0))
  }
}

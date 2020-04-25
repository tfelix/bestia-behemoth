package net.bestia.model.geometry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RectTest {

  @Test
  fun moveAnchor() {
    var r = Cube(10, 10, 10, 3, 3, 3)
    r = r.moveTo(15, 15, 15)

    assertEquals(14, r.x)
    assertEquals(14, r.y)
    assertEquals(Vec3(15, 15, 15), r.anchor)
  }

  @Test
  fun ctor_anchorAtCorners() {
    Cube(0, 0, 0, 20, 20, 20, 20, 20, 20)
    Cube(-10, -10, -10, 30, 30, 30, -10, 20, 20)
    Cube(-10, -10, -10, 30, 30, 30, -10, -10, -10)
  }

  @Test
  fun collide_point_success() {
    val r1 = Cube(10, 10, 10, 15, 15, 15)
    val p2 = Vec3(10, 10, 10)

    assertTrue(r1.collide(p2))
    assertTrue(p2.collide(r1))
  }

  @Test
  fun collide_point_fail() {
    val r1 = Cube(10, 10, 10, 15, 15, 15)
    val p2 = Vec3(9, 10, 7)
    val p3 = Vec3(25, 28, 8)

    assertFalse(r1.collide(p2))
    assertFalse(r1.collide(p3))
    assertFalse(p2.collide(r1))
    assertFalse(p3.collide(r1))
  }

  @Test
  fun collide_circle_success() {
    val r = Cube(10, 10, 10, 5, 5, 5)
    val c = Sphere(18, 10, 10, 7)

    assertTrue(r.collide(c))
    assertTrue(c.collide(r))
  }

  @Test
  fun collide_circle_fail() {
    val r = Cube(10, 10, 10, 5, 5, 5)
    val c = Sphere(18, 10, 10, 2)

    assertFalse(r.collide(c))
    assertFalse(c.collide(r))
  }

  @Test
  fun collide_rect_success() {
    val r = Cube(10, 10, 10, 10, 10, 10)
    val r2 = Cube(11, 10, 10, 5, 5, 5)

    assertTrue(r.collide(r2))
    assertTrue(r2.collide(r))
  }

  @Test
  fun collide_rect_fail() {
    val r = Cube(10, 10, 10)
    val r2 = Cube(11, 10, 10, 5, 5, 5)

    assertFalse(r.collide(r2))
    assertFalse(r2.collide(r))
  }

  @Test
  fun getAnchor_anchorInMiddle() {
    val r = Cube(12, 12, 12, 3, 3, 3)
    assertEquals(Vec3(13, 13, 13), r.anchor)
  }

  @Test
  fun getBoundingBox() {
    val r = Cube(10, 10, 10, 5, 5, 5)
    assertEquals(10, r.boundingBox.x)
    assertEquals(10, r.boundingBox.y)
    assertEquals(5, r.boundingBox.height)
    assertEquals(5, r.boundingBox.width)
  }
}

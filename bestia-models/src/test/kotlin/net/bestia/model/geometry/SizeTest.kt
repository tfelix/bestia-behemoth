package net.bestia.model.geometry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.Serializable

class SizeTest {

  @Test
  fun is_serializable() {
    assertTrue(Serializable::class.java.isAssignableFrom(Size::class.java))
  }

  @Test
  fun ctor_negativeValue_throws() {
    assertThrows(java.lang.IllegalArgumentException::class.java) {
      Size(0, -10)
    }
  }

  @Test
  fun getter_ctor() {
    val (width, height) = Size(123, 10)
    assertEquals(123, width)
    assertEquals(10, height)
  }

  @Test
  fun equal() {
    val s1 = Size(10, 5)
    val s2 = Size(10, 5)
    val s3 = Size(3, 1)

    assertTrue(s1 == s2)
    assertTrue(s1 == s1)
    assertFalse(s2 == s3)
  }
}

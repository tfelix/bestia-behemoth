package net.bestia.model.geometry

import java.io.Serializable

import org.junit.Assert
import org.junit.Test

class SizeTest {

  @Test
  fun is_serializable() {
    Assert.assertTrue(Serializable::class.java.isAssignableFrom(Size::class.java))
  }

  @Test(expected = IllegalArgumentException::class)
  fun ctor_negativeValue_throws() {
    Size(0, -10)
  }

  @Test
  fun getter_ctor() {
    val (width, height) = Size(123, 10)
    Assert.assertEquals(123, width)
    Assert.assertEquals(10, height)
  }

  @Test
  fun equal() {
    val s1 = Size(10, 5)
    val s2 = Size(10, 5)
    val s3 = Size(3, 1)

    Assert.assertTrue(s1 == s2)
    Assert.assertTrue(s1 == s1)
    Assert.assertFalse(s2 == s3)
  }
}

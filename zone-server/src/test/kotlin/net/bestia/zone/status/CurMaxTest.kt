package net.bestia.zone.status

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CurMaxTest {

  @Test
  fun `should initialize with default values`() {
    val curMax = CurMax()

    assertEquals(0, curMax.current)
    assertEquals(0, curMax.max)
  }

  @Test
  fun `should set max value correctly`() {
    val curMax = CurMax()

    curMax.max = 100

    assertEquals(100, curMax.max)
    assertEquals(0, curMax.current)
  }

  @Test
  fun `should set current value correctly when less than max`() {
    val curMax = CurMax()
    curMax.max = 100
    curMax.current = 50

    assertEquals(50, curMax.current)
    assertEquals(100, curMax.max)
  }

  @Test
  fun `should clamp current to max when current is set higher than max`() {
    val curMax = CurMax()
    curMax.max = 50

    curMax.current = 100

    assertEquals(50, curMax.current)
    assertEquals(50, curMax.max)
  }

  @Test
  fun `should adjust current when max is reduced below current`() {
    val curMax = CurMax()
    curMax.max = 100
    curMax.current = 80

    curMax.max = 60

    assertEquals(60, curMax.current)
    assertEquals(60, curMax.max)
  }

  @Test
  fun `should not adjust current when max is increased above current`() {
    val curMax = CurMax()
    curMax.max = 50
    curMax.current = 30

    curMax.max = 100

    assertEquals(30, curMax.current)
    assertEquals(100, curMax.max)
  }

  @Test
  fun `should throw exception when setting negative current value`() {
    val curMax = CurMax()

    val exception = assertThrows<IllegalArgumentException> {
      curMax.current = -1
    }

    assertNotNull(exception)
  }

  @Test
  fun `should throw exception when setting negative max value`() {
    val curMax = CurMax()

    val exception = assertThrows<IllegalArgumentException> {
      curMax.max = -1
    }

    assertNotNull(exception)
  }

  @Test
  fun `should allow setting current equal to max`() {
    val curMax = CurMax()
    curMax.max = 100

    curMax.current = 100

    assertEquals(100, curMax.current)
    assertEquals(100, curMax.max)
  }

  @Test
  fun `should allow setting max equal to current`() {
    val curMax = CurMax()
    curMax.max = 100
    curMax.current = 50

    curMax.max = 50

    assertEquals(50, curMax.current)
    assertEquals(50, curMax.max)
  }

  @Test
  fun `should handle zero values correctly`() {
    val curMax = CurMax()

    curMax.max = 0
    curMax.current = 0

    assertEquals(0, curMax.current)
    assertEquals(0, curMax.max)
  }

  @Test
  fun `toString should return correct format`() {
    val curMax = CurMax()
    curMax.max = 100
    curMax.current = 75

    assertEquals("75/100", curMax.toString())
  }

  @Test
  fun `toString should work with zero values`() {
    val curMax = CurMax()

    assertEquals("0/0", curMax.toString())
  }

  @Test
  fun `should handle edge case where current is set before max`() {
    val curMax = CurMax()

    curMax.current = 50  // This should be clamped to 0 since max is 0

    assertEquals(0, curMax.current)
    assertEquals(0, curMax.max)
  }
}

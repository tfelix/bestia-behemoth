package net.bestia.model.map

import org.junit.Assert
import org.junit.Test

class TilePropertiesTest {

  @Test(expected = IllegalArgumentException::class)
  fun negWalkspeedSpeed_throws() {
    TileProperties(true, -100, false)
  }

  @Test
  fun correctArgs_getterOk() {
    val (isWalkable, walkspeed) = TileProperties(true, 100, false)
    Assert.assertTrue(isWalkable)
    Assert.assertEquals(100, walkspeed.toLong())
  }
}

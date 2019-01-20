package net.bestia.model.map

import net.bestia.model.geometry.Size
import org.junit.Assert
import org.junit.Test

class TilesetTest {

  val tileset: Tileset
    get() = Tileset("test", Size(10, 10), 100)

  @Test(expected = IllegalArgumentException::class)
  fun setProperties_invalidGid_throws() {
    val set = tileset
    set.setProperties(1234, TileProperties(true, 100, false))
  }

  @Test
  fun setProperties_validGid_ok() {
    val set = tileset
    set.setProperties(145, TileProperties(true, 100, false))
    set.setProperties(100, TileProperties(true, 100, false))
    set.setProperties(200, TileProperties(true, 100, false))
  }

  @Test
  fun contains_validGid_true() {
    val set = tileset
    Assert.assertTrue(set.contains(100))
    Assert.assertTrue(set.contains(156))
    Assert.assertTrue(set.contains(200))
  }

  @Test
  fun contains_invalidGid_false() {
    val set = tileset
    Assert.assertFalse(set.contains(1))
    Assert.assertFalse(set.contains(99))
    Assert.assertFalse(set.contains(201))
  }
}

package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorldMapFieldTest {

  private val region = CellRegion.world(8, 8, Resolution.KILOMETRE)

  /**
   * A linear west-to-east ramp from -3000 m to 4000 m, crossing sea level at cell 3.
   *
   * Linear on purpose: bicubic interpolation reproduces a linear ramp exactly, so a sample at a cell centre
   * is the cell's own value and the assertions below are about this field rather than about interpolation.
   */
  private val elevation = layer(LayerId.ELEVATION) { x, _ -> -3000f + x * 1000f }

  private val biome = IntLayer(LayerId.BIOME, region, IntArray(64)).apply {
    for (y in 0 until 8) for (x in 0 until 8) {
      this[x, y] = if (x <= 2) Biome.OCEAN.ordinal else Biome.TEMPERATE_FOREST.ordinal
    }
  }

  private val water = layer(LayerId.WATER_LEVEL) { x, _ -> if (x <= 2) 0f else Float.NaN }

  private val map = WorldMapField(elevation, biome, water, ice = null, seaLevel = 0.0)

  /** The centre of cell ([x], [y]) in metres. */
  private fun centre(x: Int, y: Int) = (x + 0.5) * 1000.0 to (y + 0.5) * 1000.0

  private fun colorAt(x: Int, y: Int): Int {
    val (worldX, worldY) = centre(x, y)
    return map.rgbAt(worldX, worldY, map.valueAt(worldX, worldY))
  }

  private fun valueAt(x: Int, y: Int) = centre(x, y).let { map.valueAt(it.first, it.second) }

  @Test
  fun `the sea surface is flat, whatever the seabed does`() {
    // What keeps the coastline crisp: shading a bathymetric surface makes the seabed's relief show through
    // the water as though it were land, and the map then reads as a world with no sea in it.
    for (x in 0..2) {
      assertEquals(0.0, valueAt(x, 4), 1e-9, "ocean cell $x is not at sea level")
    }
  }

  @Test
  fun `land is its own height`() {
    assertEquals(1000.0, valueAt(4, 4), 1e-6)
    assertEquals(4000.0, valueAt(7, 4), 1e-6)
  }

  @Test
  fun `sea colour comes from the depth under it, not from the flat surface`() {
    // The other half of the flat-sea decision: depth has to stay in the picture, so it moves into the colour.
    val deep = colorAt(0, 4)
    val shallow = colorAt(2, 4)

    assertNotEquals(deep, shallow, "a trench and a shelf came out the same blue")
    assertTrue(
      Colors.blue(shallow) > Colors.blue(deep) || Colors.green(shallow) > Colors.green(deep),
      "the shelf should be the lighter of the two"
    )
  }

  @Test
  fun `land colour is the biome's colour`() {
    val expected = BiomePalette().rgb(Biome.TEMPERATE_FOREST.ordinal.toDouble())

    assertEquals(expected, colorAt(5, 4))
    assertEquals(expected, colorAt(7, 4), "a mountain is still forest; relief is the shading's job, not the colour's")
  }

  @Test
  fun `a lake is bluer the deeper it is`() {
    // A lake bed at 1000 m with 40 m of water in it, against one with 2 m.
    val bed = layer(LayerId.ELEVATION) { _, _ -> 1000f }
    val lake = IntLayer(LayerId.BIOME, region, IntArray(64) { Biome.LAKE.ordinal })
    val surface = layer(LayerId.WATER_LEVEL) { x, _ -> if (x <= 3) 1002f else 1040f }
    val field = WorldMapField(bed, lake, surface, ice = null, seaLevel = 0.0)

    val (shallowX, shallowY) = centre(1, 4)
    val (deepX, deepY) = centre(6, 4)
    val shallow = field.rgbAt(shallowX, shallowY, field.valueAt(shallowX, shallowY))
    val deep = field.rgbAt(deepX, deepY, field.valueAt(deepX, deepY))

    assertNotEquals(shallow, deep)
    assertTrue(Colors.red(deep) < Colors.red(shallow), "the deep end should be the darker blue")
  }

  @Test
  fun `ice washes over whatever is under it without hiding it`() {
    val ground = layer(LayerId.ELEVATION) { _, _ -> 500f }
    val tundra = IntLayer(LayerId.BIOME, region, IntArray(64) { Biome.TUNDRA.ordinal })
    val ice = layer(LayerId.ICE_THICKNESS) { x, _ -> if (x <= 3) 0f else 400f }
    val field = WorldMapField(ground, tundra, water = null, ice = ice, seaLevel = 0.0)

    val bare = centre(1, 4).let { field.rgbAt(it.first, it.second, field.valueAt(it.first, it.second)) }
    val iced = centre(6, 4).let { field.rgbAt(it.first, it.second, field.valueAt(it.first, it.second)) }

    assertEquals(BiomePalette().rgb(Biome.TUNDRA.ordinal.toDouble()), bare)
    assertTrue(Colors.red(iced) > Colors.red(bare), "thick ice should lighten the ground under it")
    assertNotEquals(Colors.rgb(255, 255, 255), iced, "a wash must not become opaque white")
  }

  @Test
  fun `outside the raster is a hole rather than a clamped edge`() {
    // Same contract as every other field: a stage that wrote a smaller region than it claimed shows up as a
    // hole, not as smearing.
    assertTrue(map.valueAt(-500.0, 4000.0).isNaN())
    assertTrue(map.valueAt(4000.0, 40_000.0).isNaN())
  }

  private fun layer(id: LayerId, value: (Int, Int) -> Float) =
    FloatLayer(id, region, FloatArray(64)).apply {
      for (y in 0 until 8) for (x in 0 until 8) this[x, y] = value(x, y)
    }
}

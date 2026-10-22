package net.bestia.zone.cartography.habitat

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.zone.cartography.tile.TileId
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HabitatLayerTest {

  /** 2 km square of 64 m cells: ocean in the west half, grassland in the east. */
  private val biome = IntLayer(
    LayerId.BIOME,
    CellRegion(0, 0, CELLS, CELLS, Resolution(CELL_METRES)),
    IntArray(CELLS * CELLS) { i -> if (i % CELLS < CELLS / 2) Biome.OCEAN.ordinal else Biome.GRASSLAND.ordinal }
  )

  @Test
  fun `the haze is fixed per den and never centred on it`() {
    val centre = HabitatLayer.hazeCentre(SEED, denFeatureId = 5, speciesId = 1, denX = 1000.0, denY = 1000.0)
    val again = HabitatLayer.hazeCentre(SEED, denFeatureId = 5, speciesId = 1, denX = 1000.0, denY = 1000.0)
    val offset = hypot(centre.x - 1000.0, centre.y - 1000.0)

    assertEquals(centre, again)
    assertTrue(offset >= 0.4 * HabitatLayer.JITTER_METRES && offset <= HabitatLayer.JITTER_METRES, "offset $offset m")
  }

  @Test
  fun `the tint covers the habitat and nothing else`() {
    val layer = HabitatLayer(biome, setOf(Biome.GRASSLAND), emptyList())
    val image = layer.render(TileId(level = 3, tx = 0, ty = 0))

    assertTrue(alphaAt(image, x = 200, y = 128) > 0, "grassland in the east is tinted")
    assertEquals(0, alphaAt(image, x = 20, y = 128), "ocean in the west is not")
  }

  @Test
  fun `an empty habitat means any land, never water`() {
    val image = HabitatLayer(biome, emptySet(), emptyList()).render(TileId(level = 3, tx = 0, ty = 0))

    assertTrue(alphaAt(image, x = 200, y = 128) > 0)
    assertEquals(0, alphaAt(image, x = 20, y = 128))
  }

  @Test
  fun `the hottest point of the haze is not the den`() {
    val den = 1500.0 to 1000.0
    val centre = HabitatLayer.hazeCentre(SEED, denFeatureId = 9, speciesId = 1, denX = den.first, denY = den.second)
    val tile = TileId(level = 3, tx = 0, ty = 0)
    val image = HabitatLayer(biome, setOf(Biome.GRASSLAND), listOf(centre)).render(tile)

    val view = tile.viewport()
    var hottest = 0 to 0
    for (y in 0 until TileId.TILE_PIXELS) {
      for (x in 0 until TileId.TILE_PIXELS) {
        if (alphaAt(image, x, y) > alphaAt(image, hottest.first, hottest.second)) hottest = x to y
      }
    }
    val distance = hypot(view.worldX(hottest.first) - den.first, view.worldY(hottest.second) - den.second)

    assertTrue(distance > 0.3 * HabitatLayer.JITTER_METRES, "the haze peaks $distance m from the den")
  }

  private fun alphaAt(image: BufferedImage, x: Int, y: Int): Int {
    return image.getRGB(x, y) ushr 24
  }

  private companion object {
    const val CELLS = 32
    const val CELL_METRES = 64.0
    const val SEED = 42L
  }
}

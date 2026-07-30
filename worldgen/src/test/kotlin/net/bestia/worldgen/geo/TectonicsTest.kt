package net.bestia.worldgen.geo

import net.bestia.worldgen.core.WorldConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TectonicsTest {

  private val stage = TectonicsStage()

  private fun spacingFor(cells: Int): Double {
    val config = WorldConfig(seed = 1L, widthCells = cells, heightCells = cells)
    return stage.defaultSpacing(config, config.widthMetres, config.heightMetres)
  }

  @Test
  fun `the plate spacing of a reference world and larger is unchanged`() {
    // The guard on scaling the plate-spacing floor by detailScale. 512 km is the size every constant in every
    // stage was tuned at - "correct by construction", per WorldConfig - so a change that quietly gave it
    // twenty-five plates instead of five would rework the one world already known to be right.
    //
    // Safe because `detailScale` is `(512 km / shortEdge).coerceIn(1.0, 8.0)`, i.e. exactly 1.0 at and above
    // 512 km, so the floor is the unscaled constant there. Asserted rather than reasoned about, because the
    // reasoning lives in two files and the clamp is easy to widen by accident.
    assertEquals(512_000.0 / 5.0, spacingFor(512), 1e-6, "the 512 km reference world moved")
    assertEquals(700_000.0, spacingFor(4096), 1e-6, "a full-size world should sit on MAX_PLATE_SPACING")
  }

  @Test
  fun `a small world keeps its own spacing instead of being clamped up to a planet's`() {
    // What the change is for. A 128 km world wants 25.6 km and used to be forced to 50 km, which left it six
    // to nine plates, a cross-fade a fifth of the map wide, and a continental swell of 75 km - 1.7 lobes
    // across the whole world, which is a tilt rather than a landscape.
    assertEquals(25_600.0, spacingFor(128), 1e-6)
    assertEquals(51_200.0, spacingFor(256), 1e-6)
  }

  @Test
  fun `the floor still binds on a world too small to have plates at all`() {
    // detailScale caps at 8, so the floor bottoms out at 6.25 km rather than shrinking without limit. A 32 km
    // world would otherwise get 6.4 km plates, which is not a plate, it is a paving slab.
    val tiny = spacingFor(32)

    assertTrue(tiny >= 6_000.0, "a 32 km world got ${tiny.toInt()} m plates")
    assertTrue(tiny > 32_000.0 / 5.0 - 1.0, "the floor should still be binding at this size")
  }
}

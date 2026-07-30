package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The forced ocean margin, and the one property everything else about it rests on.
 */
class OceanBorderTest {

  private val config = WorldConfig(seed = 0xB02DE2L, widthCells = 128, heightCells = 128)
  private val region = CellRegion.world(128, 128, Resolution.KILOMETRE)

  private fun border(wobble: Double) = OceanBorder.of(
    config = config,
    depthBelowSeaLevel = 400.0,
    region = region,
    metresPerCell = 1000.0,
    gridWidth = region.width,
    wobbleMetres = wobble
  )

  private fun trueDistance(x: Double, y: Double) =
    min(min(x, config.widthMetres - x), min(y, config.heightMetres - y))

  @Test
  fun `the wobbled edge is never further from the edge than the real one`() {
    // The load-bearing property. `Invariants.checkOceanBorderIsOcean` measures the margin with the plain
    // rectangular distance, so as long as this one is never larger, everything inside the true margin is
    // still drowned - by construction, not by tuning. A future tweak that breaks this breaks the wrap seam,
    // and nothing else would look wrong until a player swam to the edge of the world.
    val wobbled = border(2_500.0)

    for (row in 0..200) {
      for (column in 0..200) {
        val x = column * config.widthMetres / 200.0
        val y = row * config.heightMetres / 200.0

        assertTrue(
          wobbled.distanceToEdge(x, y) <= trueDistance(x, y) + 1e-9,
          "at (${x.toInt()}, ${y.toInt()}) the wobbled distance exceeded the true one"
        )
      }
    }
  }

  @Test
  fun `the wrap seam is drowned to the same depth on both sides`() {
    // This, not periodicity of the wobble, is what makes the seam safe - and it is worth pinning, because the
    // wobble makes the margin's *outer* edge wander and it would be easy to assume the inner one wanders too.
    //
    // The x seam is at x = 0 = W, which is inside the west and east margins. Both sides are therefore held at
    // the margin floor whatever the terrain wanted to do there, so a player crossing swims from deep water
    // into deep water. That is also why the two edges need no shared wobble: there is nothing to match.
    val seaLevel = 0.0
    val wobbled = border(2_500.0)
    val floor = seaLevel - 400.0

    for (row in 4 until region.height - 4) {
      val west = row * region.width
      val east = row * region.width + region.width - 1

      assertEquals(floor, wobbled.heightAt(west, 2_000.0, seaLevel), 1e-9, "row $row west of the seam")
      assertEquals(floor, wobbled.heightAt(east, 2_000.0, seaLevel), 1e-9, "row $row east of the seam")
    }
  }

  @Test
  fun `the coastline actually wanders`() {
    // Without this, a wobble accidentally left at zero leaves every other test in this file green.
    val wobbled = border(2_500.0)

    val along = (0..400).map { wobbled.distanceToEdge(it * config.widthMetres / 400.0, 40_000.0) }
    val spread = along.max() - along.min()

    // Measured at ~2450 m of the 2500 available. Asserted well below that, because the exact figure is a
    // property of the noise and not worth pinning - what matters is that the coast uses most of its budget
    // rather than hovering near the middle of it, which is what a weak fbm would give.
    assertTrue(spread > 2_000.0, "the south edge only moved by ${spread.toInt()} m of a possible 2500")
  }

  @Test
  fun `no wobble gives the plain distance away from the corners, so the old behaviour is one parameter away`() {
    // Corner rounding is a separate concern from the wobble and stays on either way, so this holds along an
    // edge rather than everywhere - see the corner test below for the diagonal.
    val plain = border(0.0)

    for (at in doubleArrayOf(0.0, 1_000.0, 64_000.0, 127_000.0)) {
      assertEquals(trueDistance(at, 40_000.0), plain.distanceToEdge(at, 40_000.0), 1e-9, "at x=${at.toInt()}")
    }
  }

  @Test
  fun `the corners are rounded rather than creased`() {
    // A hard `min` of two edge distances creases along the diagonal, and four creases meeting at a right
    // angle is what makes the corner of a map look like the corner of a map. On the diagonal the smooth
    // minimum must come out strictly below both terms.
    val plain = border(0.0)
    val onDiagonal = 20_000.0

    assertTrue(
      plain.distanceToEdge(onDiagonal, onDiagonal) < onDiagonal - 100.0,
      "the corner was not rounded at all"
    )

    // ...and where one edge is clearly nearest, it must give that edge's distance, or the rounding is
    // leaking inland and quietly widening the margin everywhere. Here the north edge is 38 km away and
    // every other edge is 40 km or more, which is beyond the rounding scale.
    assertEquals(38_000.0, plain.distanceToEdge(40_000.0, 90_000.0), 1.0)
  }

  @Test
  fun `the margin drowns everything inside it, wobble or not`() {
    // The invariant restated as a unit test, so a failure names this file rather than a seed in the sweep.
    val seaLevel = 0.0
    val wobbled = border(2_500.0)

    for (i in 0 until region.cellCount.toInt()) {
      val x = (i % region.width + 0.5) * 1000.0
      val y = (i / region.width + 0.5) * 1000.0
      if (trueDistance(x, y) >= config.oceanBorderMetres) continue

      // A mountain, to make the point: whatever the natural height, the margin is water.
      assertTrue(
        wobbled.heightAt(i, 3_000.0, seaLevel) <= seaLevel - OceanBorder.SHELF_DEPTH + 1e-9,
        "a 3000 m peak survived inside the margin at (${x.toInt()}, ${y.toInt()})"
      )
    }
  }
}

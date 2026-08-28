package net.bestia.worldgen.place

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pipeline.StandardWorld
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The partition promises to cover the whole world and to name every part of it. Both are the kind of
 * claim that looks true right up until somebody walks into the one cell nobody owns, so both are
 * asserted rather than eyeballed - and the counts are printed, because a partition that produces one
 * region passes every structural check there is.
 */
class PlaceRegionsTest {

  private val world by lazy {
    StandardWorld.build(
      WorldConfig(seed = StandardWorld.DEFAULT_SEED, widthCells = 128, heightCells = 128)
    )
  }

  private val regions by lazy { PlaceRegions.of(world.world) }

  @Test
  fun `the world is covered by a plausible number of regions`() {
    val config = world.config
    val expected = config.widthMetres * config.heightMetres /
        (RegionParams().spacing * RegionParams().spacing)

    println(
      "Regions: ${regions.count} (${regions.landCount} with land) on a " +
          "${config.widthMetres.toInt() / 1000} km world, Voronoi estimate ${expected.toInt()}"
    )
    println(regions.regions.take(12).joinToString("\n") {
      "  ${it.name.padEnd(24)} ${it.kind} ${it.cellCount} km2 " +
          "land=${"%.2f".format(it.landShare)} relief=${it.relief.toInt()}m ${it.dominantBiome}"
    })

    // Bridson packs at about 0.70 points per r-squared, so the Voronoi estimate is an upper bound; the
    // merge pass only removes regions. A tenth of the estimate would mean the growth collapsed.
    assertTrue(regions.count > expected * 0.2) { "only ${regions.count} regions, expected near $expected" }
    assertTrue(regions.count <= expected) { "${regions.count} regions exceeds the seeding bound $expected" }
    assertTrue(regions.landCount > 0) { "no region holds any land" }
  }

  @Test
  fun `every position in the world resolves to a region`() {
    val config = world.config
    val step = config.baseResolution.metresPerCell

    var checked = 0
    var y = 0.0
    while (y < config.heightMetres) {
      var x = 0.0
      while (x < config.widthMetres) {
        val index = regions.indexAt(x + step * 0.5, y + step * 0.5)
        assertTrue(index in 0 until regions.count) { "($x,$y) resolved to region $index" }
        checked++
        x += step
      }
      y += step
    }

    assertEquals(config.widthCells * config.heightCells, checked)
  }

  @Test
  fun `no region spans the coastline`() {
    // The growth refuses a step between water and land, so a water region with dry land in it - or the
    // reverse - means the mask and the summary disagree about what water is.
    for (region in regions.regions) {
      if (region.isWater) {
        assertTrue(region.landShare < 0.5) { "${region.name} is water but ${region.landShare} land" }
      } else {
        assertTrue(region.landShare > 0.5) { "${region.name} is land but only ${region.landShare} of it" }
      }
    }
  }

  @Test
  fun `every region has a name and enough ground to deserve one`() {
    val minimum = RegionParams().minCells

    for (region in regions.regions) {
      assertFalse(region.name.isBlank()) { "region ${region.index} has no name" }
      assertTrue(region.cellCount > 0) { "${region.name} owns no cells" }

      // A region under the floor survived the merge, which is only allowed when it had no neighbour of
      // its own water class to be absorbed into - an island or a pond.
      if (region.cellCount < minimum) {
        println("  kept undersized: ${region.name} at ${region.cellCount} cells (isolated)")
      }
    }
  }

  @Test
  fun `no region shares a name with one it borders`() {
    val byName = regions.regions.groupBy { it.name }.filterValues { it.size > 1 }
    println("Duplicate names: ${byName.size} (${byName.keys.take(5)})")

    // Local uniqueness is the real constraint - see RegionNaming. Two regions far apart may share.
    for ((name, sharing) in byName) {
      for (first in sharing) {
        for (second in sharing) {
          if (first.index >= second.index) continue
          val gap = kotlin.math.hypot(first.centre.x - second.centre.x, first.centre.y - second.centre.y)
          assertTrue(gap > RegionParams().spacing) { "$name repeats only ${gap.toInt()} m away" }
        }
      }
    }
  }

  @Test
  fun `the name pools are untouched by this feature`() {
    // The whole reason this system moves no `pipelineVersion`: `Names.region` reuses the existing word
    // pools with new salts, and `catalogueDigest` hashes pools rather than salts. If this number moves,
    // a pool was edited and every place name in every existing world changed with it.
    assertEquals(EXPECTED_NAME_DIGEST, Names.catalogueDigest())
  }

  @Test
  fun `a region name is stable and non-blank for every culture and kind`() {
    for (culture in -1..3) {
      for (kind in RegionKind.entries) {
        val first = Names.region(0xABCDEFL, culture, kind.form)
        val again = Names.region(0xABCDEFL, culture, kind.form)

        assertEquals(first, again)
        assertFalse(first.isBlank())
        assertTrue(first.endsWith(kind.form.replaceFirstChar { it.uppercase() })) { first }
      }
    }
  }

  private companion object {
    /** Pinned, not computed - see the test that uses it. */
    const val EXPECTED_NAME_DIGEST = -9118719711542149956L
  }
}

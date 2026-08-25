package net.bestia.zone.world.fire

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceCover
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which ground carries a fire, checked against the cap table rather than against a generated world.
 *
 * `SurfaceBurnableGround` is three lookups and one arithmetic line over `SurfaceCover.cap`, `Biome.litter` and
 * `Biome.canopy`. Building a world to test it would measure the world, so this asks the same question of the
 * same inputs: **for every biome, would the ground it caps in burn, and does the ranking come out right.**
 *
 * The value that matters is not any single number - they are shaped rather than balanced - but the shape of
 * the answer across the biome list, and in particular the two entries that would be wrong under a plausible
 * simpler implementation.
 */
class BurnableGroundTest {

  /** The fuel arithmetic, given what the world would have said. Mirrors `SurfaceBurnableGround.fuelAt`. */
  private fun fuelOf(biome: Biome, temperature: Double = 15.0, blighted: Boolean = false): Double {
    val cap = SurfaceCover.cap(biome, temperature, 0.0, blighted)
    val capFuel = when (cap) {
      BlockType.DRY_GRASS -> 1.0
      BlockType.GRASS -> 0.8
      BlockType.BLIGHTED_GRASS -> 0.9
      else -> return 0.0
    }
    return (biome.litter * 1.2 * (1.0 - biome.canopy * 0.5) * capFuel).coerceIn(0.0, 1.0)
  }

  @Test
  fun `grassland burns well`() {
    assertTrue(fuelOf(Biome.GRASSLAND) > 0.5, "grassland fuel is ${fuelOf(Biome.GRASSLAND)}")
  }

  /**
   * **The bog case**, and the reason this test exists at all.
   *
   * Bog scores 0.85 on litter - higher than grassland - because peat is the *absence* of decay.
   * `BiomeForageGround`'s KDoc records that a naive litter threshold "would have quietly sent every herbivore
   * into the mires"; the same threshold would have set a swamp alight. It comes out zero here because bog caps
   * in MUD, with no exclusion list involved.
   */
  @Test
  fun `a bog does not burn despite outscoring grassland on litter`() {
    assertTrue(Biome.BOG.litter > Biome.GRASSLAND.litter, "the premise of this test no longer holds")
    assertEquals(0.0, fuelOf(Biome.BOG), "a bog caught fire")
  }

  @Test
  fun `ground with no cover does not burn`() {
    for (biome in listOf(Biome.DESERT, Biome.BEACH, Biome.BADLANDS, Biome.ICE_SHEET, Biome.OCEAN, Biome.LAKE)) {
      assertEquals(0.0, fuelOf(biome), "$biome burns")
    }
  }

  @Test
  fun `frozen ground does not burn`() {
    // Cold enough that the cap is snow whatever grows there in summer.
    assertEquals(0.0, fuelOf(Biome.TUNDRA, temperature = -20.0), "snow-capped tundra burns")
    assertEquals(0.0, fuelOf(Biome.TAIGA, temperature = -20.0), "snow-capped taiga burns")
  }

  /** Canopy damping, and the reason it is not the only term: a forest floor still burns. */
  @Test
  fun `an open grassland burns better than a closed forest floor`() {
    val grass = fuelOf(Biome.GRASSLAND)
    val forest = fuelOf(Biome.TEMPERATE_RAINFOREST)

    assertTrue(forest > 0.0, "a rainforest floor is completely fireproof")
    assertTrue(grass > forest, "grassland ($grass) does not outburn rainforest ($forest)")
  }

  /**
   * Counted output rather than a spot check - the habit `VegetationScatter`'s "not tuned by argument" note
   * insists on. Printed so the numbers are visible when they are next retuned.
   */
  @Test
  fun `the biome census reads sensibly`() {
    val ranked = Biome.entries
      .map { it to fuelOf(it) }
      .sortedByDescending { it.second }

    println("fuel by biome:")
    ranked.forEach { (biome, fuel) -> println("  %-28s %.3f".format(biome.name, fuel)) }

    val burning = ranked.filter { it.second > 0.0 }
    println("${burning.size} of ${Biome.entries.size} biomes carry a fire")

    // A world where almost nothing burns is a fire mechanic nobody meets, and one where almost everything does
    // is a world that burns down. Wide bounds: this is a tripwire, not a target.
    assertTrue(
      burning.size in 4..Biome.entries.size - 6,
      "${burning.size} of ${Biome.entries.size} biomes burn, which is not a plausible world"
    )
    assertTrue(
      ranked.first().second > 0.5,
      "the most flammable biome only reaches ${ranked.first().second}"
    )
  }
}

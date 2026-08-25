package net.bestia.zone.world.fire

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceCover
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which ground carries a fire.
 *
 * `SurfaceBurnableGround` reads the **surface block** from the materialiser and combines it with the biome's
 * litter and canopy. Building a world to test that would measure the world, so this asks the same arithmetic
 * of the same inputs, using `SurfaceCover.cap` to stand in for what the voxel pass would put on top of each
 * biome where nothing has been built.
 *
 * That substitution is exactly where the interesting cases are, and it is worth being explicit that it is a
 * substitution: `cap` does **not** know about roads, bridges or masonry, which is why the real implementation
 * stopped using it. Those are covered by the block table below rather than by a biome.
 */
class BurnableGroundTest {

  /** The fuel arithmetic over a surface block and a biome. Mirrors `SurfaceBurnableGround.fuelAt`. */
  private fun fuelOf(block: BlockType, biome: Biome): Double {
    val blockFuel = when (block) {
      BlockType.DRY_GRASS -> 1.0
      BlockType.GRASS -> 0.8
      BlockType.BLIGHTED_GRASS -> 0.9
      else -> return 0.0
    }
    return (biome.litter * 1.2 * (1.0 - biome.canopy * 0.5) * blockFuel).coerceIn(0.0, 1.0)
  }

  /** What the voxel pass would cap this biome with where nothing has been built on it. */
  private fun fuelOf(biome: Biome, temperature: Double = 15.0, blighted: Boolean = false): Double =
    fuelOf(SurfaceCover.cap(biome, temperature, 0.0, blighted), biome)

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
   * **The case that made the implementation change.** A street is cobblestone and a bridge masonry, both
   * stamped by the voxel pass into a cell whose *biome* is grassland - so a biome-only fuel function set fire
   * to a paved road. Reading the surface block is what closes it, and it closes bridges and mine collars with
   * the same line.
   */
  @Test
  fun `worked stone does not burn even in the middle of a grassland`() {
    assertTrue(fuelOf(BlockType.GRASS, Biome.GRASSLAND) > 0.0, "the premise: grassland burns")

    for (built in listOf(BlockType.COBBLESTONE, BlockType.MASONRY, BlockType.STONE, BlockType.GRAVEL)) {
      assertEquals(0.0, fuelOf(built, Biome.GRASSLAND), "$built burns in a grassland")
    }
  }

  @Test
  fun `standing water does not burn even in a riparian cell`() {
    assertTrue(Biome.RIPARIAN.litter > 0.5, "the premise: riparian scores well on litter")
    assertEquals(0.0, fuelOf(BlockType.WATER, Biome.RIPARIAN), "a river burns")
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

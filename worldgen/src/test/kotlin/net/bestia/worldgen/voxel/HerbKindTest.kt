package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HerbKindTest {

  private val params = GroundCoverParams()

  @Test
  fun `ordinary land grows wild herbs`() {
    assertEquals(HerbKind.WILD, herbAt(Biome.GRASSLAND, roll = 0.0))
  }

  @Test
  fun `each special land grows its own herb on part of its cells`() {
    assertEquals(HerbKind.MEDICINAL, herbAt(Biome.TEMPERATE_FOREST, roll = 0.1))
    assertEquals(HerbKind.FROST, herbAt(Biome.TAIGA, roll = 0.1))
    assertEquals(HerbKind.SUN, herbAt(Biome.DESERT, roll = 0.1))
    assertEquals(HerbKind.MANA, herbAt(Biome.GRASSLAND, roll = 0.1, mana = 0.7))
    assertEquals(HerbKind.GHOST, herbAt(Biome.GRASSLAND, roll = 0.1, blighted = true))
  }

  @Test
  fun `the rest of a special land still grows wild herbs`() {
    assertEquals(HerbKind.WILD, herbAt(Biome.TEMPERATE_FOREST, roll = 0.9))
    assertEquals(HerbKind.WILD, herbAt(Biome.GRASSLAND, roll = 0.9, blighted = true))
  }

  @Test
  fun `the rarer herb wins where lands overlap`() {
    assertEquals(HerbKind.GHOST, herbAt(Biome.TAIGA, roll = 0.1, mana = 0.9, blighted = true))
    assertEquals(HerbKind.MANA, herbAt(Biome.TAIGA, roll = 0.1, mana = 0.9))
  }

  @Test
  fun `average mana grows no mana herbs`() {
    assertEquals(HerbKind.WILD, herbAt(Biome.GRASSLAND, roll = 0.1, mana = 0.5))
  }

  private fun herbAt(biome: Biome, roll: Double, mana: Double = 0.5, blighted: Boolean = false): HerbKind {
    return HerbKind.of(blighted, mana, biome, roll, params)
  }
}

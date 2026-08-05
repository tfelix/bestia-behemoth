package net.bestia.zone.ecs.spawn

import net.bestia.worldgen.bio.Biome
import net.bestia.zone.bestia.Bestia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which species a den holds.
 *
 * The one part of the wild spawn path with a decision in it, and the part where a mistake is quietest: a join
 * that is too strict leaves the wilderness empty, and one that is too loose puts a level-100 boss in the
 * fields outside a starting village. Neither shows up in a log line that only counts dens.
 */
class WildSpeciesSelectionTest {

  private fun species(
    id: Long,
    level: Int,
    habitat: String = "",
    corruptedOnly: Boolean = false,
    boss: Boolean = false,
    weight: Int = 100
  ) = Bestia(
    id = id,
    identifier = "species_" + "x".repeat(id.toInt()),
    level = level,
    experienceReward = 1,
    health = 10,
    mana = 10,
    habitat = habitat,
    corruptedOnly = corruptedOnly,
    boss = boss,
    spawnWeight = weight
  )

  @Test
  fun `a species outside the den's level range is refused`() {
    val blob = species(1, level = 3)

    assertTrue(WildSpawnerService.fits(blob, 1, 7, Biome.GRASSLAND, corrupted = false, boss = false))
    assertTrue(!WildSpawnerService.fits(blob, 20, 30, Biome.GRASSLAND, corrupted = false, boss = false))
  }

  @Test
  fun `an empty habitat means any biome, and a listed one means only those`() {
    val anywhere = species(1, level = 5)
    val grassOnly = species(2, level = 5, habitat = "GRASSLAND,DRYLAND")

    assertTrue(WildSpawnerService.fits(anywhere, 1, 9, Biome.DESERT, corrupted = false, boss = false))
    assertTrue(WildSpawnerService.fits(grassOnly, 1, 9, Biome.DRYLAND, corrupted = false, boss = false))
    assertTrue(!WildSpawnerService.fits(grassOnly, 1, 9, Biome.DESERT, corrupted = false, boss = false))
  }

  @Test
  fun `a corrupted-only species never reaches clean ground`() {
    val blighted = species(1, level = 85, corruptedOnly = true)

    assertTrue(WildSpawnerService.fits(blighted, 80, 90, Biome.GRASSLAND, corrupted = true, boss = false))
    assertTrue(!WildSpawnerService.fits(blighted, 80, 90, Biome.GRASSLAND, corrupted = false, boss = false))
  }

  @Test
  fun `the boss flag has to match in both directions`() {
    val boss = species(1, level = 100, boss = true)
    val ordinary = species(2, level = 100)

    // A boss den takes the boss...
    assertTrue(WildSpawnerService.fits(boss, 100, 100, Biome.GRASSLAND, corrupted = true, boss = true))
    // ...and an ordinary den does not, which is what makes one-per-province mean anything.
    assertTrue(!WildSpawnerService.fits(boss, 100, 100, Biome.GRASSLAND, corrupted = true, boss = false))
    // And a boss den does not fall back to an ordinary level-100 pack.
    assertTrue(!WildSpawnerService.fits(ordinary, 100, 100, Biome.GRASSLAND, corrupted = true, boss = true))
  }

  @Test
  fun `a den nothing fits gets no species rather than a wrong one`() {
    val catalogue = listOf(species(1, level = 3), species(2, level = 100, boss = true))

    // The mid-level gap the shipped catalogue actually has: two species cannot cover a 1-to-100 ramp, and the
    // honest answer for a level-40 den is nothing at all.
    assertNull(
      WildSpawnerService.pick(catalogue, 1L, 1L, 36, 44, Biome.DRYLAND, corrupted = false, boss = false)
    )
  }

  @Test
  fun `the same den holds the same species on every boot`() {
    val catalogue = (1L..6L).map { species(it, level = 20) }

    val first = WildSpawnerService.pick(catalogue, 99L, 4242L, 16, 24, Biome.DRYLAND, false, false)
    val again = WildSpawnerService.pick(catalogue, 99L, 4242L, 16, 24, Biome.DRYLAND, false, false)

    assertNotNull(first)
    assertEquals(first!!.id, again!!.id)
  }

  @Test
  fun `a different den can hold a different species`() {
    // Not "must" - two dens drawing the same species is a legitimate outcome of a weighted draw. What is
    // asserted is that the draw depends on the den at all, which a constant would fail.
    val catalogue = (1L..6L).map { species(it, level = 20) }

    val drawn = (1L..40L)
      .mapNotNull { WildSpawnerService.pick(catalogue, 99L, it, 16, 24, Biome.DRYLAND, false, false)?.id }
      .toSet()

    assertTrue(drawn.size > 1, "every den drew the same species; the draw ignores the den")
  }

  @Test
  fun `weight decides the draw`() {
    val common = species(1, level = 20, weight = 950)
    val rare = species(2, level = 20, weight = 50)

    val picks = (1L..400L)
      .mapNotNull {
        WildSpawnerService.pick(listOf(common, rare), 7L, it, 16, 24, Biome.DRYLAND, false, false)?.id
      }

    val rareShare = picks.count { it == 2L }.toDouble() / picks.size
    assertTrue(rareShare < 0.25, "the rare species took $rareShare of the draws against a 0.05 weight")
  }
}

package net.bestia.zone.ecs.spawn

import net.bestia.worldgen.bio.Biome
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.ecs.spawn.WildSpawnerService.Candidate
import net.bestia.zone.ecs.spawn.WildSpawnerService.DenFacts
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
 *
 * The join is deliberately asymmetric now, and most of these cases exist to hold that shape in place: biome,
 * boss-ness, corruption and the event flag are **never** relaxed, the level band is relaxed when nothing
 * exact fits, and temperature is a weight rather than a gate.
 */
class WildSpeciesSelectionTest {

  private val config = WildSpawnConfig()

  private fun species(
    id: Long,
    level: Int,
    habitat: String = "",
    corruptedOnly: Boolean = false,
    boss: Boolean = false,
    weight: Int = 100,
    eventOnly: Boolean = false,
    temperatureMin: Double? = null,
    temperatureMax: Double? = null
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
    spawnWeight = weight,
    eventOnly = eventOnly,
    temperatureMinCelsius = temperatureMin,
    temperatureMaxCelsius = temperatureMax
  )

  private fun candidate(
    id: Long,
    level: Int,
    habitat: String = "",
    corruptedOnly: Boolean = false,
    boss: Boolean = false,
    weight: Int = 100,
    eventOnly: Boolean = false,
    temperatureMin: Double? = null,
    temperatureMax: Double? = null
  ) = Candidate(
    species(id, level, habitat, corruptedOnly, boss, weight, eventOnly, temperatureMin, temperatureMax)
  )

  private fun den(
    levelMin: Int,
    levelMax: Int,
    biome: Biome = Biome.GRASSLAND,
    corrupted: Boolean = false,
    boss: Boolean = false,
    temperature: Double = 15.0
  ) = DenFacts(levelMin, levelMax, biome, corrupted, boss, temperature)

  @Test
  fun `a species outside the den's level range is placed anyway, and says how far off it is`() {
    // This case used to assert the opposite, and that was the bug: a correct all-or-nothing join over a
    // catalogue of two species left 644 of 656 dens on the shipped world empty. A level-3 creature in a
    // level-25 den is a balance problem; an empty world is not a world.
    val blob = candidate(1, level = 3)

    assertEquals(0, WildSpawnerService.levelMissOf(blob, den(1, 7)))
    assertEquals(17, WildSpawnerService.levelMissOf(blob, den(20, 30)))
    assertTrue(WildSpawnerService.admits(blob, den(20, 30)), "the level band is not a hard constraint")

    val choice = WildSpawnerService.pick(listOf(blob), 1L, 1L, den(20, 30), config)
    assertEquals(1L, choice!!.species.id)
    assertEquals(17, choice.levelMiss)
  }

  @Test
  fun `an exact match displaces a fallback the moment it is added to the catalogue`() {
    // The property the whole tiering exists for: content added later takes over automatically, with no code
    // change and no re-tuning of anything.
    val blob = candidate(1, level = 3)
    val wolf = candidate(2, level = 25)
    val target = den(20, 30)

    assertEquals(1L, WildSpawnerService.pick(listOf(blob), 1L, 1L, target, config)!!.species.id)

    val upgraded = WildSpawnerService.pick(listOf(blob, wolf), 1L, 1L, target, config)!!
    assertEquals(2L, upgraded.species.id)
    assertEquals(0, upgraded.levelMiss, "an exact match must report no miss, or it logs as a fallback")
  }

  @Test
  fun `an empty habitat means any biome, and a listed one means only those`() {
    val anywhere = candidate(1, level = 5)
    val grassOnly = candidate(2, level = 5, habitat = "GRASSLAND,DRYLAND")

    assertTrue(WildSpawnerService.admits(anywhere, den(1, 9, Biome.DESERT)))
    assertTrue(WildSpawnerService.admits(grassOnly, den(1, 9, Biome.DRYLAND)))
    assertTrue(!WildSpawnerService.admits(grassOnly, den(1, 9, Biome.DESERT)))
  }

  @Test
  fun `the habitat is never relaxed, even when it leaves the den empty`() {
    // The line the fallback does not cross. A blob in a volcanic field is not a compromise between a full
    // world and an empty one - it is nonsense, and the empty den is the better answer.
    val blob = candidate(1, level = 3, habitat = "GRASSLAND")

    assertNull(WildSpawnerService.pick(listOf(blob), 1L, 1L, den(36, 44, Biome.VOLCANIC_FIELD), config))
  }

  @Test
  fun `a corrupted-only species never reaches clean ground`() {
    val blighted = candidate(1, level = 85, corruptedOnly = true)

    assertTrue(WildSpawnerService.admits(blighted, den(80, 90, corrupted = true)))
    assertTrue(!WildSpawnerService.admits(blighted, den(80, 90, corrupted = false)))
  }

  @Test
  fun `the boss flag has to match in both directions`() {
    val boss = candidate(1, level = 100, boss = true)
    val ordinary = candidate(2, level = 100)

    // A boss den takes the boss...
    assertTrue(WildSpawnerService.admits(boss, den(100, 100, corrupted = true, boss = true)))
    // ...and an ordinary den does not, which is what makes one-per-province mean anything.
    assertTrue(!WildSpawnerService.admits(boss, den(100, 100, corrupted = true, boss = false)))
    // And a boss den does not fall back to an ordinary level-100 pack.
    assertTrue(!WildSpawnerService.admits(ordinary, den(100, 100, corrupted = true, boss = true)))
  }

  @Test
  fun `an event-only species is refused by every kind of den`() {
    val raidBoss = candidate(1, level = 100, boss = true, eventOnly = true)
    val scripted = candidate(2, level = 10, eventOnly = true)

    assertTrue(!WildSpawnerService.admits(raidBoss, den(100, 100, corrupted = true, boss = true)))
    assertTrue(!WildSpawnerService.admits(scripted, den(1, 20)))
    // And it is refused even when it is the only thing that could possibly go there, which is the case an
    // "unspawnable by accident" implementation would get wrong.
    assertNull(WildSpawnerService.pick(listOf(scripted), 1L, 1L, den(1, 20), config))
  }

  @Test
  fun `a den nothing admits gets no species rather than a wrong one`() {
    // Biome, not level: the level gap is now filled by the fallback, so the only way to get an honest null
    // is for the country itself to suit nothing in the catalogue.
    val catalogue = listOf(
      candidate(1, level = 3, habitat = "GRASSLAND"),
      candidate(2, level = 100, boss = true)
    )

    assertNull(WildSpawnerService.pick(catalogue, 1L, 1L, den(36, 44, Biome.DESERT), config))
  }

  @Test
  fun `the same den holds the same species on every boot`() {
    val catalogue = (1L..6L).map { candidate(it, level = 20) }

    val first = WildSpawnerService.pick(catalogue, 99L, 4242L, den(16, 24, Biome.DRYLAND), config)
    val again = WildSpawnerService.pick(catalogue, 99L, 4242L, den(16, 24, Biome.DRYLAND), config)

    assertNotNull(first)
    assertEquals(first!!.species.id, again!!.species.id)
  }

  @Test
  fun `a different den can hold a different species`() {
    // Not "must" - two dens drawing the same species is a legitimate outcome of a weighted draw. What is
    // asserted is that the draw depends on the den at all, which a constant would fail.
    val catalogue = (1L..6L).map { candidate(it, level = 20) }

    val drawn = (1L..40L)
      .mapNotNull { WildSpawnerService.pick(catalogue, 99L, it, den(16, 24, Biome.DRYLAND), config)?.species?.id }
      .toSet()

    assertTrue(drawn.size > 1, "every den drew the same species; the draw ignores the den")
  }

  @Test
  fun `weight decides the draw`() {
    val common = candidate(1, level = 20, weight = 950)
    val rare = candidate(2, level = 20, weight = 50)

    val picks = (1L..400L)
      .mapNotNull {
        WildSpawnerService.pick(listOf(common, rare), 7L, it, den(16, 24, Biome.DRYLAND), config)?.species?.id
      }

    val rareShare = picks.count { it == 2L }.toDouble() / picks.size
    assertTrue(rareShare < 0.25, "the rare species took $rareShare of the draws against a 0.05 weight")
  }

  @Test
  fun `a species outside its temperature window is rarer but not absent`() {
    // Soft on purpose: this is the difference between "unusual here" and "mis-authored into oblivion".
    val comfortable = candidate(1, level = 20, temperatureMin = 10.0, temperatureMax = 20.0)
    val freezing = candidate(2, level = 20, temperatureMin = -30.0, temperatureMax = -10.0)

    val picks = (1L..400L).mapNotNull {
      WildSpawnerService.pick(
        listOf(comfortable, freezing), 7L, it, den(16, 24, temperature = 15.0), config
      )?.species?.id
    }

    val coldShare = picks.count { it == 2L }.toDouble() / picks.size
    assertTrue(coldShare in 0.0..0.20, "the out-of-window species took $coldShare of the draws")
    assertTrue(picks.any { it == 1L }, "the in-window species was never drawn")
  }

  @Test
  fun `a species with no temperature window is never penalised`() {
    val indifferent = candidate(1, level = 20)
    val fussy = candidate(2, level = 20, temperatureMin = 10.0, temperatureMax = 20.0)

    val anywhere = den(16, 24, temperature = 15.0)
    assertEquals(
      WildSpawnerService.weightOf(fussy, anywhere, config),
      WildSpawnerService.weightOf(indifferent, den(16, 24, temperature = -40.0), config)
    )
  }

  @Test
  fun `a zero minimum weight makes temperature effectively hard`() {
    val freezing = candidate(1, level = 20, temperatureMin = -30.0, temperatureMax = -10.0)
    val temperate = candidate(2, level = 20, temperatureMin = 10.0, temperatureMax = 20.0)
    val hard = config.copy(minTemperatureWeight = 0.0)

    val picks = (1L..200L).mapNotNull {
      WildSpawnerService.pick(listOf(freezing, temperate), 7L, it, den(16, 24, temperature = 15.0), hard)
        ?.species?.id
    }

    assertTrue(picks.none { it == 1L }, "a zero floor should have priced the out-of-window species out")
  }

  @Test
  fun `every candidate being out of window still fills the den`() {
    // The `total == 0` path. These species already passed the hard filter, so they do belong in this
    // country - they are merely all uncomfortable, and an empty den would be the wrong answer.
    val freezing = candidate(1, level = 20, temperatureMin = -30.0, temperatureMax = -10.0)
    val scorching = candidate(2, level = 20, temperatureMin = 40.0, temperatureMax = 60.0)
    val hard = config.copy(minTemperatureWeight = 0.0)

    val choice = WildSpawnerService.pick(listOf(freezing, scorching), 7L, 1L, den(16, 24, temperature = 15.0), hard)

    assertNotNull(choice, "a den with only uncomfortable candidates must still be stocked")
  }
}

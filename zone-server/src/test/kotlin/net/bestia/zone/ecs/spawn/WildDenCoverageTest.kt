package net.bestia.zone.ecs.spawn

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.spawn.SpawnerChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.bestia.Bestia
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * How much of the generator's level ramp the **shipped bestia catalogue** can actually fill.
 *
 * This is a content measurement wearing a test's clothes, and it is here because the alternative is finding
 * out by walking around an empty world. `worldgen` places on the order of a thousand dens spread over levels
 * 1 to 100; `resources/mob/` holds two species. So most dens necessarily go unstocked, and the number that do
 * is the size of the content gap - not a bug in the join, which `WildSpeciesSelectionTest` covers.
 *
 * The assertions are deliberately weak in one direction and firm in the other: **some** den must be fillable
 * (or the whole path is dead), and the boss dens must find the boss (or the endgame is). The coverage figure
 * itself is printed rather than asserted, because pinning it would make adding a mob a test failure.
 */
class WildDenCoverageTest {

  /** The two species that actually ship, transcribed from `resources/mob/`. */
  private val shipped = listOf(
    Bestia(
      id = 1,
      identifier = "blob",
      level = 3,
      experienceReward = 5,
      health = 10,
      mana = 8,
      habitat = "GRASSLAND,DRYLAND,RIPARIAN,BEACH,TEMPERATE_FOREST",
      spawnWeight = 100
    ),
    Bestia(
      id = 2,
      identifier = "doom_master_of_doom",
      level = 100,
      experienceReward = 12_000,
      health = 4_000,
      mana = 900,
      corruptedOnly = true,
      boss = true,
      spawnWeight = 1
    )
  )

  @Test
  fun `the shipped catalogue fills the dens it can, and the gap is reported`() {
    val world = StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))

    var dens = 0
    var stocked = 0
    var bossDens = 0
    var bossStocked = 0
    val unfilledByBand = IntArray(4)

    for (feature in world.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue
      dens++

      val levelMin = marker.attribute(SpawnerChannels.LEVEL_MIN).toInt()
      val levelMax = marker.attribute(SpawnerChannels.LEVEL_MAX).toInt()
      val biome = Biome.entries[marker.attribute(SpawnerChannels.BIOME).toInt()]
      val corrupted = marker.attribute(SpawnerChannels.CORRUPTION) >= 0.5
      val boss = marker.attribute(SpawnerChannels.BOSS) >= 0.5

      if (boss) bossDens++

      val picked = WildSpawnerService.pick(
        shipped, world.config.seed, marker.id.value, levelMin, levelMax, biome, corrupted, boss
      )

      if (picked != null) {
        stocked++
        if (boss) bossStocked++
      } else {
        val band = when {
          levelMax <= 8 -> 0
          levelMax <= 40 -> 1
          levelMax <= 79 -> 2
          else -> 3
        }
        unfilledByBand[band]++
      }
    }

    println(
      "wild dens: $dens total, $stocked stocked by the shipped catalogue " +
          "(${"%.1f".format(stocked * 100.0 / dens)}%), " +
          "unstocked by band 1-8/9-40/41-79/80-100 = ${unfilledByBand.joinToString("/")}, " +
          "boss dens $bossStocked/$bossDens"
    )

    assertTrue(dens > 0, "the generator produced no dens at all")
    assertTrue(stocked > 0, "not one den on the world can be stocked - the wild spawn path is dead")

    // The endgame is the one band two species *do* cover, so it must be complete. A boss den that finds no
    // boss would mean `corrupted-only` or the boss flag is refusing the species authored for it.
    if (bossDens > 0) {
      assertTrue(
        bossStocked == bossDens,
        "$bossStocked of $bossDens boss dens found a boss; the endgame species is being refused"
      )
    }
  }
}

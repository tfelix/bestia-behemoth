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
 * out by walking around an empty world. `worldgen` places tens of thousands of dens spread over levels 1 to
 * 100; `resources/mob/` holds two species.
 *
 * **What "unstocked" means changed with the tiered selection.** It used to mean "no species fits this den's
 * level band", which two species could never satisfy across a 1-to-100 ramp - 644 of 656 dens on the shipped
 * world. Now the level band is the constraint the fallback relaxes, so a den goes unstocked only when its
 * *biome* admits nothing at all. The number below is therefore a measure of habitat coverage, and the
 * interesting figure moved to the fallback count: how many dens hold a creature from the wrong part of the
 * ramp because nothing better exists yet.
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
    // The seed the dev server actually runs, so this measures the world somebody will walk around in.
    val world = StandardWorld.build(WorldConfig(seed = 11_753_242L, widthCells = 128, heightCells = 128))
    val config = WildSpawnConfig()
    val catalogue = shipped.map(WildSpawnerService::Candidate)

    var dens = 0
    var stocked = 0
    var fallbacks = 0
    var bossDens = 0
    var bossStocked = 0
    val unfilledByBand = IntArray(4)

    for (feature in world.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue
      dens++

      val levelMax = marker.attribute(SpawnerChannels.LEVEL_MAX).toInt()
      val boss = marker.attribute(SpawnerChannels.BOSS) >= 0.5
      if (boss) bossDens++

      val den = WildSpawnerService.DenFacts(
        levelMin = marker.attribute(SpawnerChannels.LEVEL_MIN).toInt(),
        levelMax = levelMax,
        biome = Biome.entries[marker.attribute(SpawnerChannels.BIOME).toInt()],
        corrupted = marker.attribute(SpawnerChannels.CORRUPTION) >= 0.5,
        boss = boss,
        temperature = marker.attribute(SpawnerChannels.TEMPERATURE)
      )

      val picked = WildSpawnerService.pick(catalogue, world.config.seed, marker.id.value, den, config)

      if (picked != null) {
        stocked++
        if (picked.levelMiss > 0) fallbacks++
        if (boss) bossStocked++
      } else {
        unfilledByBand[WildSpawnerService.bandIndexOf(levelMax)]++
      }
    }

    println(
      "wild dens: $dens total, $stocked stocked by the shipped catalogue " +
          "(${"%.1f".format(stocked * 100.0 / dens)}%), of which $fallbacks hold a species from outside " +
          "their own level band; unstocked by band 1-8/9-40/41-79/80-100 = " +
          "${unfilledByBand.joinToString("/")} (no species their biome admits), boss dens $bossStocked/$bossDens"
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

package net.bestia.worldgen.resource

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Every world holds every mineable ore.** The requirement, asserted per world rather than per sweep.
 *
 * This is the one resource test whose assertion is a floor on each individual world, and the distinction from
 * its two neighbours is worth stating because all three look alike. `GemDepositTest` and `VolcanicResourceTest`
 * assert on a *total across seeds* and deliberately run with the guarantee **switched off**, because what they
 * are testing is whether a suitability arm can be reached by the causal sampler at all - a question the floor
 * would answer for them and thereby hide. This one runs on the shipped tuning and asks the opposite question:
 * given everything the stage does, is there a seed that would hand a player a world with no tin in it.
 *
 * ### Why per world, and why the small sizes
 *
 * "Rare" and "absent" are different promises. A player who crosses a whole map and finds no iron has proved
 * something about the game rather than about their luck, and no aggregate over seeds can catch that - a sweep
 * total stays healthy while one world in ten is broken.
 *
 * The sizes are chosen for where the guarantee is actually under strain. A 192 km world satisfies this with no
 * help at all; 128 km is what `application.yml` boots and is where the sampler starts coming up short; 64 km is
 * past the point where the world has room for its own quota at the full dispersal distance, so it is the case
 * that exercises `ResourceParams.guaranteeSeparation`. Measured over forty-seven seeds at each of 64, 96, 128
 * and 192 km - one hundred and eighty-eight worlds - every one held all thirteen ores. The handful kept here is
 * the regression net; the sweep is in the commit that made it true.
 */
class OreCoverageTest {

  @Test
  fun `every world holds every ore`() {
    for ((label, world) in worlds) {
      val counts = countsOf(world)
      val tuning = world.params.resource.ore

      for (ore in MinableOre.entries) {
        val promised = tuning.floorOf(ore)
        if (promised <= 0) continue

        val found = counts[ore] ?: 0
        assertTrue(
          found >= promised,
          "$label has $found ${ore.name.lowercase()} deposits against the $promised every world is promised. " +
              "A player can cross this map and prove the ore is not in it. Before reaching for the floor, check " +
              "whether the ore's ground exists at all - `ResourceStage.guarantee` only ever places where " +
              "suitability is above zero, so a zero here can also mean the geology genuinely is not there"
        )
      }
    }
  }

  /**
   * No deposit is dropped closer than the stage's own hard floor, even by the relaxed top-up.
   *
   * The other half of `guaranteeSeparation`: it buys coverage by giving up dispersal, and this is what says how
   * much it is allowed to give up. `Invariants` checks the same thing on its own sweep; it is here as well
   * because this file is where the relaxation is exercised on purpose, and a regression would show up here
   * first.
   */
  @Test
  fun `nothing is placed closer than the guarantee's own floor`() {
    for ((label, world) in worlds) {
      val hard = world.params.resource.guaranteeSeparation
      val markers = depositsOf(world)

      for (i in markers.indices) {
        for (j in i + 1 until markers.size) {
          val distance = markers[i].position.distanceTo(markers[j].position)
          assertTrue(
            distance >= hard - 1.0,
            "$label: two deposits are ${distance.toInt()} m apart, inside the ${hard.toInt()} m " +
                "guaranteeSeparation the top-up is not allowed to go under"
          )
        }
      }
    }
  }

  /**
   * The relaxed pass is a last resort, not the mechanism.
   *
   * Without this, "every world has every ore" could be satisfied by abandoning dispersal altogether and piling
   * thirteen mines into one valley - which is the promise kept and the reason for it thrown away. Measured, a
   * 128 km world puts at most four deposits inside the target and a 192 km world none at all; the bound below
   * is what the design can account for, which is twice the sum of the floors.
   */
  @Test
  fun `the dispersal target is bent rarely and never abandoned`() {
    for ((label, world) in worlds) {
      val target = world.params.resource.oreSeparation
      val markers = depositsOf(world)
      val involved = HashSet<Int>()

      for (i in markers.indices) {
        for (j in i + 1 until markers.size) {
          if (markers[i].position.distanceTo(markers[j].position) < target) {
            involved.add(i)
            involved.add(j)
          }
        }
      }

      val allowed = 2 * MinableOre.entries.sumOf { world.params.resource.ore.floorOf(it) }
      assertTrue(
        involved.size <= allowed,
        "$label: ${involved.size} of ${markers.size} deposits stand inside the ${target.toInt()} m dispersal " +
            "target, more than the $allowed the guaranteed top-up could account for"
      )
    }
  }

  private fun countsOf(world: GeneratedWorld): Map<MinableOre, Int> {
    val counts = HashMap<MinableOre, Int>()
    for (marker in depositsOf(world)) {
      val ore = MinableOre.of(ResourceType.entries[marker.attribute(DepositChannels.TYPE).toInt()]) ?: continue
      counts[ore] = (counts[ore] ?: 0) + 1
    }
    return counts
  }

  private fun depositsOf(world: GeneratedWorld) = world.world.features.all()
    .filter { it.kind == FeatureKind.ORE_DEPOSIT }
    .filterIsInstance<PointMarker>()
    .filter { MinableOre.of(ResourceType.entries[it.attribute(DepositChannels.TYPE).toInt()]) != null }

  private companion object {

    /**
     * The shipped size and the strained one.
     *
     * Six seeds at 128 km because that is the world `application.yml` boots, and three at 64 km because that is
     * where the relaxed pass has to fire - at 64 km the map has room for about twenty-eight deposits at the
     * full 12 km spacing and the floors alone ask for twenty-nine, so the two rules cannot both be satisfied
     * and the test is that coverage is the one that wins.
     */
    val worlds: List<Pair<String, GeneratedWorld>> by lazy {
      val big = listOf(1L, 3L, 7L, 11L, 42L, 0xC0FFEEL).map { "seed $it at 128 km" to build(it, 128) }
      val small = listOf(2L, 11L, 26L).map { "seed $it at 64 km" to build(it, 64) }
      big + small
    }

    fun build(seed: Long, cells: Int) = StandardWorld.build(
      WorldConfig(seed = seed, widthCells = cells, heightCells = cells, chunkSize = 32, voxelSize = 1.0)
    )
  }
}

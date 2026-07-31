package net.bestia.worldgen.bio

import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Top-2 biome classification: the runner-up's identity, and the dither that consumes it.
 *
 * The classifier always computed the runner-up's *score* - that is where `BIOME_CONFIDENCE` comes from - and
 * threw away which prototype it belonged to, so a consumer could tell a cell was in a transition but not what
 * between. The subtle part of keeping it is that the demotion has to happen in the branch where the winner
 * *changes*, not only in the branch for prototypes that never led; getting that wrong loses the runner-up on
 * most of the map rather than on none of it, which is the kind of bug a spot check passes.
 */
class BiomeBlendTest {

  private companion object {
    val world: GeneratedWorld = StandardWorld.build(
      StandardWorld.demoConfig(seed = StandardWorld.DEFAULT_SEED).copy(widthCells = 192, heightCells = 192)
    )
  }

  private fun sampleAt(vararg axes: Pair<Int, Double>): DoubleArray {
    // Mid-range on every axis, then the caller's overrides. Mid-range rather than zero because zero is a
    // corner of the space and every prototype is roughly equidistant from it.
    val sample = DoubleArray(BiomeAxis.COUNT) { 0.5 }
    for ((axis, value) in axes) sample[axis] = value
    return sample
  }

  // --- The classifier ---------------------------------------------------------------------------------

  @Test
  fun `a prototype centre classifies as itself with high confidence`() {
    for (prototype in Biomes.CLIMATIC) {
      val match = Biomes.classify(prototype.at.copyOf())

      assertEquals(
        prototype.biome, match.biome,
        "the centre of ${prototype.biome} does not classify as ${prototype.biome}"
      )
      assertTrue(
        match.confidence > 0.5,
        "${prototype.biome} at its own centre has confidence ${match.confidence}"
      )
      // Distance zero means the runner-up is whatever is nearest, but there must *be* one: 14 prototypes
      // cannot leave a cell with no second choice.
      assertNotNull(match.runnerUp, "${prototype.biome} at its own centre reports no runner-up")
      assertTrue(
        match.runnerUp != match.biome,
        "${prototype.biome} is its own runner-up"
      )
    }
  }

  /** The top two prototypes by weighted distance, found the slow obvious way. */
  private fun bruteForceTopTwo(sample: DoubleArray): Pair<Biome, Biome> {
    val ranked = Biomes.CLIMATIC
      .map { prototype ->
        var sum = 0.0
        for (axis in 0 until BiomeAxis.COUNT) {
          val delta = sample[axis] - prototype.at[axis]
          sum += prototype.weight[axis] * delta * delta
        }
        prototype.biome to sum
      }
      .sortedBy { it.second }

    return ranked[0].first to ranked[1].first
  }

  @Test
  fun `the winner and runner-up agree with a brute-force ranking everywhere`() {
    // The strongest statement of correctness, and the one that depends on no geometric assumption at all: the
    // incremental single-pass loop must agree with sorting every prototype by distance. It covers the
    // winner-changed branch without having to construct a case that exercises it.
    var checked = 0

    // A deterministic lattice-free walk through the unit cube, so the samples are spread over the whole
    // classification space rather than clustered near prototype centres.
    var state = 0x9E3779B97F4A7C15uL
    fun next(): Double {
      state = state * 6364136223846793005uL + 1442695040888963407uL
      return ((state shr 11).toLong().toDouble() / (1L shl 53).toDouble())
    }

    repeat(20_000) {
      val sample = DoubleArray(BiomeAxis.COUNT) { next() }
      val match = Biomes.classify(sample)
      val (expectedBest, expectedSecond) = bruteForceTopTwo(sample)

      assertEquals(expectedBest, match.biome, "winner disagrees at ${sample.toList()}")
      assertEquals(expectedSecond, match.runnerUp, "runner-up disagrees at ${sample.toList()}")
      checked++
    }

    assertTrue(checked == 20_000)
  }

  @Test
  fun `at a classification boundary the pair is the two biomes either side of it`() {
    // The property a blend actually needs, stated at a boundary located by bisection rather than assumed.
    //
    // Two earlier attempts were wrong about the geometry rather than about the code, and both are worth
    // recording. Asserting it at the *midpoint* between two prototype centres fails because weights are
    // per-prototype - ALPINE weights elevation at 4.5 - so the unweighted midpoint is nobody's midpoint under
    // the actual metric: measured, at only 30% of the 91 midpoints is the winner even one of the two. Walking
    // the segment in fixed steps then fails too, because a heavily weighted prototype can overtake *two*
    // others inside one step: stepping DESERT->ALPINE, the winner went STEPPE -> ALPINE while the runner-up
    // before the crossing was GRASSLAND, and all three claims were true.
    //
    // Bisecting to where the top two are within rounding of each other removes the step size from the
    // argument. There the two scores are equal, so the biome on each side must be the other's runner-up.
    var crossings = 0

    for (a in Biomes.CLIMATIC.indices) {
      for (b in a + 1 until Biomes.CLIMATIC.size) {
        val from = Biomes.CLIMATIC[a]
        val to = Biomes.CLIMATIC[b]

        fun at(t: Double) = DoubleArray(BiomeAxis.COUNT) { from.at[it] + (to.at[it] - from.at[it]) * t }

        var low = 0.0
        var high = 1.0
        val startsAs = Biomes.classify(at(0.0)).biome
        if (Biomes.classify(at(1.0)).biome == startsAs) continue

        // Bisect to the first change of winner. 60 halvings takes the bracket below double precision.
        repeat(60) {
          val mid = (low + high) / 2.0
          if (Biomes.classify(at(mid)).biome == startsAs) low = mid else high = mid
        }

        val below = Biomes.classify(at(low))
        val above = Biomes.classify(at(high))
        if (below.biome == above.biome) continue
        crossings++

        assertEquals(
          above.biome, below.runnerUp,
          "at the ${below.biome}/${above.biome} boundary along ${from.biome}->${to.biome}, " +
              "the runner-up below it is ${below.runnerUp}"
        )
        // The direction that fails when the demotion is missing from the winner-changed branch.
        assertEquals(
          below.biome, above.runnerUp,
          "at the ${below.biome}/${above.biome} boundary along ${from.biome}->${to.biome}, " +
              "the runner-up above it is ${above.runnerUp}"
        )
      }
    }

    assertTrue(crossings > 40, "only $crossings boundary crossings were found, so this asserts little")
  }

  @Test
  fun `the runner-up survives the winner changing`() {
    // The bug the TODO warns about, stated directly. CLIMATIC's first entry is ICE_SHEET, which leads until
    // something beats it, so a warm wet sample changes the winner several times on the way through the list.
    // If `secondBest = best` is missing from the winner-changed branch, the runner-up here comes back as
    // whatever never led rather than as the biome that was displaced last.
    val warmWet = sampleAt(
      BiomeAxis.TEMPERATURE to 0.84,
      BiomeAxis.PRECIPITATION to 0.92,
      BiomeAxis.SEASONALITY to 0.14,
      BiomeAxis.TEMPERATURE_RANGE to 0.12,
      BiomeAxis.ELEVATION to 0.08,
      BiomeAxis.SLOPE to 0.20,
      BiomeAxis.WETNESS to 0.90
    )
    val match = Biomes.classify(warmWet)

    assertEquals(Biome.TROPICAL_RAINFOREST, match.biome)
    assertNotNull(match.runnerUp)
    assertTrue(match.runnerUp != Biome.ICE_SHEET, "the runner-up is the list's first entry, which never won")

    // And the runner-up really is the second nearest, checked against a brute-force ranking rather than
    // against an expectation about which biome it ought to be.
    val ranked = Biomes.CLIMATIC
      .map { prototype ->
        var sum = 0.0
        for (axis in 0 until BiomeAxis.COUNT) {
          val delta = warmWet[axis] - prototype.at[axis]
          sum += prototype.weight[axis] * delta * delta
        }
        prototype.biome to sum
      }
      .sortedBy { it.second }

    assertEquals(ranked[0].first, match.biome, "the winner is not the nearest prototype")
    assertEquals(ranked[1].first, match.runnerUp, "the runner-up is not the second nearest prototype")
  }

  @Test
  fun `a single prototype has no runner-up`() {
    // Where the sentinel comes from. One prototype means no second choice at all, and reporting the winner as
    // its own runner-up would make a blend between a biome and itself.
    val only = listOf(Biomes.CLIMATIC.first())
    val match = Biomes.classify(sampleAt(), only)

    assertEquals(only.first().biome, match.biome)
    assertNull(match.runnerUp, "a single-prototype classification invented a runner-up")
    assertEquals(1.0, match.confidence, 1e-12, "a single prototype should be fully confident")
  }

  @Test
  fun `the runner-up is order-stable across repeated classification`() {
    // The dither is a pure function of world position and the layers, so the layers themselves must be a pure
    // function of the sample. A tie broken by iteration order would still be stable; one broken by anything
    // else would not.
    val sample = sampleAt(BiomeAxis.TEMPERATURE to 0.55, BiomeAxis.PRECIPITATION to 0.40)
    val first = Biomes.classify(sample)

    repeat(8) {
      val again = Biomes.classify(sample.copyOf())
      assertEquals(first.biome, again.biome)
      assertEquals(first.runnerUp, again.runnerUp)
      assertEquals(first.confidence, again.confidence, 1e-15)
    }
  }

  // --- The layer -------------------------------------------------------------------------------------

  @Test
  fun `the secondary layer holds real biomes or the sentinel and nothing else`() {
    val secondary: IntLayer = world.world.layers.require(LayerId.BIOME_SECONDARY)

    for (ordinal in secondary.data) {
      if (ordinal == LayerId.NO_SECONDARY) continue
      assertNotNull(
        Biome.entries.getOrNull(ordinal),
        "BIOME_SECONDARY holds $ordinal, which is neither a biome ordinal nor the sentinel"
      )
    }
  }

  @Test
  fun `the secondary layer is populated across most of the world`() {
    val secondary: IntLayer = world.world.layers.require(LayerId.BIOME_SECONDARY)
    val withRunnerUp = secondary.data.count { it != LayerId.NO_SECONDARY }
    val share = withRunnerUp.toDouble() / secondary.data.size

    // The sentinel is for overridden cells - water, beach, cliff, riparian, wetland - which are a real share of
    // the world but nowhere near all of it. A tiny share here would mean the classifier's runner-up is being
    // lost; a share of 1.0 would mean the override policy is not writing the sentinel at all.
    assertTrue(
      share > 0.3,
      "only ${"%.1f".format(Locale.ROOT, 100 * share)}% of cells have a runner-up"
    )
    assertTrue(
      share < 1.0,
      "every cell has a runner-up, so overridden cells are not getting the sentinel"
    )
  }

  @Test
  fun `an overridden cell has the sentinel and full confidence together`() {
    val biome: IntLayer = world.world.layers.require(LayerId.BIOME)
    val secondary: IntLayer = world.world.layers.require(LayerId.BIOME_SECONDARY)
    val confidence = world.world.layers.require<net.bestia.worldgen.core.FloatLayer>(LayerId.BIOME_CONFIDENCE)

    // The two encodings have to agree, because the dither reads them as a pair: a sentinel with a low
    // confidence would be a cell asking to be blended with nothing.
    var checked = 0
    for (i in biome.data.indices) {
      if (secondary.data[i] != LayerId.NO_SECONDARY) continue
      checked++
      assertEquals(
        1.0f, confidence.data[i],
        "cell $i has no runner-up but a confidence of ${confidence.data[i]}"
      )
    }

    assertTrue(checked > 0, "no cell in the world carries the sentinel, so this asserts nothing")
  }

  // --- The edge biomes are still the edge biomes -----------------------------------------------------

  @Test
  fun `water cells are unchanged by the blend`() {
    // The dither must never turn an ocean cell into something walkable. Water is an override, so it carries
    // the sentinel and full confidence, and the dither's own guard should make it unreachable.
    val biome: IntLayer = world.world.layers.require(LayerId.BIOME)
    val secondary: IntLayer = world.world.layers.require(LayerId.BIOME_SECONDARY)

    var water = 0
    for (i in biome.data.indices) {
      val kind = Biome.entries.getOrNull(biome.data[i]) ?: continue
      if (!kind.isWater) continue
      water++
      assertEquals(
        LayerId.NO_SECONDARY, secondary.data[i],
        "a $kind cell has runner-up ${Biome.entries.getOrNull(secondary.data[i])}"
      )
    }

    assertTrue(water > 0, "the world has no water, so this asserts nothing")
  }
}

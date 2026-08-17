package net.bestia.worldgen.resource

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.OreBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sulfur, obsidian and pyrelith: that they are placed at all, and that where they are placed is checkable.
 *
 * ### The sweep is the point, not a nicety, and so is [RawGeology]
 *
 * These worlds are built with the guaranteed deposit floor **switched off**, and without that this file would
 * be worthless. Sulfur and pyrelith each have a floor under them on the shipped tuning, so on the real defaults
 * every world holds both whatever `suitabilityFor` says - which means an arm that never fires anywhere would
 * pass here in silence. [RawGeology] explains the split; `OreCoverageTest` makes the other half of the claim.
 *
 * With the floor off, a world can legitimately come out with no pyrelith - it needs the strongest volcanism
 * there is - so a single-seed test still cannot tell "correctly absent" from "never placed anywhere". The sweep
 * asserts on the **total across seeds**, which is the only quantity that separates the two. It is
 * `ManaHistoryTest`'s shape and it is here for the same reason.
 *
 * ### And the placement claim
 *
 * `ResourceStage.suitabilityFor`'s KDoc argues that the point of causal placement is falsifiability - "copper
 * should appear along volcanic arcs and nowhere else". The cheap robust form of that for these three is: no
 * volcanic deposit stands where volcanism is zero. It needs no constructed seed and it fails loudly if the
 * suitability ever comes to be keyed on something else.
 */
class VolcanicResourceTest {

  @Test
  fun `the volcanic resources are placed somewhere, on some world`() {
    val totals = HashMap<ResourceType, Int>()
    val perSeed = ArrayList<String>()

    for ((seed, world) in SEEDS.zip(worlds)) {
      val counts = HashMap<ResourceType, Int>()
      for (deposit in depositsOf(world)) {
        val type = ResourceType.entries[deposit.attribute(DepositChannels.TYPE).toInt()]
        if (type !in VOLCANIC) continue
        counts[type] = (counts[type] ?: 0) + 1
        totals[type] = (totals[type] ?: 0) + 1
      }

      perSeed.add("seed $seed: " + VOLCANIC.joinToString(" ") { "${it.label}=${counts[it] ?: 0}" })
    }

    perSeed.forEach(::println)
    println("totals: " + VOLCANIC.joinToString(" ") { "${it.label}=${totals[it] ?: 0}" })

    for (type in VOLCANIC) {
      assertTrue(
        (totals[type] ?: 0) > 0,
        "no ${type.label} deposit on any of ${SEEDS.size} worlds, with the guaranteed floor off. Its " +
            "suitability is unreachable, or its spacing has compounded with it - and do NOT fix this by " +
            "raising its floor, which is exactly the thing this sweep switches off in order to see past"
      )
    }
  }

  @Test
  fun `a volcanic deposit stands on volcanic ground`() {
    var checked = 0

    // Over the whole sweep rather than one world, since the worlds are built once and shared. Every volcanic
    // deposit in every world has to satisfy this, and a single seed would check a handful.
    for (world in worlds) {
    val volcanism = world.world.layers.require<FloatLayer>(LayerId.VOLCANISM)

    for (deposit in depositsOf(world)) {
      val type = ResourceType.entries[deposit.attribute(DepositChannels.TYPE).toInt()]
      if (type !in VOLCANIC) continue
      checked++

      val here = volcanism.sampleBilinear(deposit.position.x, deposit.position.y)

      // Above zero, not above each type's own suitability floor. The floors are smoothsteps and a deposit sits
      // where the *candidate sampler* offered one, so a marker can legitimately land a little below its own ramp's
      // lower edge; "volcanism is not zero here" is the claim the placement actually makes and the one whose
      // failure would mean the suitability had stopped reading this layer.
      assertTrue(
        here > 0.0,
        "a ${type.label} deposit at (${deposit.position.x.toInt()}, ${deposit.position.y.toInt()}) stands on " +
            "ground with no volcanism at all"
      )
    }
    }

    assertTrue(checked > 0, "the sweep found no volcanic deposits to check")
  }

  @Test
  fun `obsidian is quarried, not assayed`() {
    // The `MARBLE` shape, and the geology rather than a simplification: obsidian is a massive glassy carapace,
    // so there is nothing for a grade to be a grade *of*.
    assertNull(
      OreBlocks.blocksFor(ResourceType.OBSIDIAN),
      "obsidian is not a graded ore, so it must have no grade triple"
    )
    assertEquals(BlockType.OBSIDIAN, OreBlocks.plainBlockFor(ResourceType.OBSIDIAN))
    assertNull(MinableOre.of(ResourceType.OBSIDIAN), "obsidian has no orebody, so it is not a MinableOre")
    assertTrue(!OreBlocks.isOre(BlockType.OBSIDIAN), "a quarried block is not ore")
  }

  @Test
  fun `sulfur and pyrelith round-trip through the block palette`() {
    // Both directions for all six blocks. A one-way check passes on a `GRADED` entry that names the wrong
    // triple, which is a copy-paste away in a table of ten.
    for (type in listOf(ResourceType.SULFUR, ResourceType.PYRELITH)) {
      val blocks = OreBlocks.blocksFor(type) ?: error("$type should be a graded ore")
      assertEquals(OreGrade.entries.size, blocks.size)

      for ((index, grade) in OreGrade.entries.withIndex()) {
        val block = OreBlocks.blockFor(type, grade) ?: error("no block for $type $grade")
        assertEquals(blocks[index], block, "blocksFor and blockFor disagree about $type $grade")
        assertTrue(OreBlocks.isOre(block), "$block should read as ore")

        val back = OreBlocks.yieldOf(block) ?: error("$block does not name what it yields")
        assertEquals(type, back.resource, "$block yields the wrong resource")
        assertEquals(grade, back.grade, "$block yields the wrong grade")
      }
    }
  }

  @Test
  fun `a sulfur mine names sulfur`() {
    // Free proof that `SpecialSites.mines` needed no edit: it reads any ORE_DEPOSIT marker, so a sulfur mine and
    // its MINE_OPENED chronicle line come with the resource and nothing else. Over the sweep rather than one
    // seed, because whether history opens a mine on a *particular* deposit is a roll.
    val named = HashMap<ResourceType, Int>()

    for (world in worlds) {
      for (site in world.world.features.all().filterIsInstance<PointMarker>()) {
        if (site.kind != FeatureKind.MINE) continue
        val resource = runCatching { site.attribute(SiteChannels.RESOURCE).toInt() }.getOrNull() ?: continue
        val type = ResourceType.entries.getOrNull(resource) ?: continue
        named[type] = (named[type] ?: 0) + 1
      }
    }

    println("mines by resource: " + named.entries.sortedByDescending { it.value }
      .joinToString(" ") { "${it.key.label}=${it.value}" })

    assertTrue(named.isNotEmpty(), "history opened no mine at all across the sweep")
    assertTrue(
      named.keys.any { it in VOLCANIC },
      "no mine on any volcanic resource across ${SEEDS.size} worlds, though ${named.values.sum()} mines opened"
    )
  }

  /**
   * The sweep worlds, built once for the whole class.
   *
   * Three of the five tests need the sweep and building it per test would triple the cost - which is what
   * decided the seed count, since a robust total needs more seeds than one world's worth of luck.
   */
  private val worlds: List<GeneratedWorld> by lazy {
    SEEDS.map {
      StandardWorld.build(
        WorldConfig(seed = it, widthCells = CELLS, heightCells = CELLS, chunkSize = 32, voxelSize = 1.0),
        params = RawGeology.PARAMS
      )
    }
  }

  private fun depositsOf(world: GeneratedWorld) = world.world.features.all()
    .filter { it.kind == FeatureKind.ORE_DEPOSIT }
    .filterIsInstance<PointMarker>()

  private companion object {
    val VOLCANIC = listOf(ResourceType.SULFUR, ResourceType.OBSIDIAN, ResourceType.PYRELITH)

    /**
     * 192 cells, which is `Invariants`' own sweep size.
     *
     * Large enough to reliably contain a convergent boundary *and* a hotspot chain, and small enough that four
     * of them build in a few seconds. Pyrelith needs the strongest volcanism there is, so a smaller world is
     * where it legitimately comes out absent - which is the case the sweep exists to distinguish, not to hide.
     */
    const val CELLS = 192

    /**
     * Six, and the count was decided by what the sweep measured rather than picked.
     *
     * Deposits found per seed at the tuning this landed on, which is the baseline a future retune should be read
     * against:
     *
     * ```
     *   seed        1    3    7   11   42  C0FFEE   total   worlds with none
     *   sulfur      1    3    1    4    1    1       11      0 of 6
     *   obsidian    0    0    1    0    1    4        6      3 of 6
     *   pyrelith    1    0    2    0    2    3        8      2 of 6
     * ```
     *
     * Four seeds was not enough: at four, obsidian totalled **one** deposit and pyrelith two, and a total of one
     * is a test that passes by luck rather than one that measures reachability. Both of those numbers were also
     * what found the two tuning errors this commit fixes - obsidian's spacing compounding with its own strict
     * suitability, and pyrelith's hardness term having the wrong sign.
     */
    val SEEDS = listOf(1L, 3L, 7L, 11L, 42L, 0xC0FFEEL)
  }
}

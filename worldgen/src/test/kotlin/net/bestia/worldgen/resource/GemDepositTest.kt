package net.bestia.worldgen.resource

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.OreBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The gems reach the ground, on some world.
 *
 * `VolcanicResourceTest`'s shape and for its reason, which the module states as habit 6: **a subsystem that is
 * complete, tested and never reached looks exactly like one that works.** Three of these four gems are
 * `guaranteed = false`, so nothing anywhere else in the suite would notice if a suitability arm were written so
 * tightly that no world ever satisfied it - every per-seed test would pass by finding nothing and asserting
 * nothing. Only a count says otherwise.
 *
 * ### Why a total across seeds and not a per-seed floor
 *
 * A rare gem is *meant* to be absent from some worlds, which is exactly what makes "this seed has none" an
 * unusable assertion - it cannot tell correct absence from a dead code path. The total over a sweep can: at
 * these abundances, zero across every seed is not rarity, it is unreachability.
 *
 * [AMETHYST] is the exception and is asserted per seed, because it is the one gem with a floor under it. If it
 * is ever missing from a single world, `ResourceStage`'s guarantee has stopped working.
 */
class GemDepositTest {

  @Test
  fun `every gem is placed somewhere, on some world`() {
    val totals = HashMap<ResourceType, Int>()
    val perSeed = ArrayList<String>()

    for ((seed, world) in SEEDS.zip(worlds)) {
      val counts = HashMap<ResourceType, Int>()
      for (deposit in depositsOf(world)) {
        val type = ResourceType.entries[deposit.attribute(DepositChannels.TYPE).toInt()]
        if (type !in GEMS) continue
        counts[type] = (counts[type] ?: 0) + 1
        totals[type] = (totals[type] ?: 0) + 1
      }

      perSeed.add("seed $seed: " + GEMS.joinToString(" ") { "${it.label}=${counts[it] ?: 0}" })

      assertTrue(
        (counts[ResourceType.AMETHYST] ?: 0) > 0,
        "seed $seed has no amethyst, and amethyst is the one gem every world is promised - see " +
            "MinableOre.guaranteed on why the other three opted out and this one did not"
      )
    }

    // Printed unconditionally: the numbers are the point, and a future retune wants to read them against the
    // baseline in [SEEDS]' own note rather than against a pass or a fail.
    perSeed.forEach(::println)
    println("totals: " + GEMS.joinToString(" ") { "${it.label}=${totals[it] ?: 0}" })

    for (type in GEMS) {
      assertTrue(
        (totals[type] ?: 0) > 0,
        "no ${type.label} deposit on any of ${SEEDS.size} worlds. Its suitability arm in " +
            "ResourceStage.suitabilityFor is unreachable, or its spacing has compounded with it - and do NOT " +
            "fix it by making it guaranteed, which would paper over an arm that never fires"
      )
    }
  }

  /**
   * Every gem deposit sits in the depth band its own entry declares.
   *
   * Cheap, and it is the assertion that catches a gem wired to the wrong `MinableOre` row - the failure mode a
   * table of thirteen entries with six numbers each actually has.
   */
  @Test
  fun `every gem deposit sits at a depth its own ore allows`() {
    var checked = 0

    for (world in worlds) {
      for (deposit in depositsOf(world)) {
        val type = ResourceType.entries[deposit.attribute(DepositChannels.TYPE).toInt()]
        if (type !in GEMS) continue

        val ore = assertNotNull(MinableOre.of(type), "${type.label} is a gem with no MinableOre row")
        val depth = deposit.attribute(DepositChannels.DEPTH)
        checked++

        assertTrue(
          depth >= ore.minDepth - 0.001 && depth <= ore.maxDepth + 0.001,
          "a ${type.label} deposit is $depth m down, outside ${ore.minDepth}..${ore.maxDepth}"
        )
      }
    }

    assertTrue(checked > 0, "the sweep found no gem deposits to check")
  }

  /**
   * Every gem round-trips through the block palette, in both directions.
   *
   * `VolcanicResourceTest` makes this check for sulfur and pyrelith and gives the reason: a one-way check
   * passes on a `GRADED` entry that names the wrong triple, which is one copy-paste away in a table that now
   * holds fifteen of them.
   */
  @Test
  fun `every gem round-trips through the block palette`() {
    val seen = HashSet<BlockType>()

    for (type in GEMS) {
      val blocks = assertNotNull(OreBlocks.blocksFor(type), "${type.label} has no grade triple")
      assertEquals(3, blocks.size, "${type.label} does not have three grades")

      for (block in blocks) {
        assertTrue(seen.add(block), "$block is claimed by more than one gem")
        assertTrue(block.name.startsWith("GEM_"), "$block is a gem's block but is not named like one")
        assertEquals(type, OreBlocks.yieldOf(block)?.resource, "$block does not name ${type.label} back")
      }

      // Contiguous and ascending, which `viewer/Palette.graded` and the client's own table both index on.
      assertEquals(
        listOf(blocks[0].id, blocks[0].id + 1, blocks[0].id + 2),
        blocks.map { it.id },
        "${type.label}'s grade blocks are not three consecutive ids"
      )
    }
  }

  private fun depositsOf(world: GeneratedWorld) = world.world.features.all()
    .filter { it.kind == FeatureKind.ORE_DEPOSIT }
    .filterIsInstance<PointMarker>()

  private companion object {
    val GEMS = listOf(
      ResourceType.AMETHYST, ResourceType.EMERALD, ResourceType.RUBY, ResourceType.DIAMOND
    )

    /** 192 cells, `Invariants`' own sweep size and `VolcanicResourceTest`'s, for that file's reasons. */
    const val CELLS = 192

    /**
     * The same six seeds `VolcanicResourceTest` sweeps, deliberately.
     *
     * Two sweeps over one set of seeds is two sets of numbers that can be read side by side - a retune that
     * moves the gems and not the volcanics is a different kind of change from one that moves both.
     *
     * Deposits found per seed at the tuning this landed on, which is the baseline a future retune should be
     * read against:
     *
     * ```
     *   seed        1    3    7   11   42  C0FFEE   total   worlds with none
     *   amethyst    3    3    3    3    3    3       18      0 of 6
     *   emerald     1    1    3    0    1    1        7      1 of 6
     *   ruby        2    0    1    2    1    2        8      1 of 6
     *   diamond     3    1    0    0    0    1        5      3 of 6
     * ```
     *
     * Amethyst is flat threes because it is the guaranteed one and the floor is what it is getting - three
     * deposits is `ResourceParams.minDepositsPerOre` exactly, so on a 192 km world the sampler is placing none
     * of it and the top-up is placing all of it. Worth knowing rather than worth fixing: it is the shallowest
     * and commonest gem by design, and a bigger world samples it properly.
     *
     * Diamond is the one to watch, and every number in its row was earned. It read **zero across all six**
     * twice over: once from an elevation clause written for a world whose land sits below 1200 m (this one's
     * median is 1400), and once from a plain `1 - arc` that is a veto rather than a preference at these world
     * sizes. Both are recorded in `ResourceStage.suitabilityFor`. A row of five is thin but real; a row of one,
     * which is what the first fix produced, is a test that passes by luck - `VolcanicResourceTest` says so in
     * its own baseline note and it was right here too.
     */
    val SEEDS = listOf(1L, 3L, 7L, 11L, 42L, 0xC0FFEEL)

    val worlds: List<GeneratedWorld> by lazy {
      SEEDS.map {
        StandardWorld.build(
          WorldConfig(seed = it, widthCells = CELLS, heightCells = CELLS, chunkSize = 32, voxelSize = 1.0)
        )
      }
    }
  }
}

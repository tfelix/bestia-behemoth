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
 * The gems reach the ground **by geology**, on some world.
 *
 * `VolcanicResourceTest`'s shape and for its reason, which the module states as habit 6: **a subsystem that is
 * complete, tested and never reached looks exactly like one that works.** Nothing anywhere else in the suite
 * would notice if a suitability arm were written so tightly that no world ever satisfied it - every per-seed
 * test would pass by finding nothing and asserting nothing. Only a count says otherwise.
 *
 * ### The worlds here are built with the guaranteed floor switched off, and that is the whole design
 *
 * Every mineable ore now has a floor under it - see `MinableOre.guaranteedDeposits` - and a floor is precisely
 * the thing that would make this file pass while telling you nothing. A gem whose arm never fires anywhere
 * still gets its guaranteed deposit on every world, so on the shipped tuning "at least one ruby exists" is a
 * statement about the top-up and not about ruby. Turning the floor off restores the question this file was
 * written to ask: **can the causal sampler find this gem's ground at all.** `OreCoverageTest` asks the other
 * question, on the shipped tuning, and asserts per world rather than per sweep.
 *
 * ### Why a total across seeds and not a per-seed floor
 *
 * With the guarantee off, a rare gem is *meant* to be absent from some worlds, which is exactly what makes
 * "this seed has none" an unusable assertion - it cannot tell correct absence from a dead code path. The total
 * over a sweep can: at these abundances, zero across every seed is not rarity, it is unreachability.
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
    }

    // Printed unconditionally: the numbers are the point, and a future retune wants to read them against the
    // baseline in [SEEDS]' own note rather than against a pass or a fail.
    perSeed.forEach(::println)
    println("totals: " + GEMS.joinToString(" ") { "${it.label}=${totals[it] ?: 0}" })

    for (type in GEMS) {
      assertTrue(
        (totals[type] ?: 0) > 0,
        "no ${type.label} deposit on any of ${SEEDS.size} worlds, with the guaranteed floor off. Its " +
            "suitability arm in ResourceStage.suitabilityFor is unreachable, or its spacing has compounded " +
            "with it - and do NOT fix it by raising its floor, which would paper over an arm that never fires"
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
     * Deposits the **sampler alone** finds per seed, with the guaranteed floor off, which is the baseline a
     * future retune should be read against:
     *
     * ```
     *   seed        1    3    7   11   42  C0FFEE   total   worlds with none
     *   amethyst    3    3    3    3    3    3       18      0 of 6
     *   emerald     1    1    1    0    1    1        5      1 of 6
     *   ruby        4    3    9    5    9    5       35      0 of 6
     *   diamond     2    1    3    0    4    1       11      1 of 6
     * ```
     *
     * The zeroes in that table are the point of it and are not a defect: on the **shipped** tuning every one of
     * those worlds holds all four gems, because the floor fills them in, and `OreCoverageTest` is what asserts
     * so. What this table measures is the half the floor would otherwise hide.
     *
     * Diamond is the one to watch, and every number in its row was earned. It read **zero across all six**
     * twice over: once from an elevation clause written for a world whose land sits below 1200 m (this one's
     * median is 1400), and once from a plain `1 - arc` that is a veto rather than a preference at these world
     * sizes. Both are recorded in `ResourceStage.suitabilityFor`. The first fix produced a total of *one*,
     * which is a test that passes by luck rather than one that measures reachability -
     * `VolcanicResourceTest` says so in its own baseline note and it was right here too.
     *
     * The current row comes from widening both gems deliberately: ruby's and diamond's suitability ceilings
     * and age bands were loosened and their `spacingFactor`s cut, so the sampler *looks* in more places. Ruby
     * went 8 to 35 and diamond 5 to 11. Emerald drifted 7 to 5 on the same change and was not touched: it
     * picks after ruby in the dispersal order and now loses a little ground to it, which is what a shared
     * dispersal pass is supposed to do.
     */
    val SEEDS = listOf(1L, 3L, 7L, 11L, 42L, 0xC0FFEEL)

    val worlds: List<GeneratedWorld> by lazy {
      SEEDS.map {
        StandardWorld.build(
          WorldConfig(seed = it, widthCells = CELLS, heightCells = CELLS, chunkSize = 32, voxelSize = 1.0),
          params = RawGeology.PARAMS
        )
      }
    }
  }
}

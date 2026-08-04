package net.bestia.zone.world

import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * That the lore query reaches real data, end to end, with no caller.
 *
 * **This is the point of the test rather than a bonus.** `SettlementLoreService` is deliberately the data half of
 * a feature whose delivery half does not exist - no NPC, no dialogue system, nothing that speaks - so without a
 * test it is a service nobody has ever run, which is the exact failure `ExposureConfig.comfortHighCelsius`
 * documented for two releases while looking perfectly healthy.
 *
 * Run against the **pure** entry point in the companion rather than through Spring, which is why that split
 * exists: a test that had to stand up the container and a database to check a lookup is a test nobody runs.
 *
 * ### Why it is a sweep
 *
 * "A town remembers an eruption" needs a volcano near a town *and* an eruption in the eleven hundred years the
 * simulation runs, and both are rolls. On any one seed the answer can legitimately be nothing, so a per-seed
 * assertion would either flake or be pinned to a lucky seed - and neither distinguishes a rare thing working from
 * a dead one. `VolcanicHistoryTest` in `worldgen` makes the same argument about the same rolls one tier down.
 */
class SettlementLoreTest {

  @Test
  fun `some town on some world remembers an eruption`() {
    var withLore = 0
    var withEruption = 0
    var seedsWithEruption = 0

    for (seed in SEEDS) {
      val world = StandardWorld.build(WorldConfig(seed = seed, widthCells = CELLS, heightCells = CELLS))
      val positions = SettlementLoreService.settlementPositions(world)
      val chronicle = world.world.chronicle

      var lore = 0
      var eruptions = 0
      for (record in chronicle.settlements) {
        if (record.isRuin) continue

        val memories = SettlementLoreService.loreOf(world, record.index, positions = positions)
        if (memories.isEmpty()) continue
        lore++

        // The eruption is the one that has to arrive through the *nearby* half of the query: it happened to a
        // mountain and carries no settlement actor, so `eventsOf` alone would never find it.
        val erupted = memories.filter { it.kind == EventKind.ERUPTION }
        if (erupted.isEmpty()) continue
        eruptions++

        assertTrue(
          erupted.all { it.nearby },
          "settlement ${record.index} on seed $seed claims an eruption as its own event; an eruption happens " +
              "to a mountain and must arrive as nearby news"
        )

        // One sample per seed, printed. This is the fastest read on whether the query would return anything worth
        // an NPC saying - the assertions can only check that prose exists, not that it is any good.
        if (eruptions == 1) {
          println("  e.g. town ${record.index} remembers, in year ${erupted.first().year}:")
          for (memory in memories.take(3)) {
            println("    ${if (memory.nearby) "nearby " else "its own"} y${memory.year} ${memory.detail}")
          }
        }
      }

      withLore += lore
      withEruption += eruptions
      if (eruptions > 0) seedsWithEruption++
      println("seed $seed: $lore standing towns with lore, $eruptions of them remembering an eruption")
    }

    println("totals: $withLore towns with lore, $withEruption remembering an eruption")

    assertTrue(withLore > 0, "no standing settlement on any world returned any lore at all")
    assertTrue(
      seedsWithEruption > 0,
      "no town on any of ${SEEDS.size} worlds remembers an eruption. The nearby half of the query is the only " +
          "way one can, so either it is broken or HistorySim stopped putting eruptions at vents"
    )
  }

  @Test
  fun `the prose is prose, not actor indices`() {
    // `HistoryEvent.detail` is stored pre-rendered so a reader needs no second copy of every name. If it were
    // ever built from actor indices instead, this is what it would look like - and the failure would be invisible
    // until an NPC said "settlement#12 is buried in ash and stands empty" to a player.
    val world = StandardWorld.build(WorldConfig(seed = SEEDS.first(), widthCells = CELLS, heightCells = CELLS))
    val positions = SettlementLoreService.settlementPositions(world)

    var checked = 0
    for (record in world.world.chronicle.settlements) {
      if (record.isRuin) continue
      for (memory in SettlementLoreService.loreOf(world, record.index, positions = positions)) {
        checked++
        assertTrue(memory.detail.isNotBlank(), "an event's detail is blank, so there is nothing to say")
        assertTrue(
          '#' !in memory.detail,
          "the detail '${memory.detail}' contains '#', which is what an actor index looks like"
        )
        assertTrue(
          "index" !in memory.detail.lowercase(),
          "the detail '${memory.detail}' names an index rather than a place"
        )
      }
    }

    assertTrue(checked > 0, "no lore to check the prose of")
  }

  @Test
  fun `an unknown settlement asks for nothing and gets nothing`() {
    // The bounds check, which matters because the index comes from outside: a caller holding a stale index from a
    // previous world must get an empty list rather than an exception on a live server.
    val world = StandardWorld.build(WorldConfig(seed = SEEDS.first(), widthCells = CELLS, heightCells = CELLS))

    assertTrue(SettlementLoreService.loreOf(world, -1).isEmpty())
    assertTrue(SettlementLoreService.loreOf(world, world.world.chronicle.settlements.size).isEmpty())
    assertTrue(SettlementLoreService.loreOf(world, Int.MAX_VALUE).isEmpty())
  }

  private companion object {
    /** `VolcanicHistoryTest`'s size, and for its reason: 128 rarely puts a town within ash reach of a vent. */
    const val CELLS = 256

    val SEEDS = listOf(1L, 3L, 42L)
  }
}

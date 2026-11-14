package net.bestia.zone.townsfolk.knowledge

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.zone.geometry.Compass
import net.bestia.zone.townsfolk.knowledge.LegendKnowledge.Truth
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.ReadsLargeWorlds
import net.bestia.zone.world.SettlementLoreService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ReadsLargeWorlds
class LegendKnowledgeTest {

  @Test
  fun `most towns tell a legend true, some twist it and a few tell a myth`() {
    val told = allTold()
    val shares = Truth.entries.associateWith { truth -> told.count { it.truth == truth } / told.size.toDouble() }
    println("${told.size} legends told across ${SEEDS.size} worlds: $shares")

    assertTrue(told.size >= MIN_TOLD, "only ${told.size} legends told on ${SEEDS.size} worlds")
    assertTrue(shares.getValue(Truth.TRUE) in 0.5..0.7, "true share ${shares.getValue(Truth.TRUE)}")
    assertTrue(shares.getValue(Truth.MYTH) > 0.0, "no town tells a myth")
  }

  @Test
  fun `a true legend points from the nearest standing town to the place, and a myth points away`() {
    for ((world, told) in toldByWorld()) {
      val positions = SettlementLoreService.settlementPositions(world)

      for (legend in told) {
        val anchor = positions.getValue(legend.anchor)
        val toPlace = Compass.octantOf(legend.subject.position.x - anchor.x, legend.subject.position.y - anchor.y)
        val off = Math.floorMod(legend.heading - toPlace, Compass.POINTS).let { minOf(it, Compass.POINTS - it) }

        when (legend.truth) {
          Truth.TRUE -> assertEquals(0, off, "a true ${legend.subject.kind} points $off points off")
          Truth.DISTORTED -> assertEquals(1, off, "a twisted ${legend.subject.kind} points $off points off")
          Truth.MYTH -> assertTrue(off >= 3, "a myth about a ${legend.subject.kind} points only $off points off")
        }
      }
    }
  }

  @Test
  fun `every slot a legend kind declares is one its legends carry`() {
    val catalogue = HistoryLineCatalogue().also { it.load() }

    for (legend in allTold()) {
      val missing = catalogue.of(legend.subject.kind).slots - legend.knowledge.slots.keys
      assertTrue(missing.isEmpty(), "${legend.subject.kind} declares $missing but its legends do not carry them")
    }
  }

  @Test
  fun `every legend is spoken as an old saying`() {
    assertTrue(allTold().all { it.knowledge.certainty == Certainty.LEGEND })
  }

  private fun allTold(): List<LegendKnowledge.Told> {
    return toldByWorld().flatMap { it.second }
  }

  private fun toldByWorld(): List<Pair<GeneratedWorld, List<LegendKnowledge.Told>>> {
    return SEEDS.map { seed ->
      val world = GeneratedWorlds.of(seed)
      val chronicle = world.world.chronicle
      val positions = SettlementLoreService.settlementPositions(world)
      val told = positions.keys
        .filter { chronicle.settlementStood(it, chronicle.presentYear) }
        .flatMap { LegendKnowledge.toldAt(chronicle, it, seed, positions) }

      world to told
    }
  }

  private companion object {
    val SEEDS = GeneratedWorlds.SEEDS

    /** Enough tellings that the shares mean something. */
    const val MIN_TOLD = 50
  }
}

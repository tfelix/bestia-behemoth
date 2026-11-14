package net.bestia.zone.townsfolk.knowledge

import net.bestia.worldgen.pop.EconomyProbe
import net.bestia.worldgen.pop.Households
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.ReadsLargeWorlds
import net.bestia.zone.world.SettlementLoreService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ReadsLargeWorlds
class LineageClaimsTest {

  @Test
  fun `a claim to a house is held by exactly one family in town`() {
    var claims = 0

    for (seed in GeneratedWorlds.SEEDS) {
      val world = GeneratedWorlds.of(seed)
      val positions = SettlementLoreService.settlementPositions(world)

      for (record in world.world.chronicle.settlements) {
        if (record.isRuin || !record.wasFounded) continue
        val summary = EconomyProbe.summaryFor(world.world, record.index) ?: continue
        if (summary.householdCount <= 0) continue

        val town = TownKnowledge.of(
          generated = world,
          settlement = record.index,
          summary = summary,
          worldSeed = seed,
          householdAt = { Households.one(summary, it) },
          profileOf = { KnowledgeProfile.ORDINARY },
          positions = positions,
        )
        val holders = town.holderCounts()

        for (claim in town.all().filter { it.key == LegendKind.LINEAGE.key }) {
          claims++
          assertEquals(1, holders[claim.topic], "seed $seed town ${record.index}: ${claim.slots} has ${holders[claim.topic]} claimants")
          assertEquals(Certainty.TOLD, claim.certainty)
        }
      }
    }

    println("$claims claims to a house across ${GeneratedWorlds.SEEDS.size} worlds")
    assertTrue(claims > 0, "no family anywhere claims an old house")
  }

  @Test
  fun `every slot the lineage phrasing declares is one a claim carries`() {
    val declared = HistoryLineCatalogue().also { it.load() }.of(LegendKind.LINEAGE).slots

    for (seed in GeneratedWorlds.SEEDS) {
      val world = GeneratedWorlds.of(seed)
      val chronicle = world.world.chronicle
      val positions = SettlementLoreService.settlementPositions(world)

      for (settlement in positions.keys) {
        for (claim in LineageClaims.at(chronicle, settlement, seed, positions)) {
          val missing = declared - claim.slots.keys
          assertTrue(missing.isEmpty(), "a claim on seed $seed lacks $missing")
        }
      }
    }
  }
}

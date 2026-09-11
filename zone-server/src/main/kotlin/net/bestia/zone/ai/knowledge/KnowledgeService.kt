package net.bestia.zone.ai.knowledge

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.Households
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.spawn.townsfolk.HouseholdPlacement
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkIdentity
import net.bestia.zone.world.SettlementLoreService
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * What each townsperson knows, built the first time somebody asks about their settlement.
 *
 * Lazy per town for `TownsfolkRoster`'s reason: a world holds hundreds of settlements and a player is
 * standing in one of them, so the one they walked into is the only one worth the memory. Tick-thread
 * only, like the roster and the site index it sits beside - the maps are plain and unsynchronised
 * because there is never a second thread to race against.
 */
@Service
class KnowledgeService(
  private val worldService: WorldService,
  private val sites: SettlementSiteIndex,
  private val placement: HouseholdPlacement,
) {

  private val bySettlement = HashMap<Int, TownKnowledge>()
  private var positions: Map<Int, Vec2d>? = null

  /** What the person behind a `Townsfolk` component can be told about. */
  fun knownBy(identity: Long): List<Knowledge> {
    val town = of(TownsfolkIdentity.settlementOf(identity))

    return town.heldBy(TownsfolkIdentity.householdOf(identity))
  }

  fun of(settlement: Int): TownKnowledge {
    bySettlement[settlement]?.let { return it }

    val built = build(settlement)
    bySettlement[settlement] = built

    return built
  }

  /** For a settlement whose people have changed under it, and for tooling. */
  fun forget(settlement: Int) {
    bySettlement.remove(settlement)
  }

  /**
   * Dropped rather than left to go stale, because a settlement index means a different town on a new
   * seed - which is the difference between an empty cache and a village that remembers somebody else's
   * history.
   */
  @EventListener
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    bySettlement.clear()
    positions = null

    LOG.info { "World replaced; dropped what every town knew" }
  }

  private fun build(settlement: Int): TownKnowledge {
    val summary = sites.siteOf(settlement)?.population ?: return EMPTY

    return TownKnowledge.of(
      generated = worldService.generated,
      settlement = settlement,
      summary = summary,
      worldSeed = worldService.record.seed,
      householdAt = { index -> Households.one(summary, index) },
      profileOf = { household -> profileOf(household) },
      positions = positionsOf(),
    )
  }

  /**
   * A household's trade, as a weighting.
   *
   * Through the head, because the household's business is what the head keeps - and because a knowledge
   * profile describes what a *house* hears, which is one thing whoever answers the door.
   */
  private fun profileOf(household: Household): KnowledgeProfile {
    return placement.occupationFor(household, household.head).knowledge
  }

  private fun positionsOf(): Map<Int, Vec2d> {
    positions?.let { return it }

    val built = SettlementLoreService.settlementPositions(worldService.generated)
    positions = built

    return built
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private val EMPTY = TownKnowledge(emptyList(), emptyMap())
  }
}

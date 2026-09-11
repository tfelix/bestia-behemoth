package net.bestia.zone.ai.knowledge

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.Households
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ai.rumour.RumourLineCatalogue
import net.bestia.zone.ai.rumour.RumourRegistry
import net.bestia.zone.ecs.spawn.townsfolk.HouseholdPlacement
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkIdentity
import net.bestia.zone.environment.time.BestiaClock
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
  private val lines: HistoryLineCatalogue,
  private val rumours: RumourRegistry,
  private val rumourLines: RumourLineCatalogue,
  private val clock: BestiaClock,
) {

  private val bySettlement = HashMap<Int, Cached>()
  private var positions: Map<Int, Vec2d>? = null

  /** What the person behind a `Townsfolk` component can be told about. */
  fun knownBy(identity: Long): List<Knowledge> {
    val town = of(TownsfolkIdentity.settlementOf(identity))

    return town.heldBy(TownsfolkIdentity.householdOf(identity))
  }

  /**
   * What this town knows today.
   *
   * The day matters only where there is news: a chronicle memory is as true this morning as it was a
   * century ago, but a rumour's importance decays, and its share of the town decays with it. So a town
   * holding news is rebuilt once a day and every other town is built once ever - which is almost all of
   * them, almost always.
   */
  fun of(settlement: Int): TownKnowledge {
    val today = clock.now().absoluteDay.toLong()

    bySettlement[settlement]?.let { cached ->
      if (!cached.decays || cached.builtOnDay == today) {
        return cached.knowledge
      }
    }

    val built = build(settlement)
    bySettlement[settlement] = Cached(built, today, rumours.heardBy(settlement).isNotEmpty())

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
      variantsOf = { kind -> lines.of(kind).variants },
      rumours = recentNewsIn(settlement),
      positions = positionsOf(),
    )
  }

  /**
   * What this town has heard lately, as memories.
   *
   * Spent news is dropped here rather than only in the sweep: the sweep is what deletes the row, and
   * nothing guarantees it has run today. A rumour worth nothing would otherwise still take a
   * conversation slot from something a player might want.
   */
  private fun recentNewsIn(settlement: Int): List<Knowledge> {
    val day = clock.now().absoluteDay
    val presentYear = worldService.generated.world.chronicle.presentYear

    return rumours.heardBy(settlement)
      .filterNot { it.hasExpired(day) }
      .map { it.toKnowledge(day, presentYear, rumourLines.of(it.kind).variants) }
  }

  private class Cached(
    val knowledge: TownKnowledge,
    val builtOnDay: Long,
    /** Whether anything in here ages. False for a town with no news, which is nearly every town. */
    val decays: Boolean,
  )

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

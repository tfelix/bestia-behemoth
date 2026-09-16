package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * Everybody who lives in a settlement, and where each of them would be at a given hour.
 *
 * ### Why a roster exists at all
 *
 * Deciding who to materialise means asking, of every inhabitant, "would you be near this player right
 * now" - and the answer depends on the hour, so it cannot be precomputed once. What *can* be precomputed
 * is everything the answer is made of: who lives here, what they do, where they live and where they work.
 * None of that changes, so it is expanded once per settlement and kept.
 *
 * ### And why that does not undo the level of detail
 *
 * A roster is built the first time somebody asks about a settlement and not before, exactly as
 * [SettlementSiteIndex] builds a site. A world of three hundred towns costs nothing until a player walks
 * into one of them, and the one they walk into is precisely the one worth the memory.
 */
@Service
class TownsfolkRoster(
  private val sites: SettlementSiteIndex,
  private val placement: HouseholdPlacement,
) {

  /**
   * One inhabitant, with everything about them that does not depend on the clock.
   *
   * @param home the doorstep of the house, which is also where they go at night
   * @param workplace null for somebody with nowhere in particular to be
   */
  class Resident(
    val identity: Long,
    val occupation: Occupation,
    val home: Vec3L,
    val homeBuilding: Long,
    val workplace: Vec3L?,
  ) {

    /**
     * Where this person would be at [hour].
     *
     * A pure function of the occupation and the clock, which is what lets residency ask the question
     * without materialising anybody. It is deliberately coarse - the post through the shift, the house
     * otherwise, and nothing about the walk between them - because it decides whether somebody is worth
     * building, not where to draw them.
     */
    fun anchorAt(hour: Int): Vec3L {
      val shift = occupation.shift ?: return home
      return if (shift.covers(hour) && workplace != null) workplace else home
    }

    /** The hours they are indoors at home. See [IndoorRegistry]. */
    val restHours: HourWindow get() = occupation.rest
  }

  private val bySettlement = HashMap<Int, List<Resident>>()

  fun of(settlement: Int): List<Resident> {
    bySettlement[settlement]?.let { return it }

    val built = build(settlement)
    bySettlement[settlement] = built
    return built
  }

  /** For a settlement whose buildings have changed under it, and for tooling. */
  fun forget(settlement: Int) {
    bySettlement.remove(settlement)
  }

  private fun build(settlement: Int): List<Resident> {
    val summary = sites.siteOf(settlement)?.population ?: return emptyList()
    // A housed household puts at least one person out, so this is the floor rather than the census.
    val residents = ArrayList<Resident>(summary.householdCount)

    for (household in 0 until summary.householdCount) {
      val placed = placement.of(settlement, household) ?: continue

      for (member in placed.residents) {
        residents.add(
          Resident(
            identity = TownsfolkIdentity.of(settlement, household, member),
            occupation = placement.occupationFor(placed.household, placed.household.members[member]),
            home = placed.home,
            homeBuilding = placed.homeBuilding,
            workplace = placed.workplace,
          )
        )
      }
    }

    return residents
  }
}

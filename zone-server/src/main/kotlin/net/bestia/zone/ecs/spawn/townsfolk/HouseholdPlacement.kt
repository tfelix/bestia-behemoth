package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.Households
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.worldgen.pop.Sector
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * Where a household lives, where it works, and what its people do.
 *
 * All of it derived, none of it stored. `Households.one` rebuilds a household from the settlement's seed
 * without expanding any other, and a house is an index into [SettlementSite.buildings], which has a fixed
 * order for this reason - so the same person comes back to the same door after the town has been empty
 * for a week, with nothing written down in between.
 */
@Service
class HouseholdPlacement(
  private val sites: SettlementSiteIndex,
  private val occupations: OccupationCatalogue,
) {

  /**
   * @param household the expansion of the settlement's seed at this index
   * @param workplace null for somebody with nowhere in particular to be - a town with no barn for its
   *   farmers, or a trade whose shop this settlement does not have
   */
  class Placement(
    val settlement: Int,
    val household: Household,
    val home: Vec3L,
    /** The house's prop id. What tells somebody with a door from somebody standing in a field. */
    val homeBuilding: Long,
    val workplace: Vec3L?,
  )

  /** @return null when the settlement has no people, or no houses to put them in */
  fun of(settlement: Int, household: Int): Placement? {
    val site = sites.siteOf(settlement) ?: return null
    val summary = site.population ?: return null
    if (household !in 0 until summary.householdCount) return null

    val homes = site.buildingsOf(BuildingFunction.RESIDENCE)
    if (homes.isEmpty()) return null

    val expanded = Households.one(summary, household)
    val house = homes[household % homes.size]

    return Placement(
      settlement = settlement,
      household = expanded,
      home = sites.doorstepOf(house),
      homeBuilding = house.propId,
      workplace = workplaceOf(site, expanded, household)?.let { sites.doorstepOf(it) },
    )
  }

  /**
   * What one member of a household does.
   *
   * Children are children whatever the household keeps, and everybody else takes the household's trade -
   * an apprentice and a servant work where the head works, which is what those kinships mean. Where no
   * occupation exists for the trade yet, they are labourers rather than unemployed: a town of people with
   * nothing to do at all reads worse than a town of people carrying things about, and the trade will get
   * its own occupation later.
   */
  fun occupationFor(household: Household, member: Member): Occupation {
    if (member.kinship == Kinship.CHILD) return occupations.getOrThrow(CHILD)

    val trade = household.business.takeIf { it >= 0 }?.let { BusinessCatalogue.ALL[it].id }
    val kept = trade?.let { id -> occupations.all().firstOrNull { it.businessType == id } }

    return kept
      ?: occupations.getOrThrow(if (household.sector == Sector.FARM) FARMER else LABOURER)
  }

  /**
   * A building of the household's trade, or a barn for a farming one.
   *
   * Indexed by the household rather than by a count of who else keeps this trade, because the households
   * of one trade are contiguous - `Households.one` walks the roster in catalogue order - so the modulo
   * spreads them over that trade's buildings without anybody having to expand the rest of the town.
   */
  private fun workplaceOf(site: SettlementSite, household: Household, index: Int): SettlementSite.Building? {
    val candidates = when {
      household.business >= 0 -> site.buildingsFor(household.business)
      household.sector == Sector.FARM -> site.buildingsOf(BuildingFunction.FARM)
      else -> emptyList()
    }

    return candidates.getOrNull(index % candidates.size.coerceAtLeast(1))
  }

  private companion object {
    const val CHILD = "child"
    const val FARMER = "farmer"
    const val LABOURER = "labourer"
  }
}

package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.core.GenRng
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
  private val config: TownsfolkResidencyConfig,
) {

  /**
   * @param household the expansion of the settlement's seed at this index
   * @param residents indices into [Household.members] of the people who are out of doors
   * @param workplace null for somebody with nowhere in particular to be - a town with no barn for its
   *   farmers, or a trade whose shop this settlement does not have
   */
  class Placement(
    val settlement: Int,
    val household: Household,
    val residents: List<Int>,
    val home: Vec3L,
    /** The house's prop id. What tells somebody with a door from somebody standing in a field. */
    val homeBuilding: Long,
    val workplace: Vec3L?,
  )

  /** @return null when the settlement has no people, no houses to put them in, or no house left for this one */
  fun of(settlement: Int, household: Int): Placement? {
    val site = sites.siteOf(settlement) ?: return null
    val summary = site.population ?: return null
    if (household !in 0 until summary.householdCount) return null

    val homes = site.buildingsOf(BuildingFunction.RESIDENCE)
    if (homes.isEmpty()) return null

    val expanded = Households.one(summary, household)
    // One household per house. A town has about one residence per ten inhabitants but one household per
    // five, so wrapping the list gave every front door two families and put the second one on the street.
    val house = homes.getOrNull(household) ?: return null

    return Placement(
      settlement = settlement,
      household = expanded,
      residents = residentsOf(summary.seed, expanded),
      home = sites.doorstepOf(house),
      homeBuilding = house.propId,
      workplace = workplaceOf(site, expanded, household)?.let { sites.doorstepOf(it) },
    )
  }

  /**
   * Which of a household is out in the town, and which is only ever a number in the census.
   *
   * The head always, because a household's trade is the head's and a town staffed by its children keeps
   * no shops. The rest by draw rather than by kinship rank: at one or two people a house a rank order
   * would seat the spouse every time and leave no child standing anywhere in the world, and a child is
   * somebody the player can talk to.
   */
  private fun residentsOf(seed: Long, household: Household): List<Int> {
    val spread = config.maxPerHome - config.minPerHome + 1
    val drawn = GenRng.hashUnit(seed, household.index.toLong(), HOME_SIZE_SALT)
    val wanted = (config.minPerHome + (drawn * spread).toInt()).coerceAtMost(household.members.size)

    val rest = (1 until household.members.size)
      .sortedBy { GenRng.hashUnit(seed, household.index.toLong(), it.toLong(), RESIDENT_SALT) }
      .take(wanted - 1)

    return (listOf(HEAD) + rest).sorted()
  }

  /**
   * How far this person's own day sits off the hours their occupation states, in minutes.
   *
   * Drawn from the three indices that *are* a person, so it survives the destroy and rebuild that walking
   * through a door costs them - an entity id would give them a new timetable every time they came out.
   * Zero when the town keeps punctual hours.
   *
   * One number for the whole day rather than one per boundary; see
   * [net.bestia.zone.ai.core.state.HourWindow.coversMinute].
   */
  fun dayOffsetOf(settlement: Int, household: Int, member: Int): Int {
    val spread = occupations.dayJitterMinutes()
    if (spread == 0) return 0

    val drawn = GenRng.hashUnit(
      settlement.toLong(), household.toLong(), member.toLong(), DAY_OFFSET_SALT
    )

    return (drawn * (2 * spread + 1)).toInt() - spread
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

    /** `Households.membersOf` puts the head first, and `Household.head` reads it back that way. */
    const val HEAD = 0

    // One salt per question, as the lattice and the town's memories already do.
    const val HOME_SIZE_SALT = 0xD00_1L
    const val RESIDENT_SALT = 0xD00_2L
    const val DAY_OFFSET_SALT = 0xD00_3L
  }
}

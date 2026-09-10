package net.bestia.zone.world.settlement

import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.vector.Vec2d

/**
 * One settlement's buildings and businesses, as the runtime needs them.
 *
 * The generator already decides all of this - where every house stands, which way its door faces, and which
 * trade occupies it - and then throws the join away: a `BUSINESS` marker knows its settlement and its trade
 * but not which building hosts it, and nothing anywhere keeps a building list. This is that join, made once
 * per settlement.
 *
 * Positions are metres, matching the feature store. A caller placing an entity converts.
 */
class SettlementSite(
  val index: Int,
  val centre: Vec2d,
  val tier: SettlementTier,
  val population: PopulationSummary?,
  val buildings: List<Building>
) {

  /**
   * @param propId what this building is called once it is a prop a player can knock down
   * @param door the doorstep, already stood off the wall - see [DoorPoint]
   */
  class Building(
    val propId: Long,
    val function: BuildingFunction,
    val centre: Vec2d,
    val door: Vec2d,
    val floorElevation: Double,
    /** Index into `BusinessCatalogue.ALL` for the trade working here, or -1 for an unoccupied building. */
    val businessType: Int
  )

  private val byFunction: Map<BuildingFunction, List<Building>> = buildings.groupBy { it.function }
  private val byPropId: Map<Long, Building> = buildings.associateBy { it.propId }

  fun buildingsOf(function: BuildingFunction): List<Building> {
    return byFunction[function].orEmpty()
  }

  /** Every building whose trade is [businessType], in the order the generator emitted them. */
  fun buildingsFor(businessType: Int): List<Building> {
    return buildings.filter { it.businessType == businessType }
  }

  fun buildingOf(propId: Long): Building? {
    return byPropId[propId]
  }
}

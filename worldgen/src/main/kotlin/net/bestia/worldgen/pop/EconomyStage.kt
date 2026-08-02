package net.bestia.worldgen.pop

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.civ.TownStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.ResourceStage
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Tuning for [EconomyStage]. */
data class EconomyParams(

  /**
   * Residents one square kilometre of ideal farmland feeds.
   *
   * The number the whole economy scales off, and the one to reach for when a world comes out with every town
   * starving or every town a metropolis.
   *
   * Calibrated *against placement*, not against a textbook, and that direction is deliberate. The tier
   * population ranges in [SettlementTier] were arrived at by eye and are the one thing about a settlement
   * already known to look right; a food model that says the largest city in a reference world can feed nine
   * thousand of its twenty-three thousand people is not a discovery about the city, it is a wrong divisor. At
   * a hundred and twenty every settlement in the reference world came out unable to feed itself, which the
   * `town` view showed as a hundred percent of the population farming.
   */
  val residentsPerSquareKilometre: Double = 170.0,

  /**
   * Residents one square kilometre of water feeds.
   *
   * An order of magnitude below farmland, which is about right for inshore fishing - and enough that a village
   * on a coast where nothing grows is a fishing village rather than a starving one.
   */
  val residentsPerSquareKilometreOfWater: Double = 14.0,

  /** Agricultural catchment radius per tier, in metres. A day's cart haul at the top end. */
  val cityCatchment: Double = 20_000.0,
  val townCatchment: Double = 12_000.0,
  val villageCatchment: Double = 7_000.0,
  val hamletCatchment: Double = 4_000.0,

  /** Metres within which a settlement can work a deposit. */
  val resourceRange: Double = 14_000.0,

  /** Metres within which a road counts as passing through a settlement. */
  val roadReach: Double = 2_500.0,

  /** Residents per household. */
  val peoplePerHousehold: Double = 4.6,

  /** Employees per business, before sector adjustment. A master, a journeyman and a boy. */
  val peoplePerBusiness: Double = 3.2,

  /**
   * Metres between roadside inns along a trunk road: one day's travel.
   *
   * Thirty kilometres is a laden cart's day. The inns are placed by arc length along the road polyline,
   * which the architecture document points out is exactly what the vector tier makes free - "the road
   * network gives you exact one-day-travel positions along the polyline by arc length".
   */
  val innSpacing: Double = 30_000.0,

  /** Metres from a settlement within which a roadside inn is redundant: the town has its own. */
  val innClearance: Double = 6_000.0
) : Params {

  init {
    require(residentsPerSquareKilometre > 0.0) {
      "residentsPerSquareKilometre must be positive, was $residentsPerSquareKilometre"
    }
    require(residentsPerSquareKilometreOfWater >= 0.0) {
      "residentsPerSquareKilometreOfWater must not be negative, was $residentsPerSquareKilometreOfWater"
    }
    // Positive, but deliberately *not* ordered by tier. A world where a hamlet out-farms a city is a strange
    // world rather than a broken one, and forbidding it here would reject the experiment that finds out.
    require(cityCatchment > 0.0) { "cityCatchment must be positive, was $cityCatchment" }
    require(townCatchment > 0.0) { "townCatchment must be positive, was $townCatchment" }
    require(villageCatchment > 0.0) { "villageCatchment must be positive, was $villageCatchment" }
    require(hamletCatchment > 0.0) { "hamletCatchment must be positive, was $hamletCatchment" }
    require(resourceRange >= 0.0) { "resourceRange must not be negative, was $resourceRange" }
    require(roadReach >= 0.0) { "roadReach must not be negative, was $roadReach" }
    require(peoplePerHousehold > 0.0) { "peoplePerHousehold must be positive, was $peoplePerHousehold" }
    require(peoplePerBusiness > 0.0) { "peoplePerBusiness must be positive, was $peoplePerBusiness" }
    require(innSpacing > 0.0) { "innSpacing must be positive, was $innSpacing" }
    require(innClearance >= 0.0) { "innClearance must not be negative, was $innClearance" }
  }

  override fun digest() = ParamsDigest()
    .put("residentsPerSquareKilometre", residentsPerSquareKilometre)
    .put("residentsPerSquareKilometreOfWater", residentsPerSquareKilometreOfWater)
    .put("cityCatchment", cityCatchment)
    .put("townCatchment", townCatchment)
    .put("villageCatchment", villageCatchment)
    .put("hamletCatchment", hamletCatchment)
    .put("resourceRange", resourceRange)
    .put("roadReach", roadReach)
    .put("peoplePerHousehold", peoplePerHousehold)
    .put("peoplePerBusiness", peoplePerBusiness)
    .put("innSpacing", innSpacing)
    .put("innClearance", innClearance)
}

/**
 * Step 9: the economy, and the people it implies.
 *
 * ### NPCs are derived from economy, not sprinkled
 *
 * The chain is: the agricultural catchment says how many people the land feeds; the surplus says how many of
 * them do not farm; culture and wealth split those between crafts, trade, services, administration, clergy
 * and the garrison; and service ratios plus preconditions turn that into an actual roster of businesses.
 * Nothing is placed at random and nothing has a quota, which is why the results are *specific*: a port with
 * four fishmongers and a shipwright, a mining town with three smiths and no baker.
 *
 * ### What is stored, and what is not
 *
 * Businesses are stored, because they are places - a marker each, sitting in the building the town stage
 * laid out. Households are **not**: a summary and a seed go in the `SETTLEMENT_ECONOMY` marker, and
 * [Households.expand] rebuilds them on demand. That is the architecture document's agent LOD, and it is the
 * difference between a few dozen bytes per settlement and four hundred thousand stored people.
 *
 * Individual NPCs, their schedules and the rumour graph are not here. What is here is the substrate they are
 * derived from - occupations, kinship, wealth, a small-world social graph and the events a household
 * plausibly knows - all of it as pure functions in [Households] rather than as stored state, because a
 * *runtime* system is what should own a living NPC and worldgen should own what it is made of.
 */
class EconomyStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: EconomyParams = EconomyParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, Culture.catalogueDigest(), SettlementTier.catalogueDigest(), BusinessCatalogue.digest())
  override val dependencies = listOf(
    ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, BiomeStage.ID, ResourceStage.ID,
    SettlementStage.ID, HistoryStage.ID, TownStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.SETTLEMENT_ECONOMY),
    StageOutput.Vector(FeatureKind.BUSINESS),
    StageOutput.Vector(FeatureKind.ROADSIDE_INN)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val places = EconomyReader.read(ctx, region)
    if (places.isEmpty()) return StageResult.EMPTY

    streamBase = GenRng.hash(ctx.seed, id.hash, version.toLong())
    val nextId = FeatureIds.allocator(id)
    val out = ArrayList<VectorFeature>()

    val buildings = buildingsBySettlement(ctx, region)

    for ((place, evaluated) in evaluate(ctx, region, places)) {
      val roster = BusinessCatalogue.evaluate(evaluated.setting).filter { it.exists }

      out.add(economyMarker(nextId(), place, evaluated.catchment, evaluated.setting, roster))
      out.addAll(placeBusinesses(nextId, place, roster, buildings[place.index].orEmpty()))
    }

    out.addAll(roadsideInns(ctx, region, places, nextId))

    return StageResult(features = out)
  }

  /** One settlement's catchment and the setting derived from it. */
  internal class Evaluated(val catchment: Catchments.Result, val setting: BusinessCatalogue.Setting)

  /**
   * Catchments and settings for every settlement, in index order.
   *
   * Public within the module for the sake of [EconomyProbe], which the `town -Pwhy` view uses to re-derive
   * one settlement's decision and print the trace. That the tool calls *this* rather than reimplementing the
   * reasoning is the only thing that keeps a "why" view honest - a second copy of the derivation would
   * eventually explain a roster the stage did not produce.
   */
  internal fun evaluate(
    ctx: GenContext,
    region: CellRegion,
    places: List<EconomyReader.Place>
  ): List<Pair<EconomyReader.Place, Evaluated>> {
    val land = Catchments(ctx, region, params)
    val catchments = land.shareOut(places)

    return places.map { place ->
      val catchment = catchments.getValue(place.index)
      place to Evaluated(catchment, settingFor(place, catchment, land))
    }
  }

  private fun settingFor(
    place: EconomyReader.Place,
    catchment: Catchments.Result,
    land: Catchments
  ): BusinessCatalogue.Setting = BusinessCatalogue.Setting(
    population = place.population,
    wealth = place.wealth,
    traffic = place.traffic,
    coastal = place.coastal,
    water = catchment.hasFreshWater || place.coastal,
    // A share of the catchment's own yield rather than a per-resident figure. Per resident it is a statement
    // about how empty the countryside is - a lone city in a wide valley grows nine residents' worth of cereal
    // per head and passes every grain test trivially - where the question a baker actually asks is whether
    // this land grows grain *at all*, which is what a share answers.
    cerealShare = catchment.cerealCapacity / max(1.0, catchment.foodCapacity),
    pasture = catchment.pasture,
    resources = catchment.resources,
    temperature = land.temperatureAt(place.position),
    technology = place.technology,
    garrison = place.walled || place.tier == SettlementTier.CITY
  )

  // --- Emitting ------------------------------------------------------------------------------------

  private fun economyMarker(
    featureId: FeatureId,
    place: EconomyReader.Place,
    catchment: Catchments.Result,
    setting: BusinessCatalogue.Setting,
    roster: List<BusinessCatalogue.Decision>
  ): PointMarker {
    val sectors = allocateSectors(place, catchment)
    val households = max(1, (place.population / params.peoplePerHousehold).toInt())

    return PointMarker(
      id = featureId,
      kind = FeatureKind.SETTLEMENT_ECONOMY,
      position = place.position,
      attributes = StationTable.Builder(1)
        .channel(EconomyChannels.INDEX) { place.index.toDouble() }
        .channel(EconomyChannels.FOOD_CAPACITY) { catchment.foodCapacity }
        .channel(EconomyChannels.FOOD_SURPLUS) { catchment.foodCapacity - place.population }
        .channel(EconomyChannels.CEREAL_SHARE) { setting.cerealShare }
        .channel(EconomyChannels.PASTURE) { catchment.pasture }
        .channel(EconomyChannels.FARMERS) { sectors[Sector.FARM.ordinal].toDouble() }
        .channel(EconomyChannels.CRAFTERS) { sectors[Sector.CRAFT.ordinal].toDouble() }
        .channel(EconomyChannels.TRADERS) { sectors[Sector.TRADE.ordinal].toDouble() }
        .channel(EconomyChannels.SERVANTS) { sectors[Sector.SERVICE.ordinal].toDouble() }
        .channel(EconomyChannels.ADMINISTRATORS) { sectors[Sector.ADMIN.ordinal].toDouble() }
        .channel(EconomyChannels.CLERGY) { sectors[Sector.CLERGY.ordinal].toDouble() }
        .channel(EconomyChannels.SOLDIERS) { sectors[Sector.MILITARY.ordinal].toDouble() }
        .channel(EconomyChannels.BUSINESS_COUNT) { roster.sumOf { it.count }.toDouble() }
        .channel(EconomyChannels.HOUSEHOLD_COUNT) { households.toDouble() }
        .channel(EconomyChannels.HOUSEHOLD_SEED) { householdSeed(place.index).toDouble() }
        .channel(EconomyChannels.TRAFFIC) { place.traffic }
        .build()
    )
  }

  /**
   * How the population splits by sector.
   *
   * ### Farmers come out of the land, not out of a share
   *
   * How many people have to farm is decided by how much food the land in reach yields per head: a settlement
   * on thin upland soil is almost all farmers and one on a floodplain can spare half its people for
   * everything else. That inversion is the whole model - the document's "compute food surplus from the
   * agricultural catchment, which determines how many non-farmers the settlement can support" - and it is
   * what makes a good site worth having beyond being pretty.
   *
   * ### Everyone else is split by culture, not by the shop roster
   *
   * The first version allocated the non-farm population in proportion to what the *business roster* demanded,
   * and the `town` view showed why that is wrong: a city of 4 852 came out with 2 366 crafters, because the
   * ninety establishments it had were normalised to consume every spare hand. A roster is a list of the
   * establishments a player can walk into; most non-farm work - labourers, carters, servants, soldiers,
   * builders - belongs to no establishment at all. So the split is by culture bias, and the businesses are
   * placed on top of it.
   */
  private fun allocateSectors(
    place: EconomyReader.Place,
    catchment: Catchments.Result
  ): IntArray {
    val out = IntArray(Sector.entries.size)
    val population = place.population
    val culture = place.culture

    // Residents the land in reach could feed, per resident actually present. Above one, there is slack.
    val landPerResident = (catchment.foodCapacity / max(1.0, population.toDouble()))
      .coerceIn(0.0, LAND_SLACK_CAP)

    // At exactly enough land, a farmer feeds `1 / farmShare` people - which is the definition of the culture's
    // farming share. Better land lets each farmer feed more, up to the cap.
    val fedPerFarmer = (1.0 / culture.farmShare) * landPerResident
    val needed = if (fedPerFarmer <= 0.0) {
      population
    } else {
      Math.ceil(population / fedPerFarmer).toInt()
    }

    // A place whose own land cannot feed it does not put every hand on the land - it buys grain, and what it
    // buys arrives by road. So the ceiling on farmers falls with traffic and with tier, and the shortfall
    // shows up as the negative food surplus the economy marker already reports. Without this, the largest
    // city in the reference world came out a hundred percent farmers and still starving, which is a model
    // saying "this city cannot exist" about a city that does.
    val urban = when (place.tier) {
      SettlementTier.CITY -> 1.0
      SettlementTier.TOWN -> 0.5

      // A village feeds itself off its own fields; nothing arrives by road that it could not grow.
      SettlementTier.VILLAGE, SettlementTier.HAMLET -> 0.0
    }
    val maxFarmShare = (MAX_FARM_SHARE -
        TRADE_FED_SHARE * (place.traffic / TrafficProxy.HIGHWAY).coerceAtMost(1.0) -
        URBAN_FED_SHARE * urban).coerceAtLeast(MIN_FARM_SHARE)

    val farmers = needed.coerceIn(
      (population * MIN_FARM_SHARE).toInt(),
      (population * maxFarmShare).toInt().coerceAtLeast(1)
    )

    var free = population - farmers

    val weights = DoubleArray(Sector.entries.size)
    for (sector in Sector.entries) {
      if (sector == Sector.FARM) continue
      weights[sector.ordinal] = NON_FARM_BASE.getValue(sector) * biasOf(culture, sector) *
          wealthPull(sector, place.wealth)
    }
    val total = weights.sum()

    if (total > 0.0 && free > 0) {
      for (sector in Sector.entries) {
        if (sector == Sector.FARM) continue
        val share = (free * weights[sector.ordinal] / total).toInt()
        out[sector.ordinal] = share
        free -= share
      }
    }

    // Rounding remainder, and everyone left over if there was no non-farm demand at all.
    out[Sector.FARM.ordinal] = farmers + max(0, free)
    return out
  }

  /**
   * How much a sector grows with wealth.
   *
   * Administration, clergy and trade are what a rich place spends its surplus on; craft and farm labour are
   * not. Without this a poor village and a rich city have the same occupational profile at different scales,
   * which is the sort of thing that makes two settlements interchangeable.
   */
  private fun wealthPull(sector: Sector, wealth: Double): Double = when (sector) {
    Sector.ADMIN, Sector.CLERGY -> 0.5 + wealth * 1.5
    Sector.TRADE -> 0.7 + wealth
    Sector.SERVICE -> 0.8 + wealth * 0.6

    // What a place does regardless of how rich it is. A garrison is sized by threat, not by surplus.
    Sector.FARM, Sector.CRAFT, Sector.MILITARY -> 1.0
  }

  private fun biasOf(culture: Culture, sector: Sector): Double = when (sector) {
    Sector.FARM -> 1.0
    Sector.CRAFT -> culture.craftBias
    Sector.TRADE -> culture.tradeBias
    Sector.SERVICE -> culture.serviceBias
    Sector.ADMIN -> culture.adminBias
    Sector.CLERGY -> culture.clergyBias
    Sector.MILITARY -> culture.militaryBias
  }

  /**
   * Puts each business in a building whose function suits it.
   *
   * Suits, and then anything: a settlement laid out before its roster was known will not have a shop plot
   * for every shop, and an inn in a house is better than an inn nowhere. What matters is that a business has
   * a *position* - it is the thing an NPC schedule resolves against, and a business without one is a row in
   * a table.
   */
  private fun placeBusinesses(
    nextId: () -> FeatureId,
    place: EconomyReader.Place,
    roster: List<BusinessCatalogue.Decision>,
    buildings: List<FootprintFeature>
  ): List<VectorFeature> {
    if (buildings.isEmpty()) return emptyList()

    val byFunction = buildings.groupBy {
      BuildingFunction.entries[it.attribute(BuildingChannels.FUNCTION).toInt()]
    }
    val taken = HashSet<FeatureId>()
    val out = ArrayList<VectorFeature>()

    // A ceiling on how much of a town can be trade premises. Without it a settlement whose layout was cut
    // short - a village on a spur of land, or a city against the building cap - has a business in every
    // building it has, and a town where every house is a shop reads as wrong from inside it.
    val ceiling = maxOf(1, (buildings.size * MAX_BUSINESS_SHARE).toInt())

    for (decision in roster) {
      repeat(decision.count) {
        if (taken.size >= ceiling) return@repeat
        val host = hostFor(decision.type, byFunction, buildings, taken) ?: return@repeat
        taken.add(host.id)

        out.add(
          PointMarker(
            id = nextId(),
            kind = FeatureKind.BUSINESS,
            position = host.center,
            attributes = StationTable.Builder(1)
              .channel(BusinessChannels.SETTLEMENT) { place.index.toDouble() }
              .channel(BusinessChannels.TYPE) { BusinessCatalogue.ALL.indexOf(decision.type).toDouble() }
              .channel(BusinessChannels.SECTOR) { decision.type.sector.ordinal.toDouble() }
              .channel(BusinessChannels.EMPLOYEES) { params.peoplePerBusiness }
              .build()
          )
        )
      }
    }

    return out
  }

  private fun hostFor(
    type: BusinessType,
    byFunction: Map<BuildingFunction, List<FootprintFeature>>,
    all: List<FootprintFeature>,
    taken: Set<FeatureId>
  ): FootprintFeature? {
    for (preferred in preferredFunctions(type)) {
      byFunction[preferred]?.firstOrNull { it.id !in taken }?.let { return it }
    }
    return all.firstOrNull { it.id !in taken }
  }

  private fun preferredFunctions(type: BusinessType): List<BuildingFunction> = when {
    type.id == "temple" -> listOf(BuildingFunction.TEMPLE, BuildingFunction.CIVIC)
    type.id == "inn" -> listOf(BuildingFunction.INN, BuildingFunction.SHOP)
    type.id == "barracks" -> listOf(BuildingFunction.FORTIFICATION, BuildingFunction.CIVIC)
    type.id == "scribe" || type.id == "banker" -> listOf(BuildingFunction.CIVIC, BuildingFunction.SHOP)
    type.id == "shipwright" -> listOf(BuildingFunction.WAREHOUSE, BuildingFunction.CRAFT)
    type.sector == Sector.CRAFT -> listOf(BuildingFunction.CRAFT, BuildingFunction.SHOP)
    type.sector == Sector.TRADE -> listOf(BuildingFunction.SHOP, BuildingFunction.MARKET)
    else -> listOf(BuildingFunction.SHOP, BuildingFunction.RESIDENCE)
  }

  /**
   * Isolated inns along the trunk roads, one day's travel apart.
   *
   * Placed by arc length on the road's own centerline, so they land where a traveller would actually stop
   * rather than at the nearest grid cell - and skipped near a settlement, which has its own. These are the
   * detail the architecture document singles out as a worldbuilding win, and they are almost free: the
   * geometry that says where a day's travel ends is already in the vector tier.
   */
  private fun roadsideInns(
    ctx: GenContext,
    region: CellRegion,
    places: List<EconomyReader.Place>,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val roads = ctx.features.query(region.toWorld())
      .filter { it.kind == FeatureKind.ROAD }
      .filterIsInstance<PolylineFeature>()
    if (roads.isEmpty()) return emptyList()

    val out = ArrayList<VectorFeature>()

    for (road in roads) {
      // Only roads that carry traffic. A track between two villages has nobody to put up.
      if (trafficOf(road) < TRUNK_TRAFFIC) continue

      var s = params.innSpacing * 0.5
      while (s < road.centerline.length) {
        val at = road.centerline.pointAt(s)
        s += params.innSpacing

        if (places.any { it.position.distanceTo(at) < params.innClearance }) continue

        out.add(
          PointMarker(
            id = nextId(),
            kind = FeatureKind.ROADSIDE_INN,
            position = at,
            attributes = StationTable.Builder(1)
              .channel(BusinessChannels.SETTLEMENT) { -1.0 }
              .channel(BusinessChannels.TYPE) {
                BusinessCatalogue.ALL.indexOfFirst { it.id == "inn" }.toDouble()
              }
              .channel(BusinessChannels.SECTOR) { Sector.SERVICE.ordinal.toDouble() }
              .channel(BusinessChannels.EMPLOYEES) { params.peoplePerBusiness }
              .build()
          )
        )
      }
    }

    return out
  }

  private fun buildingsBySettlement(
    ctx: GenContext,
    region: CellRegion
  ): Map<Int, List<FootprintFeature>> = ctx.features.query(region.toWorld())
    .asSequence()
    .filter { it.kind == FeatureKind.BUILDING }
    .filterIsInstance<FootprintFeature>()
    .groupBy { it.attribute(BuildingChannels.SETTLEMENT).toInt() }

  private fun householdSeed(settlement: Int): Long =
    GenRng.hash(streamBase, settlement.toLong()) and HOUSEHOLD_SEED_MASK

  /**
   * The world seed folded with this stage's identity, set at the top of [generate].
   *
   * Mutable state on a stage, which the rest of the module does not have, so it is worth saying why it is
   * safe: it is written once per invocation before anything reads it and depends only on the config that
   * invocation was given. A stage instance runs once per world, so the value is a pure function of that
   * world. Were a stage ever invoked concurrently for two worlds this would have to become a parameter.
   */
  private var streamBase: Long = 0L

  private fun trafficOf(road: PolylineFeature): Double = TrafficProxy.of(road)

  companion object {
    val ID = StageId("economy")

    /** Traffic at or above which a road is a trunk route worth an inn. */
    private const val TRUNK_TRAFFIC = 2.0

    /**
     * Most land, per resident, that still buys more freedom from farming.
     *
     * Beyond this the surplus is not labour-limited but market-limited: nobody grows grain they cannot sell,
     * and an empty countryside does not make a village into a city.
     */
    private const val LAND_SLACK_CAP = 2.4

    /** Fewest farmers, as a share of population. Nowhere is a pure city. */
    private const val MIN_FARM_SHARE = 0.18

    /** Most farmers, as a share of population, for an isolated village with poor land. */
    private const val MAX_FARM_SHARE = 0.88

    /** How much of a settlement's food a highway can bring in, as a share of its population. */
    private const val TRADE_FED_SHARE = 0.30

    /** How much of a city's food comes from beyond its own catchment, by virtue of being a city. */
    private const val URBAN_FED_SHARE = 0.18

    /**
     * How a pre-industrial non-farm population divides, before culture and wealth tilt it.
     *
     * Craft dominates because most of it is not a named trade - building, carting, hauling, spinning - which
     * is exactly what the shop roster cannot see and why the split is not derived from it.
     */
    private val NON_FARM_BASE: Map<Sector, Double> = mapOf(
      Sector.CRAFT to 0.34,
      Sector.SERVICE to 0.20,
      Sector.TRADE to 0.18,
      Sector.MILITARY to 0.12,
      Sector.ADMIN to 0.08,
      Sector.CLERGY to 0.08
    )

    /** Keeps a household seed exactly representable in a station channel. */
    private const val HOUSEHOLD_SEED_MASK = 0xFFFF_FFFF_FFFFL

    /** Largest share of a settlement's buildings that may be trade premises rather than dwellings. */
    private const val MAX_BUSINESS_SHARE = 0.35
  }
}

/**
 * Road traffic, inferred from the width the settlement stage gave the road.
 *
 * The settlement stage simulates traffic with a gravity model and then *discards the number*, keeping only
 * the tier it chose - a track, a road or a highway. So this reads the tier back out of the carriageway
 * half-width, which is exact because there are three discrete values, and maps it to a traffic weight.
 *
 * It is a proxy and it is worth naming as one: what survives into the vector tier is the tier, not the
 * traffic, so "how busy is this road" is answered to three levels rather than continuously. That is enough
 * for the two questions asked of it - is this a trunk route, and is this settlement a crossroads - and the
 * fix if it stops being enough is a `traffic` station channel on the road, not a better guess here.
 */
internal object TrafficProxy {

  fun fromHalfWidth(halfWidth: Double): Double = when {
    halfWidth >= 4.0 -> HIGHWAY
    halfWidth >= 2.4 -> ROAD
    halfWidth > 0.0 -> TRACK
    else -> 0.0
  }

  /**
   * A road's tier, from the widest carriageway anywhere along it.
   *
   * The widest, not the first station's, and the difference is not academic. A road's half-width is driven to
   * *zero* over a bridged river crossing - that gap is what stops the road damming the channel - so a road
   * that happens to cross a river within a station or two of its start reports a half-width of nothing at
   * station zero. Reading it there made the largest city in a test world report no road traffic at all, and
   * therefore no market traders and no bank.
   */
  fun of(road: PolylineFeature): Double {
    val stations = road.stations
    val channel = runCatching { stations.channel(Profiles.CHANNEL_HALF_WIDTH) }.getOrNull() ?: return 0.0

    var widest = 0.0
    for (station in 0 until stations.stationCount) {
      widest = max(widest, stations.valueAt(channel, station))
    }
    return fromHalfWidth(widest)
  }

  const val TRACK = 1.0
  const val ROAD = 3.0
  const val HIGHWAY = 8.0
}

/**
 * The settlements the economy runs over, joined across placement, history and the road network.
 */
internal object EconomyReader {

  class Place(
    val index: Int,
    val position: Vec2d,
    val tier: SettlementTier,
    val cultureIndex: Int,
    val population: Int,
    val wealth: Double,
    val technology: Double,
    val walled: Boolean,
    val coastal: Boolean,
    /** Sum of the traffic weights of the roads passing through. Zero for a place no road reaches. */
    val traffic: Double
  ) {
    val culture: Culture get() = Culture.byIndex(cultureIndex)
  }

  fun read(ctx: GenContext, region: CellRegion): List<Place> {
    val bounds = region.toWorld()
    val all = ctx.features.query(bounds)

    val placed = all
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }

    val history = all
      .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(HistoryChannels.INDEX).toInt() }

    val roads = all
      .filter { it.kind == FeatureKind.ROAD }
      .filterIsInstance<PolylineFeature>()

    val distanceToOcean = ctx.layers.float(LayerId.DISTANCE_TO_OCEAN)
    val params = EconomyParams()

    return placed.keys.sorted().mapNotNull { index ->
      val site = placed.getValue(index)
      val past = history[index] ?: return@mapNotNull null

      if (past.attribute(HistoryChannels.FOUNDED_YEAR).toInt() == 0) return@mapNotNull null
      if (past.attribute(HistoryChannels.ABANDONED_YEAR).toInt() != 0) return@mapNotNull null

      val population = past.attribute(HistoryChannels.POPULATION).toInt()
      if (population < 1) return@mapNotNull null

      Place(
        index = index,
        position = site.position,
        tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()],
        cultureIndex = site.attribute(SettlementChannels.CULTURE).toInt(),
        population = population,
        wealth = past.attribute(HistoryChannels.WEALTH),
        technology = past.attribute(HistoryChannels.TECHNOLOGY),
        walled = past.attribute(HistoryChannels.WALL_YEAR).toInt() != 0,
        coastal = distanceToOcean.sampleBilinear(site.position.x, site.position.y) < COASTAL_RANGE,
        traffic = trafficAt(site.position, roads, params.roadReach)
      )
    }
  }

  private fun trafficAt(at: Vec2d, roads: List<PolylineFeature>, reach: Double): Double {
    var total = 0.0
    for (road in roads) {
      if (!road.bbox.expanded(reach).contains(at.x, at.y)) continue
      if (road.centerline.project(at).distance > reach) continue
      total += TrafficProxy.of(road)
    }
    return total
  }

  private const val COASTAL_RANGE = 6_000.0
}

/**
 * The agricultural catchments, and the competition between them.
 *
 * ### Why the sharing out matters
 *
 * A catchment is a disc, and discs overlap. Summing each settlement's own disc independently double-counts
 * every field between two villages, and the result is a world where every settlement is comfortably fed and
 * expansion has no cost. Sharing each cell out between the settlements that claim it, by proximity, is what
 * makes a crowded region poorer per head than an empty one - which is the pressure that makes a good site
 * worth having.
 */
internal class Catchments(
  private val ctx: GenContext,
  private val region: CellRegion,
  private val params: EconomyParams
) {

  class Result(
    /** Residents the catchment feeds. */
    val foodCapacity: Double,
    /** How much of that is cereal, which is what the grain trades need. */
    val cerealCapacity: Double,
    /** Mean grazing quality, 0 to 1. */
    val pasture: Double,
    val hasFreshWater: Boolean,
    val resources: Set<ResourceType>,
    /** Square kilometres actually claimed after competition. Reported by the census view. */
    val claimedSquareKm: Double
  )

  private val fertility: FloatLayer = ctx.layers.float(LayerId.SOIL_FERTILITY)
  private val elevation: FloatLayer = ctx.layers.float(LayerId.ELEVATION)
  private val waterLevel: FloatLayer = ctx.layers.float(LayerId.WATER_LEVEL)
  private val temperature: FloatLayer = ctx.layers.float(LayerId.TEMPERATURE)
  private val biome = ctx.layers.int(LayerId.BIOME)

  /**
   * Temperature lifted onto this stage's own grid.
   *
   * Climate runs four times coarser than the heightfield, so its layer's region is a quarter of the width in
   * each direction - and indexing it with a kilometre-grid cell coordinate does not fail, it *clamps*, and
   * returns the value from somewhere near the world's corner. The first version did exactly that: every
   * catchment in the world read the polar temperature at the edge of the climate grid, `climateFactor`
   * returned zero, and every settlement came out with a food capacity of nothing and a population that was
   * a hundred percent farmers. The `town -Pwhy` view printed "cereal is 0% of the catchment's yield" for a
   * city in a river valley, which is what made it findable at all.
   *
   * The lesson is the one [net.bestia.worldgen.fields.Grid.resampled] exists for: a stage reads another
   * stage's layer *through world coordinates*, never by index, unless it knows the two resolutions are equal.
   */
  private val warmth: Grid = Grid.resampled(temperature, region)

  private val metres = region.resolution.metresPerCell
  private val squareKmPerCell = metres * metres / 1_000_000.0

  private val deposits: List<PointMarker> = ctx.features.query(region.toWorld())
    .filter { it.kind == FeatureKind.ORE_DEPOSIT }
    .filterIsInstance<PointMarker>()

  fun temperatureAt(at: Vec2d): Double = temperature.sampleBilinear(at.x, at.y)

  /** Every settlement's catchment, with overlapping cells divided between the claimants. */
  fun shareOut(places: List<EconomyReader.Place>): Map<Int, Result> {
    // Pass one: total claim weight per cell.
    val claims = DoubleArray(region.width * region.height)
    for (place in places) {
      forEachCell(place) { cell, weight -> claims[cell] += weight }
    }

    // Pass two: each settlement takes its share.
    val out = LinkedHashMap<Int, Result>(places.size)
    for (place in places) {
      var food = 0.0
      var cereal = 0.0
      var pastureSum = 0.0
      var cells = 0
      var claimed = 0.0
      var fresh = false
      val types = HashSet<ResourceType>()

      forEachCell(place) { cell, weight ->
        val share = if (claims[cell] > 0.0) weight / claims[cell] else 0.0
        val x = region.minX + cell % region.width
        val y = region.minY + cell / region.width

        if (!waterLevel[x, y].isNaN()) {
          fresh = true
          types.add(ResourceType.FISH)
          // Water feeds people too, and far less densely than farmland. Without this a fishing village on a
          // rocky coast had a food capacity of exactly zero, which the invariant sweep found on one seed in
          // eight - and which downstream became a settlement with nothing to eat living beside a sea full of
          // fish.
          food += params.residentsPerSquareKilometreOfWater * squareKmPerCell * share
        } else {
          val local = Biome.entries[biome[x, y]]
          val arable = fertility[x, y] * climateFactor(warmth.data[cell]) * arableFactor(x, y)
          val grazing = grazingOf(local)

          // Arable *or* pasture, whichever the ground is better for. Herding is what feeds a settlement on
          // ground too cold or too steep to plough, and it is the reason a steppe or a fell can hold anybody
          // at all - the arable term alone goes to zero there and takes the whole catchment with it.
          val yieldHere = (arable + grazing * PASTURE_EFFICIENCY).coerceAtMost(1.0) *
              params.residentsPerSquareKilometre * squareKmPerCell
          food += yieldHere * share

          // Cereal wants flat, fertile *and* temperate, and is the arable term alone - pasture grows no grain.
          // Excluding the fells and the tropics is what makes a highland town short of bread.
          if (local.isCereal()) {
            cereal += arable * params.residentsPerSquareKilometre * squareKmPerCell * share
          }

          pastureSum += grazing
          cells++
          claimed += squareKmPerCell * share
          if (local.isForest()) types.add(ResourceType.TIMBER)
          if (local == Biome.ALPINE || local == Biome.CLIFF || local == Biome.BADLANDS) {
            types.add(ResourceType.STONE)
          }
        }
      }

      // Deposits are sparse markers, so they are found by a radius query rather than by walking cells.
      for (deposit in deposits) {
        if (deposit.position.distanceTo(place.position) > params.resourceRange) continue
        runCatching {
          types.add(ResourceType.entries[deposit.attribute(DepositChannels.TYPE).toInt()])
        }
      }
      if (place.coastal) types.add(ResourceType.FISH)
      // Clay is a floodplain material rather than an orebody, and every river valley has some.
      if (fresh) types.add(ResourceType.CLAY)

      out[place.index] = Result(
        foodCapacity = food,
        cerealCapacity = cereal,
        pasture = if (cells == 0) 0.0 else pastureSum / cells,
        hasFreshWater = fresh,
        resources = types,
        claimedSquareKm = claimed
      )
    }

    return out
  }

  /**
   * Walks the cells of one settlement's catchment, weighting each by proximity.
   *
   * The weight is what the sharing out divides by, and making it fall with distance is what puts a contested
   * field mostly in the nearer village's hands. Linear rather than inverse-square, because at these
   * distances the deciding factor is how far a cart goes in a morning, not a gravitational analogy.
   */
  private inline fun forEachCell(place: EconomyReader.Place, body: (cell: Int, weight: Double) -> Unit) {
    val radius = catchmentOf(place.tier)
    val span = (radius / metres).toInt()
    val cx = Math.floor(place.position.x / metres).toInt() - region.minX
    val cy = Math.floor(place.position.y / metres).toInt() - region.minY

    for (y in max(0, cy - span)..min(region.height - 1, cy + span)) {
      for (x in max(0, cx - span)..min(region.width - 1, cx + span)) {
        val dx = (x - cx) * metres
        val dy = (y - cy) * metres
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > radius) continue

        body(y * region.width + x, 1.0 - distance / radius * CLAIM_FALLOFF)
      }
    }
  }

  private fun catchmentOf(tier: SettlementTier): Double = when (tier) {
    SettlementTier.CITY -> params.cityCatchment
    SettlementTier.TOWN -> params.townCatchment
    SettlementTier.VILLAGE -> params.villageCatchment
    SettlementTier.HAMLET -> params.hamletCatchment
  }

  /** How much of a cell's fertility the climate lets a farmer realise. */
  private fun climateFactor(celsius: Double): Double = when {
    celsius < -4.0 -> 0.0
    celsius < 4.0 -> (celsius + 4.0) / 8.0 * 0.5
    celsius > 32.0 -> 0.35
    else -> 1.0
  }

  /**
   * Flat ground is farmable; a steep hillside is not.
   *
   * The threshold is deliberately generous for the resolution it is measured at. A kilometre cell whose
   * *mean* slope is one in four is not unfarmable ground - it is a valley with terraces, a floodplain and a
   * fellside in it, and the flat part is where the village is. Reading the mean as though the whole cell
   * shared it is what makes hill country come out unable to feed anybody.
   */
  private fun arableFactor(x: Int, y: Int): Double {
    val dx = (elevation[x + 1, y] - elevation[x - 1, y]) / (2.0 * metres)
    val dy = (elevation[x, y + 1] - elevation[x, y - 1]) / (2.0 * metres)
    val slope = sqrt((dx * dx + dy * dy).toDouble())
    return (1.0 - slope / MAX_ARABLE_SLOPE).coerceIn(0.0, 1.0)
  }

  private fun grazingOf(biome: Biome): Double = when (biome) {
    Biome.GRASSLAND, Biome.STEPPE -> 1.0
    Biome.SAVANNA, Biome.SHRUBLAND -> 0.7
    Biome.TUNDRA, Biome.ALPINE -> 0.35
    Biome.TEMPERATE_FOREST, Biome.TAIGA -> 0.25

    // A token amount, for the odd clearing. Exhaustive rather than defaulted: a new biome that grazes well
    // would otherwise be recorded as barren by a `when` nobody revisited, and pasture drives settlement size.
    Biome.OCEAN, Biome.LAKE, Biome.ICE_SHEET, Biome.GLACIER, Biome.COLD_DESERT,
    Biome.TEMPERATE_RAINFOREST, Biome.DESERT, Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST,
    Biome.WETLAND, Biome.RIPARIAN, Biome.BEACH, Biome.BADLANDS, Biome.CLIFF -> 0.05
  }

  private companion object {
    /** How much of its weight a cell loses at the edge of a catchment. Never all of it. */
    const val CLAIM_FALLOFF = 0.75

    const val MAX_ARABLE_SLOPE = 0.3

    /** How many people a unit of grazing feeds relative to the same land ploughed. Herding is less dense. */
    const val PASTURE_EFFICIENCY = 0.35
  }
}

/** Whether a biome grows cereal, and whether it grows trees worth felling. */
private fun Biome.isCereal(): Boolean = when (this) {
  Biome.GRASSLAND, Biome.TEMPERATE_FOREST, Biome.SHRUBLAND, Biome.WETLAND,
  Biome.TROPICAL_SEASONAL_FOREST, Biome.SAVANNA -> true
  else -> false
}

private fun Biome.isForest(): Boolean = when (this) {
  Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST, Biome.TAIGA,
  Biome.TROPICAL_RAINFOREST, Biome.TROPICAL_SEASONAL_FOREST -> true
  else -> false
}

package net.bestia.worldgen.pop

import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.resource.ResourceType
import java.util.Locale

/** Which broad part of the economy a job belongs to. */
enum class Sector { FARM, CRAFT, TRADE, SERVICE, ADMIN, CLERGY, MILITARY }

/**
 * What a settlement needs to have before a trade can exist in it.
 *
 * A named enum rather than a lambda, and that is the whole point of the design: a *reason*. When a mining
 * town has three smiths and no baker, the question a developer asks is "why is there no baker", and the
 * answer has to be a thing that can be printed - `NEEDS_GRAIN not met: food surplus from cereal is 0.03` -
 * rather than a closure that returned false. The `town -Pwhy` tool prints exactly that, per trade, per
 * settlement, and that is the debugging affordance step 9 most needed.
 */
enum class Precondition(val label: String) {
  NEEDS_WATER("fresh or salt water within reach"),
  NEEDS_SEA("a coast"),
  NEEDS_GRAIN("arable land producing cereal"),
  NEEDS_PASTURE("grazing"),
  NEEDS_TIMBER("workable timber nearby"),
  NEEDS_IRON("iron, and timber to char into fuel for it"),
  NEEDS_CLAY("clay"),
  NEEDS_STONE("building stone"),
  NEEDS_ORE("any metal ore"),
  NEEDS_VINE_CLIMATE("a climate that ripens fruit"),
  NEEDS_TRADE_ROUTE("a road carrying trade"),
  NEEDS_GARRISON("soldiers to equip"),
  NEEDS_WEALTH("enough wealth to have customers"),
  NEEDS_LITERACY("someone who can read")
}

/**
 * One kind of business, and what it takes to have one.
 *
 * ### Service ratios rather than a scatter
 *
 * `residentsEach` is the historical service ratio - one general store per two hundred people, one temple
 * per eight hundred - and it is what makes a settlement's roster follow from its size instead of being
 * rolled. What makes the roster *specific* is the pair of gates on top of it: [minPopulation], below which
 * the trade cannot be supported at all, and [preconditions], which are about the place rather than the size.
 *
 * The two together produce the thing worth having: a port with four fishmongers and a shipwright, a mining
 * town with three smiths and no baker, a crossroads village with more inns than its population implies.
 * None of that is scripted anywhere; it falls out of a ratio and a precondition list.
 */
data class BusinessType(
  val id: String,
  val label: String,
  val sector: Sector,
  /** Residents supporting one of these. Lower is commoner. */
  val residentsEach: Double,
  /** Population below which the trade does not appear at all, whatever the ratio says. */
  val minPopulation: Int,
  val preconditions: List<Precondition> = emptyList(),
  /**
   * Extra count from road traffic through the settlement, per unit of traffic.
   *
   * Only inns and stables have it, and it is the reason a crossroads village has three inns: travellers are
   * customers who do not live there, so a ratio against residents alone cannot see them.
   */
  val perTraffic: Double = 0.0,
  /** At least one of these, once the trade exists at all. A town over hamlet size always has a temple. */
  val alwaysAtLeastOne: Boolean = false
)

/**
 * The catalogue, and the decision procedure over it.
 *
 * Data in a Kotlin list rather than in a file, which the architecture document's data-driven-configuration
 * section says it should not be. That deviation is unchanged and deliberate: the shape of a business type is
 * still moving, and a serialisation layer over a shape that moves is two things to change instead of one.
 * What has been kept is the *grouping* - one list, one type, no logic mixed in - so extracting it later is a
 * serialiser rather than a redesign.
 */
object BusinessCatalogue {

  val ALL: List<BusinessType> = listOf(
    // Food and drink.
    BusinessType("baker", "baker", Sector.CRAFT, 250.0, 120, listOf(Precondition.NEEDS_GRAIN)),
    BusinessType("butcher", "butcher", Sector.CRAFT, 400.0, 200, listOf(Precondition.NEEDS_PASTURE)),
    BusinessType("brewer", "brewer", Sector.CRAFT, 500.0, 150, listOf(Precondition.NEEDS_GRAIN)),
    BusinessType(
      "vintner", "vintner", Sector.CRAFT, 900.0, 600,
      listOf(Precondition.NEEDS_VINE_CLIMATE, Precondition.NEEDS_WEALTH)
    ),
    BusinessType(
      "fishmonger", "fishmonger", Sector.TRADE, 300.0, 60,
      listOf(Precondition.NEEDS_WATER)
    ),
    BusinessType("miller", "miller", Sector.CRAFT, 600.0, 120, listOf(Precondition.NEEDS_GRAIN)),

    // Trade and lodging.
    BusinessType("general_store", "general store", Sector.TRADE, 220.0, 90),
    BusinessType(
      "inn", "inn", Sector.SERVICE, 320.0, 40,
      perTraffic = 0.9, alwaysAtLeastOne = true
    ),
    BusinessType(
      "stable", "stable", Sector.SERVICE, 900.0, 150,
      listOf(Precondition.NEEDS_PASTURE), perTraffic = 0.5
    ),
    BusinessType(
      "market_trader", "market trader", Sector.TRADE, 130.0, 400,
      listOf(Precondition.NEEDS_TRADE_ROUTE)
    ),
    BusinessType(
      "shipwright", "shipwright", Sector.CRAFT, 1_400.0, 500,
      listOf(Precondition.NEEDS_SEA, Precondition.NEEDS_TIMBER)
    ),

    // Building and metal.
    BusinessType(
      "blacksmith", "blacksmith", Sector.CRAFT, 200.0, 80,
      listOf(Precondition.NEEDS_IRON)
    ),
    BusinessType(
      "armourer", "armourer", Sector.CRAFT, 1_600.0, 900,
      listOf(Precondition.NEEDS_IRON, Precondition.NEEDS_GARRISON)
    ),
    BusinessType("carpenter", "carpenter", Sector.CRAFT, 350.0, 100, listOf(Precondition.NEEDS_TIMBER)),
    BusinessType("mason", "mason", Sector.CRAFT, 700.0, 300, listOf(Precondition.NEEDS_STONE)),
    BusinessType("potter", "potter", Sector.CRAFT, 550.0, 120, listOf(Precondition.NEEDS_CLAY)),
    BusinessType(
      "charcoaler", "charcoal burner", Sector.CRAFT, 500.0, 100,
      listOf(Precondition.NEEDS_TIMBER)
    ),

    // Noxious trades. Zoned downwind and downstream by the town stage, which is where players notice them.
    BusinessType("tanner", "tanner", Sector.CRAFT, 800.0, 250, listOf(Precondition.NEEDS_WATER)),
    BusinessType("dyer", "dyer", Sector.CRAFT, 1_000.0, 400, listOf(Precondition.NEEDS_WATER)),

    // Cloth.
    BusinessType("weaver", "weaver", Sector.CRAFT, 300.0, 120, listOf(Precondition.NEEDS_PASTURE)),
    BusinessType(
      "tailor", "tailor", Sector.CRAFT, 600.0, 300,
      listOf(Precondition.NEEDS_WEALTH)
    ),

    // Services and specialists. These are what make a city feel like a city.
    BusinessType("temple", "temple", Sector.CLERGY, 800.0, 150, alwaysAtLeastOne = true),
    BusinessType("healer", "healer", Sector.SERVICE, 500.0, 200),
    BusinessType(
      "apothecary", "apothecary", Sector.SERVICE, 1_200.0, 700,
      listOf(Precondition.NEEDS_LITERACY)
    ),
    BusinessType(
      "scribe", "scribe", Sector.ADMIN, 1_500.0, 800,
      listOf(Precondition.NEEDS_LITERACY)
    ),
    BusinessType(
      "jeweller", "jeweller", Sector.CRAFT, 2_600.0, 1_800,
      listOf(Precondition.NEEDS_ORE, Precondition.NEEDS_WEALTH)
    ),
    BusinessType(
      "bookbinder", "bookbinder", Sector.CRAFT, 4_000.0, 3_000,
      listOf(Precondition.NEEDS_LITERACY, Precondition.NEEDS_WEALTH)
    ),
    BusinessType(
      "alchemist", "alchemist", Sector.CRAFT, 5_000.0, 4_000,
      listOf(Precondition.NEEDS_LITERACY, Precondition.NEEDS_WEALTH)
    ),
    BusinessType(
      "banker", "banker", Sector.TRADE, 6_000.0, 5_000,
      listOf(Precondition.NEEDS_TRADE_ROUTE, Precondition.NEEDS_WEALTH)
    ),
    BusinessType(
      "barracks", "barracks", Sector.MILITARY, 1_800.0, 600,
      listOf(Precondition.NEEDS_GARRISON)
    )
  )

  /**
   * Fingerprint of the roster: thirty-one trades, their ratios, their thresholds and their preconditions.
   *
   * Folded by trade **id** rather than by list position, and the position is not folded at all - unlike the
   * culture and tier tables, nothing stores an index into this list. What a settlement stores is its economy
   * summary and a seed; the roster is re-derived from it, so reordering this list is genuinely cosmetic while
   * changing a ratio is not.
   *
   * Preconditions fold as their enum **names**, which is the whole reason a precondition is an enum rather than
   * a lambda: a closure could not be fingerprinted any more than it could be printed, and printing it is what
   * makes "why is there no baker" answerable.
   */
  fun digest(): Long {
    val digest = ParamsDigest()
    for (type in ALL) {
      digest.nested(
        type.id,
        ParamsDigest()
          .put("label", type.label)
          .put("sector", type.sector)
          .put("residentsEach", type.residentsEach)
          .put("minPopulation", type.minPopulation)
          .put("preconditions", type.preconditions.joinToString(",") { it.name })
          .put("perTraffic", type.perTraffic)
          .put("alwaysAtLeastOne", type.alwaysAtLeastOne)
          .value
      )
    }
    return digest.value
  }

  /** What one settlement offers a trade: the facts every precondition is decided against. */
  class Setting(
    val population: Int,
    val wealth: Double,
    /** Road traffic through the settlement, in the same units the settlement stage tiers roads by. */
    val traffic: Double,
    val coastal: Boolean,
    /** Fresh water within reach: a river, a lake, or the sea. */
    val water: Boolean,
    /** Share of the catchment's food yield that is cereal rather than pasture, orchard or fish. */
    val cerealShare: Double,
    /** Grazing quality of the catchment, 0 to 1. */
    val pasture: Double,
    /** Resource types with a worked deposit or a biome supply within reach. */
    val resources: Set<ResourceType>,
    /** Mean annual temperature, for the trades that need a climate. */
    val temperature: Double,
    /** Owning civ's technology, 0 to 1. Literacy is the threshold on it. */
    val technology: Double,
    val garrison: Boolean
  )

  /** Whether one precondition is met, and the number that decided it. */
  class Check(val precondition: Precondition, val met: Boolean, val evidence: String)

  /** The decision for one trade in one settlement, kept whether or not it produced anything. */
  class Decision(
    val type: BusinessType,
    val count: Int,
    val checks: List<Check>,
    /** Why the count is what it is, in one phrase. Printed by the `why` view. */
    val reason: String
  ) {
    val exists get() = count > 0
  }

  /**
   * Evaluates the whole catalogue against one settlement.
   *
   * Returns a decision for *every* trade including the ones that produced nothing, which is the difference
   * between a debuggable model and a list of shops. The roster is `filter { it.exists }`; the answer to "why
   * is there no baker" is the decision that is not in it.
   */
  fun evaluate(setting: Setting): List<Decision> = ALL.map { type ->
    val checks = type.preconditions.map { check(it, setting) }
    val unmet = checks.filter { !it.met }

    when {
      unmet.isNotEmpty() -> Decision(
        type, 0, checks,
        "needs ${unmet.joinToString(" and ") { it.precondition.label }} " +
            "(${unmet.joinToString("; ") { it.evidence }})"
      )

      setting.population < type.minPopulation -> Decision(
        type, 0, checks,
        "population ${setting.population} is below the ${type.minPopulation} this trade needs"
      )

      else -> {
        val fromResidents = setting.population / type.residentsEach
        val fromTraffic = setting.traffic * type.perTraffic
        val total = fromResidents + fromTraffic
        val count = max(
          if (type.alwaysAtLeastOne) 1 else 0,
          Math.round(total).toInt()
        )

        Decision(
          type, count, checks,
          if (type.perTraffic > 0.0) {
            "%.2f from %d residents, %.2f from traffic %.1f"
              .format(Locale.ROOT, fromResidents, setting.population, fromTraffic, setting.traffic)
          } else {
            "%.2f from %d residents at one per %.0f"
              .format(Locale.ROOT, fromResidents, setting.population, type.residentsEach)
          }
        )
      }
    }
  }

  private fun check(precondition: Precondition, setting: Setting): Check = when (precondition) {
    Precondition.NEEDS_WATER -> Check(
      precondition, setting.water || setting.coastal,
      if (setting.water) "fresh water within reach" else "no water within reach"
    )

    Precondition.NEEDS_SEA -> Check(
      precondition, setting.coastal, if (setting.coastal) "on the coast" else "inland"
    )

    Precondition.NEEDS_GRAIN -> Check(
      precondition, setting.cerealShare >= CEREAL_THRESHOLD,
      "cereal is %.0f%% of the catchment's yield, needs %.0f%%"
        .format(Locale.ROOT, setting.cerealShare * 100.0, CEREAL_THRESHOLD * 100.0)
    )

    Precondition.NEEDS_PASTURE -> Check(
      precondition, setting.pasture >= PASTURE_THRESHOLD,
      "pasture %.2f, needs %.2f".format(Locale.ROOT, setting.pasture, PASTURE_THRESHOLD)
    )

    Precondition.NEEDS_TIMBER -> Check(
      precondition, ResourceType.TIMBER in setting.resources,
      if (ResourceType.TIMBER in setting.resources) "timber within reach" else "no timber within reach"
    )

    // Iron *and* fuel, because a bloomery without charcoal is a pile of ore. This is the precondition that
    // most often explains a missing smith, and the one whose evidence line is worth reading.
    //
    // Timber is now the only fuel: coal left the resource catalogue with the graded-ore change, on the
    // grounds that a bulk mineral nobody could pick up was pulling its weight in the economy sim alone.
    // Charcoal is what an iron age actually ran on anyway - see the `charcoaler` business below.
    Precondition.NEEDS_IRON -> {
      val ore = ResourceType.IRON in setting.resources
      val fuel = ResourceType.TIMBER in setting.resources
      Check(
        precondition, ore && fuel,
        "iron ${if (ore) "yes" else "no"}, fuel ${if (fuel) "yes" else "no"}"
      )
    }

    Precondition.NEEDS_CLAY -> Check(
      precondition, ResourceType.CLAY in setting.resources,
      if (ResourceType.CLAY in setting.resources) "clay within reach" else "no clay within reach"
    )

    Precondition.NEEDS_STONE -> Check(
      precondition,
      ResourceType.STONE in setting.resources || ResourceType.MARBLE in setting.resources,
      "stone ${if (ResourceType.STONE in setting.resources) "yes" else "no"}"
    )

    Precondition.NEEDS_ORE -> {
      // Mithrandium is deliberately absent. It sits two hundred and fifty metres down at the shallowest,
      // which is below anything this civilisation can sink a shaft to, so a smelter next to one would be a
      // smelter with nothing to smelt. It is a resource for players, not for the simulation.
      val metals = setOf(
        ResourceType.COPPER, ResourceType.TIN, ResourceType.IRON, ResourceType.SILVER,
        ResourceType.GOLD_LODE, ResourceType.GOLD_PLACER
      )
      val found = setting.resources.intersect(metals)
      Check(precondition, found.isNotEmpty(), if (found.isEmpty()) "no metal ore" else "$found")
    }

    Precondition.NEEDS_VINE_CLIMATE -> Check(
      precondition, setting.temperature in VINE_RANGE,
      "%.1f C, needs %.0f to %.0f".format(
        Locale.ROOT, setting.temperature, VINE_RANGE.start, VINE_RANGE.endInclusive
      )
    )

    Precondition.NEEDS_TRADE_ROUTE -> Check(
      precondition, setting.traffic >= TRAFFIC_THRESHOLD,
      "traffic %.2f, needs %.2f".format(Locale.ROOT, setting.traffic, TRAFFIC_THRESHOLD)
    )

    Precondition.NEEDS_GARRISON -> Check(
      precondition, setting.garrison,
      if (setting.garrison) "walled or a frontier post" else "no garrison"
    )

    Precondition.NEEDS_WEALTH -> Check(
      precondition, setting.wealth >= WEALTH_THRESHOLD,
      "wealth %.2f, needs %.2f".format(Locale.ROOT, setting.wealth, WEALTH_THRESHOLD)
    )

    Precondition.NEEDS_LITERACY -> Check(
      precondition, setting.technology >= LITERACY_THRESHOLD,
      "technology %.2f, needs %.2f".format(Locale.ROOT, setting.technology, LITERACY_THRESHOLD)
    )
  }

  private fun max(a: Int, b: Int) = if (a > b) a else b

  /** Cereal per resident at which a settlement grows enough grain to support a trade that needs it. */
  private const val CEREAL_THRESHOLD = 0.35

  private const val PASTURE_THRESHOLD = 0.25
  private const val TRAFFIC_THRESHOLD = 1.5
  private const val WEALTH_THRESHOLD = 0.45
  private const val LITERACY_THRESHOLD = 0.30

  /** Mean annual temperature range that ripens fruit, in degrees. */
  private val VINE_RANGE = 9.0..22.0
}

/** Station channels on a [net.bestia.worldgen.vector.FeatureKind.BUSINESS] marker. */
object BusinessChannels {
  /** Joins to [net.bestia.worldgen.civ.SettlementChannels.INDEX]. -1 for a roadside inn. */
  const val SETTLEMENT = "settlement"

  /** Index into [BusinessCatalogue.ALL]. A category; read with `toInt()`. */
  const val TYPE = "type"

  /** [Sector] ordinal. */
  const val SECTOR = "sector"

  /** People employed here, including the proprietor's household. */
  const val EMPLOYEES = "employees"
}

/** Station channels on a [net.bestia.worldgen.vector.FeatureKind.SETTLEMENT_ECONOMY] marker. */
object EconomyChannels {
  const val INDEX = "index"

  /** Food produced by the catchment, in residents it can feed. */
  const val FOOD_CAPACITY = "food_capacity"

  /** Food capacity minus population. Negative means the place imports, which trade has to cover. */
  const val FOOD_SURPLUS = "food_surplus"

  /** Share of the catchment's yield that is cereal. What the grain preconditions read. */
  const val CEREAL_SHARE = "cereal_share"

  const val PASTURE = "pasture"

  /** Population by sector. Seven channels, one per [Sector], in ordinal order. */
  const val FARMERS = "farmers"
  const val CRAFTERS = "crafters"
  const val TRADERS = "traders"
  const val SERVANTS = "servants"
  const val ADMINISTRATORS = "administrators"
  const val CLERGY = "clergy"
  const val SOLDIERS = "soldiers"

  const val BUSINESS_COUNT = "business_count"
  const val HOUSEHOLD_COUNT = "household_count"

  /** Seed the household graph expands from. See [Households]. */
  const val HOUSEHOLD_SEED = "household_seed"

  /** Road traffic through the settlement. */
  const val TRAFFIC = "traffic"
}

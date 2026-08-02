package net.bestia.worldgen.civ

import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.voxel.BlockType
import kotlin.math.abs
import kotlin.math.atan2

/**
 * What a building is for.
 *
 * Ordinals are stored in a station channel, so this list may be *extended* freely but not reordered - the
 * same rule as [net.bestia.worldgen.bio.Biome]. Reordering would repurpose every stored building.
 */
enum class BuildingFunction(val label: String) {

  /** The stalls and the cross at the centre. Single storey, open sided. */
  MARKET("market"),

  TEMPLE("temple"),

  /** Guildhall, court, granary: whatever the settlement governs itself from. */
  CIVIC("civic"),

  /** Ground-floor trade with living space above, on a main street. */
  SHOP("shop"),

  /** A workshop. The noxious trades are zoned downwind and downstream; see [Zoning]. */
  CRAFT("craft"),

  WAREHOUSE("warehouse"),

  INN("inn"),

  RESIDENCE("residence"),

  /** Barn, byre, farmstead. Beyond the built-up edge. */
  FARM("farm"),

  /** A tower on the wall circuit, or a keep. */
  FORTIFICATION("fortification")
}

/** How a roof is shaped, which is most of what a building looks like from above. */
enum class RoofShape { FLAT, GABLE, HIP }

/**
 * One building, decided but not yet emitted.
 *
 * Everything here is a pure function of the lot, the settlement's history and a grammar seed, which is what
 * lets the same building be described by the world tier and materialised by a chunk without either storing
 * geometry - the marker carries these numbers and the materialiser rebuilds the walls from them.
 */
internal class Building(
  val centre: Vec2d,
  /** Unit direction of the long axis. */
  val bearing: Vec2d,
  val halfLength: Double,
  val halfWidth: Double,
  val function: BuildingFunction,
  val storeys: Int,
  val wall: BlockType,
  val roof: BlockType,
  val roofShape: RoofShape,
  /** Elevation of the ground floor, in metres. Baked in so every chunk agrees where the floor is. */
  val floorElevation: Double,
  /**
   * Seed for whatever detail a shape grammar adds later: window rhythm, dormers, a stair.
   *
   * Stored now, before anything reads it, on the same argument as the base hash riding along in every chunk
   * message: it costs eight bytes, and adding it later would renumber every building in every stored world.
   */
  val grammarSeed: Long,
  /** Which way the door faces: towards the street the lot fronts. */
  val doorBearing: Vec2d
) {
  val storeyHeight: Double get() = STOREY_HEIGHT

  val eaveElevation: Double get() = floorElevation + storeys * STOREY_HEIGHT

  companion object {
    /** Floor-to-floor height in metres. Two and a half is about right for pre-industrial building. */
    const val STOREY_HEIGHT = 2.6
  }
}

/**
 * Land value, and the functions that follow from it.
 *
 * The whole zoning model is one scalar and a sorted list, which is a smaller idea than it sounds and
 * produces most of what a real town's layout communicates: the market at the middle, the temple beside it,
 * the shops on the through street, the tanners downwind, the farms outside. What makes it work is that the
 * scalar is *not* just distance from the centre - a lot on a rank-0 street two hundred metres out is worth
 * more than one on a lane fifty metres out, which is why the high street exists.
 */
internal class Zoning(
  private val frame: TownFrame,
  private val population: Int,
  private val wealth: Double,
  private val cultureIndex: Int,
  private val coastal: Boolean,
  /** Direction the wind blows towards. Noxious trades go this way, so the smoke leaves town. */
  private val downwind: Vec2d,
  /** Direction water flows. Tanning and dyeing go this way, so the mess leaves too. */
  private val downstream: Vec2d,
  private val roll: (Long, Long) -> Double
) {

  fun valueOf(lot: Lot): Double {
    val central = 1.0 - (lot.fromCentre / frame.builtRadius).coerceIn(0.0, 1.0)
    val street = when (lot.streetRank) {
      0 -> 1.0
      1 -> 0.62
      2 -> 0.35
      else -> 0.2
    }
    // Multiplicative in neither and additive in both: a back lane by the market and a high street on the
    // edge should both be worth having, and a product would make each of them worthless.
    return central * 0.55 + street * 0.45
  }

  /**
   * Assigns a function to every lot, in descending order of land value.
   *
   * Quota-based rather than threshold-based, and that matters on a small settlement: a threshold like "land
   * value above 0.9 becomes a temple" gives a hamlet no temple and a city forty. Counting them out means a
   * village gets one and a city gets six, which is the shape the service-ratio model in
   * `net.bestia.worldgen.pop` also uses, and the two agree because they are the same idea.
   */
  fun assign(lots: List<Lot>): List<BuildingFunction> {
    val order = lots.indices.sortedWith(
      compareByDescending<Int> { valueOf(lots[it]) }.thenBy { it }
    )
    val out = arrayOfNulls<BuildingFunction>(lots.size)

    var markets = if (population >= MARKET_POPULATION) 1 else 0
    var temples = when {
      population >= 6_000 -> 3
      population >= 1_200 -> 2
      population >= 200 -> 1
      else -> 0
    }
    var civic = when {
      population >= 6_000 -> 3
      population >= 1_200 -> 1
      else -> 0
    }
    var inns = 1 + population / INN_POPULATION
    var shops = (lots.size * SHOP_SHARE * (0.6 + wealth)).toInt()
    var warehouses = if (coastal) (lots.size * WAREHOUSE_SHARE).toInt() + 1 else 0
    var crafts = (lots.size * CRAFT_SHARE * (0.7 + 0.6 * wealth)).toInt()

    for (index in order) {
      val lot = lots[index]

      // The outer ring is farmland whatever it is worth: a barn on the edge of a village is not a
      // devalued shop, it is a different kind of building.
      if (lot.fromCentre > frame.builtRadius * FARM_RING) {
        out[index] = BuildingFunction.FARM
        continue
      }

      out[index] = when {
        markets > 0 -> BuildingFunction.MARKET.also { markets-- }
        temples > 0 -> BuildingFunction.TEMPLE.also { temples-- }
        civic > 0 -> BuildingFunction.CIVIC.also { civic-- }
        // A district, not a scatter: the noxious trades want to be together and downwind, so they are
        // placed by direction first and only then by what is left over.
        crafts > 0 && inNoxiousDistrict(lot) -> BuildingFunction.CRAFT.also { crafts-- }
        warehouses > 0 && lot.streetRank <= 1 -> BuildingFunction.WAREHOUSE.also { warehouses-- }
        inns > 0 && lot.streetRank == 0 -> BuildingFunction.INN.also { inns-- }
        shops > 0 && lot.streetRank <= 1 -> BuildingFunction.SHOP.also { shops-- }
        crafts > 0 -> BuildingFunction.CRAFT.also { crafts-- }
        else -> BuildingFunction.RESIDENCE
      }
    }

    return out.map { it ?: BuildingFunction.RESIDENCE }
  }

  /**
   * Whether a lot is in the quarter the tanners and smiths get.
   *
   * Downwind *or* downstream, within a wide arc, and not in the middle of town. Both directions rather
   * than one because they are different nuisances - smoke travels on the wind, effluent on the water - and
   * a settlement usually has to accept the two in different quarters. Players notice this without being
   * told, which is the whole reason it is worth the fifteen lines.
   */
  private fun inNoxiousDistrict(lot: Lot): Boolean {
    if (lot.fromCentre < frame.builtRadius * NOXIOUS_INNER) return false

    val direction = (lot.centre - frame.centre).normalized()
    return angleBetween(direction, downwind) < NOXIOUS_ARC ||
        angleBetween(direction, downstream) < NOXIOUS_ARC
  }

  private fun angleBetween(a: Vec2d, b: Vec2d): Double =
    abs(atan2(a cross b, a dot b))

  /**
   * How much of its plot each kind of building takes, along the street and back into the block.
   *
   * Before this, function decided a building's orientation, its storeys, its walls and its roof, and never its
   * size - so a market hall, a temple and a labourer's cottage were the same box, and only the number of floors
   * told them apart from above. A town reads wrong when its public buildings are cottage-sized.
   *
   * **Capped at `1 / FOOTPRINT_FILL`**, which is the rule that keeps a building inside its own plot. That is not
   * cosmetic: `LotIndex.overlaps` is the *only* thing preventing two buildings occupying the same ground, it
   * separates *plots*, and nothing downstream would catch a building that had grown out of its own. Anything
   * genuinely larger has to get a larger plot instead - see the grand-plot pass in `TownStage`.
   */
  private fun footprintFor(function: BuildingFunction): Pair<Double, Double> = when (function) {
    // A cottage is the reference: full plot, ordinary yard behind it.
    BuildingFunction.RESIDENCE -> 1.0 to 1.0

    // A workshop needs floor space more than frontage - the yard is where the work happens.
    BuildingFunction.CRAFT -> 1.0 to MAX_PLOT_FILL

    // A shop wants a window on the street; an inn wants both, plus a yard for horses.
    BuildingFunction.SHOP -> 1.02 to MAX_PLOT_FILL
    BuildingFunction.INN -> 1.06 to MAX_PLOT_FILL

    // Everything public or agricultural takes what it can get, and gets the rest from a grand plot.
    BuildingFunction.MARKET, BuildingFunction.TEMPLE, BuildingFunction.CIVIC, BuildingFunction.WAREHOUSE,
    BuildingFunction.FARM, BuildingFunction.FORTIFICATION -> MAX_PLOT_FILL to MAX_PLOT_FILL
  }

  /**
   * The building that goes on a lot: its footprint, height, materials and roof.
   *
   * The shape rule worth naming is the orientation. An ordinary dwelling stands *gable to the street* -
   * its long axis running back into the plot - because that is how you fit a household onto a narrow
   * frontage on an expensive street, and it is why a medieval street reads as a row of narrow gables
   * rather than a row of wide fronts. A temple or a market does the opposite: it wants to be seen, so its
   * long axis runs along the street.
   */
  fun buildingFor(lot: Lot, function: BuildingFunction, index: Int): Building {
    val broadFront = function == BuildingFunction.TEMPLE || function == BuildingFunction.MARKET ||
        function == BuildingFunction.CIVIC || function == BuildingFunction.WAREHOUSE

    val alongStreet = lot.inwards.perpendicular()
    val bearing = if (broadFront) alongStreet else lot.inwards

    // Applied to the plot's own axes, *before* the broad-front swap below. Getting that order wrong puts a
    // temple's along-the-street multiplier onto its depth, which is the one direction it does not want.
    val size = footprintFor(function)
    val frontage = lot.halfFrontage * FOOTPRINT_FILL * size.first
    val depth = lot.halfDepth * FOOTPRINT_FILL * size.second
    val halfLength = if (broadFront) frontage else depth
    val halfWidth = if (broadFront) depth else frontage

    val prestige = when (function) {
      BuildingFunction.TEMPLE, BuildingFunction.CIVIC -> 1.0
      BuildingFunction.SHOP, BuildingFunction.INN -> 0.55
      BuildingFunction.WAREHOUSE -> 0.4
      BuildingFunction.MARKET -> 0.0
      BuildingFunction.FARM -> 0.0
      BuildingFunction.CRAFT, BuildingFunction.RESIDENCE, BuildingFunction.FORTIFICATION -> 0.2
    }

    val culture = Culture.byIndex(cultureIndex)
    val storeyed = culture.storeys + prestige + wealth * 1.2
    val storeys = when (function) {
      BuildingFunction.MARKET -> 1
      BuildingFunction.FARM -> 1

      BuildingFunction.TEMPLE, BuildingFunction.CIVIC, BuildingFunction.SHOP, BuildingFunction.CRAFT,
      BuildingFunction.WAREHOUSE, BuildingFunction.INN, BuildingFunction.RESIDENCE,
      BuildingFunction.FORTIFICATION ->
        (storeyed.toInt() +
            if (roll(index.toLong(), STOREY_SALT) < storeyed - storeyed.toInt()) 1 else 0)
          .coerceIn(1, MAX_STOREYS)
    }

    // Stone follows the culture, then wealth, then function. A temple is stone in a timber town, which is
    // exactly the signal a temple is for.
    val stoneChance = (culture.stoneShare * (0.5 + wealth) + prestige * 0.5).coerceIn(0.0, 1.0)
    val stone = roll(index.toLong(), STONE_SALT) < stoneChance
    val wall = when {
      function == BuildingFunction.FORTIFICATION -> BlockType.MASONRY
      stone -> BlockType.MASONRY
      culture.stoneShare < 0.2 -> BlockType.TIMBER
      else -> BlockType.PLASTER
    }

    val tiled = roll(index.toLong(), ROOF_SALT) < (wealth * 0.8 + prestige * 0.5)
    val roof = if (tiled) BlockType.ROOF_TILE else BlockType.THATCH
    val roofShape = when {
      function == BuildingFunction.MARKET -> RoofShape.FLAT
      broadFront -> RoofShape.HIP
      else -> RoofShape.GABLE
    }

    // Set from the *front* of the plot rather than its centre, so a row of buildings on a slope steps with
    // the street instead of each one levelling to its own middle and leaving a jog between neighbours.
    val front = lot.centre - lot.inwards * lot.halfDepth
    val floor = frame.groundAt(front)

    return Building(
      centre = lot.centre,
      bearing = bearing,
      halfLength = halfLength,
      halfWidth = halfWidth,
      function = function,
      storeys = storeys,
      wall = wall,
      roof = roof,
      roofShape = roofShape,
      floorElevation = floor,
      grammarSeed = (roll(index.toLong(), GRAMMAR_SALT) * (1L shl 40)).toLong(),
      doorBearing = -lot.inwards
    )
  }

  private companion object {
    /** Population at which a settlement has a market rather than a green. */
    const val MARKET_POPULATION = 400

    /** Residents per inn from population alone. Road traffic adds more; see the economy stage. */
    const val INN_POPULATION = 700

    const val SHOP_SHARE = 0.16
    const val CRAFT_SHARE = 0.14
    const val WAREHOUSE_SHARE = 0.04

    /** Fraction of the built radius beyond which every lot is a farmstead. */
    const val FARM_RING = 0.88

    /** Fraction of the built radius inside which no noxious trade is allowed. */
    const val NOXIOUS_INNER = 0.35

    /** Half-angle of the noxious quarter, in radians. About fifty degrees each side. */
    const val NOXIOUS_ARC = 0.9

    /** How much of its plot a building covers. The rest is yard, and the gap between neighbours. */
    const val FOOTPRINT_FILL = 0.90

    /**
     * The most of its plot any building may take, as a multiple of [FOOTPRINT_FILL].
     *
     * `1 / FOOTPRINT_FILL` exactly: a building at this size fills its plot and not a millimetre more, which is
     * the boundary the plot-overlap test guarantees. See [footprintFor].
     */
    const val MAX_PLOT_FILL = 1.0 / FOOTPRINT_FILL

    const val MAX_STOREYS = 4

    const val STOREY_SALT = 0x41L
    const val STONE_SALT = 0x42L
    const val ROOF_SALT = 0x43L
    const val GRAMMAR_SALT = 0x44L
  }
}

/** Station channels on a [net.bestia.worldgen.vector.FeatureKind.BUILDING] footprint. */
object BuildingChannels {

  /** Settlement this belongs to, joining to [SettlementChannels.INDEX]. -1 for an isolated building. */
  const val SETTLEMENT = "settlement"

  /** [BuildingFunction] ordinal. A category; read with `toInt()`. */
  const val FUNCTION = "function"

  const val STOREYS = "storeys"

  /** Elevation of the ground floor in metres. The one number the materialiser cannot rederive. */
  const val FLOOR_ELEVATION = "floor_elevation"

  /** [BlockType] id of the walls, and of the roof. Ids, not ordinals - they are the permanent numbers. */
  const val WALL_BLOCK = "wall_block"
  const val ROOF_BLOCK = "roof_block"

  /** [RoofShape] ordinal. */
  const val ROOF_SHAPE = "roof_shape"

  /** Unit direction the door faces. Two channels because a station channel holds one number. */
  const val DOOR_X = "door_x"
  const val DOOR_Y = "door_y"

  /** Seed for the shape-grammar detail nothing generates yet. See [Building.grammarSeed]. */
  const val GRAMMAR_SEED = "grammar_seed"
}

/** Station channels on a [net.bestia.worldgen.vector.FeatureKind.TOWN_WALL] stretch. */
object WallChannels {
  const val SETTLEMENT = "settlement"

  /** Ground elevation at this station, so the wall follows the ground rather than floating. */
  const val BASE_ELEVATION = "base_elevation"

  /** Height above [BASE_ELEVATION] in metres. */
  const val HEIGHT = "height"

  /** Half the thickness in metres. */
  const val HALF_THICKNESS = "half_thickness"

  const val BLOCK = "block"
}

/** Station channels on a [net.bestia.worldgen.vector.FeatureKind.GATE] marker. */
object GateChannels {
  const val SETTLEMENT = "settlement"
  const val WIDTH = "width"

  /** Unit direction through the gate, outward. */
  const val BEARING_X = "bearing_x"
  const val BEARING_Y = "bearing_y"
}

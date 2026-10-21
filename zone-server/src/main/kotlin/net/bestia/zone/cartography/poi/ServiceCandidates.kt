package net.bestia.zone.cartography.poi

import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.core.World
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.BusinessChannels
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.BuildingProps
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.cartography.render.MapService

/**
 * One point a player can use, in world metres.
 *
 * [id] is the host building's prop id, or a ward stone's entity id. [openFrom] and [openTo] are the staffing
 * trade's shift in game hours, null for something with no hours - a market square, a ward stone.
 */
data class MapPoi(
  val id: Long,
  val kind: MapService,
  val business: String?,
  val x: Double,
  val y: Double,
  val settlement: String?,
  val openFrom: Int?,
  val openTo: Int?,
)

/**
 * What the generator put in a world that a player could use, before anything was knocked down.
 *
 * A pure function of the frozen feature store and the occupations, so it is recomputed per area rather than
 * stored, and it is safe on any thread. Separate from [MapPoiIndex] - which lays what play has destroyed over
 * it - so the offline `mapServe` tool and the tests can ask the same question without a database or an ECS.
 *
 * Three gates, every one of them needed:
 *  - **Standing.** The settlement was founded and history did not leave it a ruin. A ruin's businesses are
 *    still in the feature store, and a shop marked in a burnt-out shell is a lie with a travel button.
 *  - **Decided.** [MapService.forBusiness] gives the trade a kind.
 *  - **Staffed.** Somebody's occupation keeps the trade. A shop nobody stands in is not somewhere to go.
 *
 * Markets are the exception to the third, and shown whoever stands in them: a market square is a place in its
 * own right.
 */
class ServiceCandidates(
  private val world: World,
  /** [StaffedTrades.hoursByTrade]: a trade missing here has nobody keeping it, and is not on the map. */
  private val keepers: Map<String, HourWindow?>,
) {

  /**
   * Standing settlements by index, to their name or null for one with no name seed.
   *
   * The join `AreaNameRegistry.loadSettlements` and `PlaceNames` perform, made once: a business knows its
   * settlement's index and nothing else, and both "is it standing" and "what is it called" need the settlement
   * marker and the chronicle record together. One walk of the whole store, so lazy.
   */
  private val standingSettlements: Map<Int, String?> by lazy {
    val standing = HashMap<Int, String?>()

    for (feature in world.features.all()) {
      if (feature !is PointMarker || feature.kind != FeatureKind.SETTLEMENT) continue

      val index = feature.optionalAttribute(SettlementChannels.INDEX)?.toInt() ?: continue
      val record = world.chronicle.settlements.getOrNull(index) ?: continue
      if (!record.wasFounded || record.isRuin) continue

      val culture = feature.optionalAttribute(SettlementChannels.CULTURE)?.toInt()
      standing[index] = if (record.nameSeed == 0L || culture == null) null else Names.place(record.nameSeed, culture)
    }

    standing
  }

  /**
   * Every candidate inside [bounds].
   *
   * Half-open on [bounds], unlike [Aabb.contains]: a building exactly on the edge between two tiles belongs to
   * one of them, or the client draws it twice and selecting one leaves the other lit.
   */
  fun within(bounds: Aabb): List<MapPoi> {
    val found = ArrayList<MapPoi>()

    for (feature in world.features.query(bounds)) {
      val poi = when {
        feature.kind == FeatureKind.BUSINESS && feature is PointMarker -> businessAt(feature)
        feature.kind == FeatureKind.BUILDING && feature is FootprintFeature -> marketAt(feature)
        else -> null
      } ?: continue

      if (inside(bounds, poi.x, poi.y)) found.add(poi)
    }

    return found
  }

  private fun businessAt(marker: PointMarker): MapPoi? {
    val settlement = marker.optionalAttribute(BusinessChannels.SETTLEMENT)?.toInt() ?: return null
    // A roadside inn has no settlement and is already on the map as a `ROADSIDE_INN` landmark.
    if (settlement !in standingSettlements) return null

    val typeIndex = marker.optionalAttribute(BusinessChannels.TYPE)?.toInt() ?: return null
    val business = BusinessCatalogue.ALL.getOrNull(typeIndex)?.id ?: return null
    val kind = MapService.forBusiness(business) ?: return null
    if (business !in keepers) return null
    val hours = keepers[business]

    val position = marker.position
    return MapPoi(
      id = BuildingProps.propIdAt(position.x, position.y),
      kind = kind,
      business = business,
      x = position.x,
      y = position.y,
      settlement = standingSettlements[settlement],
      openFrom = hours?.fromHour,
      openTo = hours?.toHour,
    )
  }

  private fun marketAt(footprint: FootprintFeature): MapPoi? {
    val function = footprint.optionalAttribute(BuildingChannels.FUNCTION)?.toInt() ?: return null
    if (function != BuildingFunction.MARKET.ordinal) return null

    val settlement = footprint.optionalAttribute(BuildingChannels.SETTLEMENT)?.toInt() ?: return null
    if (settlement !in standingSettlements) return null

    val centre = footprint.center
    return MapPoi(
      id = BuildingProps.propIdAt(centre.x, centre.y),
      kind = MapService.MARKET,
      business = null,
      x = centre.x,
      y = centre.y,
      settlement = standingSettlements[settlement],
      openFrom = null,
      openTo = null,
    )
  }

  /** `PointMarker.optionalAttribute` for a footprint, which carries its attributes in the same one-station table. */
  private fun FootprintFeature.optionalAttribute(name: String): Double? {
    val table = attributes ?: return null
    val channel = table.channelNames.indexOf(name)
    return if (channel < 0) null else table.valueAt(channel, 0)
  }

  companion object {

    /** Half-open containment, so a point on a shared edge belongs to exactly one tile. */
    fun inside(bounds: Aabb, x: Double, y: Double): Boolean {
      return x >= bounds.minX && x < bounds.maxX && y >= bounds.minY && y < bounds.maxY
    }
  }
}

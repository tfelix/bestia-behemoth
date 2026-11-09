package net.bestia.zone.townsfolk.perception

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.dress.FixtureChannels
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * The lantern lines along the roads out of a settlement, for its lamplighter to walk.
 *
 * Behind an interface for [SettlementGathering]'s reason: the real answer wants a generated world, and an AI
 * scenario test has none.
 */
interface LanternRoutes {

  /** Whether this settlement has any lanterns at all, and so wants a lamplighter. */
  fun hasLanterns(settlement: Int): Boolean

  /**
   * The line of lanterns a lamplighter walks on [day], from the settlement outwards, or null for none.
   *
   * One road a night, chosen off the day, so the round moves between the roads out of town over a week
   * rather than covering all of them in an evening - a single lamplighter could not.
   */
  fun routeFor(settlement: Int, day: Long): List<Vec3L>?
}

/**
 * Read off the dressing stage's lantern markers, once per world.
 *
 * The markers carry their settlement and their place along the line, numbered outwards road after road, so a
 * settlement's lanterns sorted by that number fall into one run per road wherever two consecutive lanterns are
 * further apart than a spacing allows.
 */
@Service
class GeneratedLanternRoutes(
  private val worldService: WorldService,
  private val sites: SettlementSiteIndex,
) : LanternRoutes {

  private val runs: Map<Int, List<List<Vec3L>>> by lazy { build() }

  override fun hasLanterns(settlement: Int): Boolean = worldService.isLoaded && runs[settlement]?.isNotEmpty() == true

  override fun routeFor(settlement: Int, day: Long): List<Vec3L>? {
    if (!worldService.isLoaded) return null
    val lines = runs[settlement] ?: return null
    if (lines.isEmpty()) return null
    val pick = (GenRng.hashUnit(settlement.toLong(), day, ROUTE_SALT) * lines.size).toInt().coerceAtMost(lines.size - 1)
    return lines[pick]
  }

  private fun build(): Map<Int, List<List<Vec3L>>> {
    if (!worldService.isLoaded) return emptyMap()
    val bySettlement = HashMap<Int, MutableList<Pair<Int, PointMarker>>>()

    for (feature in worldService.generated.world.features.all()) {
      if (feature.kind != FeatureKind.FIXTURE || feature !is PointMarker) continue
      if (feature.optionalAttribute(FixtureChannels.KIND)?.toInt() != FixtureKind.LANTERN.ordinal) continue
      val settlement = feature.optionalAttribute(FixtureChannels.SETTLEMENT)?.toInt() ?: continue
      val order = feature.optionalAttribute(FixtureChannels.ORDER)?.toInt() ?: continue
      bySettlement.getOrPut(settlement) { ArrayList() }.add(order to feature)
    }

    return bySettlement.mapValues { (_, lanterns) ->
      val sorted = lanterns.sortedBy { it.first }.map { it.second }
      val lines = ArrayList<List<Vec3L>>()
      var current = ArrayList<Vec3L>()
      var previous: PointMarker? = null
      for (lantern in sorted) {
        val gap = previous?.position?.distanceTo(lantern.position) ?: 0.0
        if (previous != null && gap > RUN_BREAK_METRES && current.isNotEmpty()) {
          lines.add(current)
          current = ArrayList()
        }
        current.add(sites.standingAt(lantern.position))
        previous = lantern
      }
      if (current.isNotEmpty()) lines.add(current)
      lines
    }
  }

  private companion object {
    /** Farther than this between two lanterns in order and they are on different roads. */
    const val RUN_BREAK_METRES = 150.0
    const val ROUTE_SALT = 0x524F555445L
  }
}

/** A world with no lanterns: for tests, and for any caller that has no generated world to read them from. */
object NoLanternRoutes : LanternRoutes {
  override fun hasLanterns(settlement: Int) = false
  override fun routeFor(settlement: Int, day: Long): List<Vec3L>? = null
}

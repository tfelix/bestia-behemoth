package net.bestia.zone.settlement

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * Where a city sign may be planted: at least 5 km from every settlement centre, generated or player, and from a
 * town that fell less than 48 h ago (its row is kept that long).
 *
 * Ward fields need no check of their own: each stands in a home village and reaches 3.5 km.
 */
@Component
class FoundingSpot(
  private val siteIndex: SettlementSiteIndex,
  private val towns: TownRegistry,
  private val worldService: WorldService,
) {

  fun isFree(at: Vec3L): Boolean {
    return !isNearGeneratedSettlement(at) && towns.all().none { towns.metresBetween(it.sign, at) < MIN_DISTANCE_METRES }
  }

  private fun isNearGeneratedSettlement(at: Vec3L): Boolean {
    val voxelSize = worldService.config.voxelSize
    val reach = (MIN_DISTANCE_METRES / voxelSize).toLong()

    return siteIndex.coveringWithin(at.x, at.y, reach)
      .mapNotNull(siteIndex::siteOf)
      .any { hypot(it.centre.x - at.x * voxelSize, it.centre.y - at.y * voxelSize) < MIN_DISTANCE_METRES }
  }

  companion object {
    const val MIN_DISTANCE_METRES = 5_000.0
  }
}

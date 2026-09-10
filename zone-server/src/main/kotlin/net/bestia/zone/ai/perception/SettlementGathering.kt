package net.bestia.zone.ai.perception

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * Where a town's people spend an evening.
 *
 * Behind an interface for [SettlementFood]'s reason: the real answer wants a generated world, and an AI
 * scenario test has none.
 */
fun interface SettlementGathering {

  /** The gathering spot for whoever is standing at [at], or null out in the country. */
  fun spotNear(at: Vec3L): Vec3L?
}

/**
 * The square, meaning the middle of the settlement.
 *
 * Not the inn's doorstep, though that was the obvious answer: a counter is already where people go to buy
 * a meal, and a second errand ending at the same coordinate gives the planner two actions with identical
 * effects to choose between. It picks either, and which one shows up in a plan then depends on map
 * ordering. The middle of town is both a better fiction and an unambiguous one.
 */
@Service
class TownSquares(private val sites: SettlementSiteIndex) : SettlementGathering {

  override fun spotNear(at: Vec3L): Vec3L? {
    val site = sites.siteCovering(at.x, at.y) ?: return null

    return sites.standingAt(site.centre)
  }
}

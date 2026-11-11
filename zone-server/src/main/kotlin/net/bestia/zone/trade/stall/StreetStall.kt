package net.bestia.zone.trade.stall

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** A Trader's stall standing in the world: whose it is, how many offers it holds, and when it packs up. */
data class StreetStall(
  val sellerMasterId: Long,
  val sellerAccountId: Long,
  val sellerEntityId: EntityId,
  val sellerName: String,
  val maxListings: Int,
  val at: Vec3L,
  var remainingSeconds: Float,
) : Component

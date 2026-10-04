package net.bestia.zone.capture

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * A set trap waiting on its tile for a wild bestia. Drawn through its `EntityVisual`; nothing here reaches
 * the client.
 */
class BestiaTrap(
  val ownerAccountId: AccountId,

  /** Who a catch goes to, kept here because the owner may have logged out by the time something walks in. */
  val masterId: Long,

  /** The master who set it, whose Willpower and skills the capture roll uses. */
  val trapperEntityId: EntityId,
  val tier: TrapTier,
  var remainingSeconds: Float = LIFETIME_SECONDS,
) : Component {

  companion object {
    const val LIFETIME_SECONDS = 10f * 60f
  }
}

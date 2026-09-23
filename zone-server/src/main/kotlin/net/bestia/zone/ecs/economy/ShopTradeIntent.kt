package net.bestia.zone.ecs.economy

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/**
 * Intent to buy or sell [amount] of [itemId] with the settlement this entity is standing in.
 *
 * Attached by `ShopTradeHandler` and resolved by `ShopTradeIntentSystem`, where every check lives -
 * `CollectPropIntent`'s arrangement, and for the same reason: the settlement ledger is a plain map
 * touched only from the tick thread, and a message handler runs on a Netty thread.
 *
 * The race it resolves is sharper here than for a prop. Two players buying the last loaf are visited by
 * a single pass of one system: the first decrements the in-memory ledger synchronously, so the second is
 * quoted against a town with one fewer loaf in it and refused if that leaves none. Entity operations
 * defer to the end of the tick and would have granted twice.
 *
 * Only one can be attached at a time, since components are one per class per entity - a player who
 * clicks buy twice inside the same 50 ms tick gets the second. Matches `CollectPropIntent`.
 */
data class ShopTradeIntent(
  val itemId: Long,
  val amount: Int,
  val selling: Boolean,

  /**
   * The commodities the merchant deals in, resolved off the tick where the household expansion is safe
   * to do. Carried rather than looked up here because `SpeakerResolver` takes the world lock, and this
   * runs inside it.
   */
  val stocked: Set<String>,

  /** Whose counter, so the window that follows the trade says whose it is. */
  val merchantEntityId: EntityId,
) : Component

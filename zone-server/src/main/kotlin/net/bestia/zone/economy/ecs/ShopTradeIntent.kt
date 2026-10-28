package net.bestia.zone.economy.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.economy.shop.Counter
import net.bestia.zone.economy.shop.ShopTradeLine

/**
 * Intent to trade a basket of [lines] with the settlement this entity is standing in.
 *
 * Attached by `ShopTradeHandler` and resolved by `ShopTradeIntentSystem`, where every check lives -
 * `CollectPropIntent`'s arrangement: the settlement ledger is a plain map touched only from the tick
 * thread, and resolving every trade in one system pass is what settles two buyers of the last loaf.
 *
 * The race it resolves is sharper here than for a prop. Two players buying the last loaf are visited by
 * a single pass of one system: the first decrements the in-memory ledger synchronously, so the second is
 * quoted against a town with one fewer loaf in it and refused if that leaves none. Entity operations
 * defer to the end of the tick and would have granted twice.
 *
 * Only one can be attached at a time, since components are one per class per entity - a player who
 * confirms twice inside the same 50 ms tick gets the second. Matches `CollectPropIntent`.
 */
data class ShopTradeIntent(
  val lines: List<ShopTradeLine>,

  /** The balance the player was quoted; the trade is refused if it would come out worse. */
  val expectedBalance: Long,

  /**
   * Whose counter, resolved off the tick where the household expansion is safe to do. Carried rather than
   * looked up here because `SpeakerResolver` opens a world scope of its own.
   */
  val counter: Counter,
) : Component

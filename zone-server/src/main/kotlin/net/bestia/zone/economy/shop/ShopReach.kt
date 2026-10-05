package net.bestia.zone.economy.shop

import net.bestia.zone.geometry.Vec3L

/**
 * How close a player has to stand to a merchant to deal with them. Checked on opening the window and again on
 * every trade, because a hand-made packet can trade without ever opening it.
 */
object ShopReach {

  /**
   * `TalkService.MAX_TALK_RANGE`, and not shared with it for that constant's own reason: the two are
   * allowed to diverge, and a counter is something you stand at rather than shout across.
   */
  const val MAX_SHOP_RANGE = 10L

  fun withinReach(player: Vec3L, merchant: Vec3L): Boolean {
    return player.distance(merchant) <= MAX_SHOP_RANGE
  }
}

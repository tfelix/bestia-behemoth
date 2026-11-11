package net.bestia.zone.master.bestia

import net.bestia.zone.BestiaEvent
import net.bestia.zone.util.PlayerBestiaId

/** A bestia was given new orders, from the game, from the companion app, or by a change to its preset. */
class BestiaAiConfigChangedEvent(
  source: Any,
  val accountId: Long,
  val masterId: Long,
  val playerBestiaId: PlayerBestiaId,
  val stored: StoredOrders,
) : BestiaEvent(source)

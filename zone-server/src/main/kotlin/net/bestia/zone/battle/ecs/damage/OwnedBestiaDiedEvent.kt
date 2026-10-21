package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.BestiaEvent
import net.bestia.zone.util.PlayerBestiaId

/** A player's bestia died. Published on the tick, once per death: a listener must not block. */
class OwnedBestiaDiedEvent(
  source: Any,
  val accountId: Long,
  val masterId: Long,
  val playerBestiaId: PlayerBestiaId,
) : BestiaEvent(source)

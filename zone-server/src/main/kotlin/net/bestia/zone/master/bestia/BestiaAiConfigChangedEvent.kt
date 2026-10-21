package net.bestia.zone.master.bestia

import net.bestia.zone.BestiaEvent
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.util.PlayerBestiaId

/** A bestia was given a new standing order, from the game or from the companion app. */
class BestiaAiConfigChangedEvent(
  source: Any,
  val accountId: Long,
  val masterId: Long,
  val playerBestiaId: PlayerBestiaId,
  val config: AiConfig,
) : BestiaEvent(source)

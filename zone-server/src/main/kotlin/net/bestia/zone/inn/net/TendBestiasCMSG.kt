package net.bestia.zone.inn.net

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class TendBestiasCMSG(
  override val playerId: Long,
  val counterEntityId: EntityId,
  val expectedPrice: Int,
) : CMSG

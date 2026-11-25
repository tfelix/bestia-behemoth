package net.bestia.zone.inn.net

import net.bestia.zone.message.CMSG
import net.bestia.zone.util.EntityId

data class SetInnSavePointCMSG(
  override val playerId: Long,
  val counterEntityId: EntityId,
) : CMSG
